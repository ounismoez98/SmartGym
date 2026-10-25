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
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
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
  await expect(adminPage.getByRole('heading', { name: 'Members', exact: true })).toBeVisible();

  await adminPage.getByLabel('Keycloak user UUID', { exact: true }).fill(randomUUID());
  await adminPage.getByLabel('First name', { exact: true }).fill(firstName);
  await adminPage.getByLabel('Last name', { exact: true }).fill(lastName);
  await adminPage.getByLabel('Email', { exact: true }).fill(email);
  const memberResponsePromise = adminPage.waitForResponse((response) =>
    response.url().endsWith('/api/membership/members') && response.request().method() === 'POST');
  await adminPage.getByRole('button', { name: 'Create member', exact: true }).click();
  const memberResponse = await memberResponsePromise;
  expect(memberResponse.ok()).toBeTruthy();
  const member = await memberResponse.json() as { id: string };

  await adminPage.getByRole('button', { name: 'Members', exact: true }).click();
  await adminPage.getByLabel('Search', { exact: true }).fill(firstName);
  await adminPage.getByRole('button', { name: 'Search', exact: true }).click();
  await expect(adminPage.getByText(`${firstName} ${lastName}`, { exact: true })).toBeVisible();
  await adminPage.getByRole('button', { name: 'Subscriptions', exact: true }).click();
  await adminPage.getByRole('combobox', { name: 'Member', exact: true }).selectOption(member.id);
  await adminPage.getByRole('combobox', { name: 'Active plan', exact: true }).selectOption({ index: 1 });
  const subscriptionResponsePromise = adminPage.waitForResponse((response) =>
    response.url().endsWith('/api/membership/subscriptions') && response.request().method() === 'POST');
  await adminPage.getByRole('button', { name: 'Create subscription', exact: true }).click();
  const subscriptionResponse = await subscriptionResponsePromise;
  expect(subscriptionResponse.ok()).toBeTruthy();
  const subscription = await subscriptionResponse.json() as { id: string };

  await adminPage.getByRole('button', { name: 'Payments', exact: true }).click();
  await adminPage.getByRole('combobox', { name: 'Member', exact: true }).selectOption(member.id);
  await adminPage.getByLabel('Subscription ID (optional)', { exact: true }).fill(subscription.id);
  await adminPage.getByLabel('Amount (cents)', { exact: true }).fill('5000');
  const paymentResponsePromise = adminPage.waitForResponse((response) =>
    response.url().endsWith('/api/membership/payments') && response.request().method() === 'POST');
  await adminPage.getByRole('button', { name: 'Record payment', exact: true }).click();
  const paymentResponse = await paymentResponsePromise;
  expect(paymentResponse.ok()).toBeTruthy();
  const payment = await paymentResponse.json() as { id: string };
  const paymentRow = adminPage.getByRole('row').filter({ hasText: `${firstName} ${lastName}` });
  await expect(paymentRow).toContainText('50.00 TND');
  await paymentRow.getByRole('button', { name: 'Correct', exact: true }).click();
  await adminPage.getByLabel('Corrected amount (cents)', { exact: true }).fill('4500');
  await adminPage.getByLabel('Reason', { exact: true }).fill('E2E correction');
  const correctionResponsePromise = adminPage.waitForResponse((response) =>
    response.url().endsWith(`/api/membership/payments/${payment.id}/corrections`)
      && response.request().method() === 'POST');
  await adminPage.getByRole('button', { name: 'Save correction', exact: true }).click();
  expect((await correctionResponsePromise).ok()).toBeTruthy();
  const correctionHistory = adminPage.getByRole('region', { name: 'Payment history', exact: true })
    .getByRole('listitem')
    .filter({ hasText: 'E2E correction' });
  await expect(correctionHistory).toContainText('Correction: 45.00 TND');
  await expect(correctionHistory).toContainText('E2E correction');
  await expect(correctionHistory).toContainText('Author:');
  await expect(correctionHistory).toContainText('Date:');

  await adminPage.getByRole('button', { name: 'Assistant', exact: true }).click();
  await adminPage.getByRole('combobox', { name: 'Subscription', exact: true }).selectOption(subscription.id);
  const reminderResponsePromise = adminPage.waitForResponse((response) =>
    response.url().endsWith('/api/membership/assistant/reminders')
      && response.request().method() === 'POST');
  await adminPage.getByRole('button', { name: 'Generate draft', exact: true }).click();
  const reminderResponse = await reminderResponsePromise;
  expect(reminderResponse.ok()).toBeTruthy();
  await expect(adminPage.getByLabel('Message', { exact: true })).toHaveValue(/202/);
  adminPage.once('dialog', (dialog) => dialog.accept());
  const confirmResponsePromise = adminPage.waitForResponse((response) =>
    response.url().includes('/api/membership/assistant/reminders/')
      && response.url().endsWith('/confirm'));
  await adminPage.getByRole('button', { name: 'Confirm & send', exact: true }).click();
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
  await expect(memberPage.getByRole('button', { name: 'My profile', exact: true })).toBeVisible();
  await expect(memberPage.getByText(email, { exact: true })).toHaveCount(0);
  await expect.poll(() => memberToken).not.toBe('');
  const forbiddenResponse = await memberContext.request.get(
    `${process.env.E2E_BASE_URL ?? 'http://localhost:8088'}/api/membership/members/${member.id}`,
    { headers: { Authorization: memberToken } }
  );
  expect(forbiddenResponse.status()).toBe(403);

  await memberContext.close();
  await adminContext.close();
});
