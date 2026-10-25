import { randomUUID } from 'node:crypto';
import { expect, test } from '@playwright/test';

function e2eCredentials() {
  const adminUsername = process.env.E2E_ADMIN_USERNAME ?? 'gym-admin';
  const adminPassword = process.env.E2E_ADMIN_PASSWORD ?? process.env.DEV_ADMIN_PASSWORD;
  const memberUsername = process.env.E2E_MEMBER_USERNAME ?? 'member-a';
  const memberPassword = process.env.E2E_MEMBER_PASSWORD ?? process.env.DEV_MEMBER_A_PASSWORD;
  if (!adminPassword || !memberPassword) {
    const missing = [
      !adminPassword && 'E2E_ADMIN_PASSWORD (or DEV_ADMIN_PASSWORD)',
      !memberPassword && 'E2E_MEMBER_PASSWORD (or DEV_MEMBER_A_PASSWORD)'
    ].filter((name): name is string => typeof name === 'string');
    throw new Error(
      `Missing E2E login credentials: ${missing.join(', ')}. `
      + 'Set them in the repository-root .env file or the current environment. '
      + 'The Playwright config reads only these credential variables from .env.'
    );
  }

  return { adminUsername, adminPassword, memberUsername, memberPassword };
}

async function signIn(page: import('@playwright/test').Page, username: string, password: string) {
  await page.goto('/membership');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.locator('#kc-login').click();
  await expect(page).toHaveURL(/\/membership$/);
}

test('admin manages membership and member access remains owner-scoped', async ({ browser, request }) => {
  const { adminUsername, adminPassword, memberUsername, memberPassword } = e2eCredentials();
  const firstName = `E2E${Date.now()}`;
  const lastName = 'Member';
  const email = `e2e-${randomUUID()}@smartgym.local`;

  const adminContext = await browser.newContext();
  const adminPage = await adminContext.newPage();
  await signIn(adminPage, adminUsername, adminPassword);
  await expect(adminPage.getByRole('heading', { name: 'Members' })).toBeVisible();

  await adminPage.getByLabel('Keycloak user UUID').fill(randomUUID());
  await adminPage.getByLabel('First name').fill(firstName);
  await adminPage.getByLabel('Last name').fill(lastName);
  await adminPage.getByLabel('Email').fill(email);
  const memberResponsePromise = adminPage.waitForResponse((response) =>
    response.url().endsWith('/api/membership/members') && response.request().method() === 'POST');
  await adminPage.getByRole('button', { name: 'Create member' }).click();
  const memberResponse = await memberResponsePromise;
  expect(memberResponse.ok()).toBeTruthy();
  const member = await memberResponse.json() as { id: string };

  await adminPage.getByRole('button', { name: 'Members' }).click();
  await adminPage.getByLabel('Search').fill(firstName);
  await adminPage.getByRole('button', { name: 'Search' }).click();
  await expect(adminPage.getByText(`${firstName} ${lastName}`)).toBeVisible();
  await adminPage.getByRole('button', { name: 'Subscriptions' }).click();
  await adminPage.getByLabel('Member').selectOption(member.id);
  await adminPage.getByLabel('Active plan').selectOption({ index: 1 });
  const subscriptionResponsePromise = adminPage.waitForResponse((response) =>
    response.url().endsWith('/api/membership/subscriptions') && response.request().method() === 'POST');
  await adminPage.getByRole('button', { name: 'Create subscription' }).click();
  const subscriptionResponse = await subscriptionResponsePromise;
  expect(subscriptionResponse.ok()).toBeTruthy();
  const subscription = await subscriptionResponse.json() as { id: string };

  await adminPage.getByRole('button', { name: 'Payments' }).click();
  await adminPage.getByLabel('Member').selectOption(member.id);
  await adminPage.getByLabel('Subscription ID (optional)').fill(subscription.id);
  await adminPage.getByLabel('Amount (cents)').fill('5000');
  const paymentResponsePromise = adminPage.waitForResponse((response) =>
    response.url().endsWith('/api/membership/payments') && response.request().method() === 'POST');
  await adminPage.getByRole('button', { name: 'Record payment' }).click();
  const paymentResponse = await paymentResponsePromise;
  expect(paymentResponse.ok()).toBeTruthy();
  const payment = await paymentResponse.json() as { id: string };
  const paymentRow = adminPage.getByRole('row').filter({ hasText: `${firstName} ${lastName}` });
  await expect(paymentRow).toContainText('50.00 TND');
  await paymentRow.getByRole('button', { name: 'Correct' }).click();
  await adminPage.getByLabel('Corrected amount (cents)').fill('4500');
  await adminPage.getByLabel('Reason').fill('E2E correction');
  const correctionResponsePromise = adminPage.waitForResponse((response) =>
    response.url().endsWith(`/api/membership/payments/${payment.id}/corrections`)
      && response.request().method() === 'POST');
  await adminPage.getByRole('button', { name: 'Save correction' }).click();
  expect((await correctionResponsePromise).ok()).toBeTruthy();

  await adminPage.getByRole('button', { name: 'Assistant' }).click();
  await adminPage.getByLabel('Subscription').selectOption(subscription.id);
  const reminderResponsePromise = adminPage.waitForResponse((response) =>
    response.url().endsWith('/api/membership/assistant/reminders')
      && response.request().method() === 'POST');
  await adminPage.getByRole('button', { name: 'Generate draft' }).click();
  const reminderResponse = await reminderResponsePromise;
  expect(reminderResponse.ok()).toBeTruthy();
  await expect(adminPage.getByLabel('Message')).toHaveValue(/202/);
  adminPage.once('dialog', (dialog) => dialog.accept());
  const confirmResponsePromise = adminPage.waitForResponse((response) =>
    response.url().includes('/api/membership/assistant/reminders/')
      && response.url().endsWith('/confirm'));
  await adminPage.getByRole('button', { name: 'Confirm & send' }).click();
  expect((await confirmResponsePromise).ok()).toBeTruthy();

  await expect.poll(async () => {
    const response = await request.get('http://localhost:8025/api/v2/messages');
    if (!response.ok()) return '';
    const body = await response.text();
    return body.includes(email) ? body : '';
  }).not.toBe('');

  let memberToken = '';
  const memberContext = await browser.newContext();
  const memberPage = await memberContext.newPage();
  memberPage.on('request', (apiRequest) => {
    if (apiRequest.url().endsWith('/api/membership/me')) {
      memberToken = apiRequest.headers()['authorization'] ?? '';
    }
  });
  await signIn(memberPage, memberUsername, memberPassword);
  await expect(memberPage.getByRole('button', { name: 'My profile' })).toBeVisible();
  await expect(memberPage.getByText(email)).toHaveCount(0);
  await expect.poll(() => memberToken).not.toBe('');
  const forbiddenResponse = await memberContext.request.get(
    `${process.env.E2E_BASE_URL ?? 'http://localhost:8088'}/api/membership/members/${member.id}`,
    { headers: { Authorization: memberToken } }
  );
  expect(forbiddenResponse.status()).toBe(403);

  await memberContext.close();
  await adminContext.close();
});
