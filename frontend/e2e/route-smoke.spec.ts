import { expect, test, type Page } from '@playwright/test';

function passwordFor(...names: string[]): string {
  const password = names.map((name) => process.env[name]).find(Boolean);
  if (!password) {
    throw new Error(`Missing local E2E password: ${names.join(' or ')}`);
  }
  return password;
}

async function signIn(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/membership');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.locator('#kc-login').click();
  await expect(page).toHaveURL(/\/membership$/);
}

async function expectRoutes(
  page: Page,
  routes: Array<{ path: string; heading: string }>
): Promise<void> {
  for (const route of routes) {
    await page.goto(route.path);
    await expect(page.getByRole('heading', { level: 1 })).toContainText(route.heading);
  }
}

async function checkUserRoutes(
  browser: import('@playwright/test').Browser,
  username: string,
  password: string,
  routes: Array<{ path: string; heading: string }>,
  forbiddenRoutes: string[]
): Promise<void> {
  const pageErrors: string[] = [];
  const context = await browser.newContext();
  const page = await context.newPage();
  page.on('pageerror', (error) => pageErrors.push(error.message));
  page.on('response', (response) => {
    if (response.url().includes('/api/') && response.status() >= 500) {
      pageErrors.push(`${response.status()} ${response.url()}`);
    }
  });

  await signIn(page, username, password);
  await expectRoutes(page, routes);
  for (const path of forbiddenRoutes) {
    await page.goto(path);
    await expect(page).toHaveURL(/\/forbidden$/);
  }

  expect(pageErrors).toEqual([]);
  await context.close();
}

test('public routes load and unknown paths show not found', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Smart');
  await page.goto('/not-a-smartgym-route');
  await expect(page.getByRole('heading', { name: 'Page not found' })).toBeVisible();
});

test('admin, coach, and member routes render only for allowed roles', async ({ browser }) => {
  await checkUserRoutes(
    browser,
    process.env.E2E_ADMIN_USERNAME ?? 'gym-admin',
    passwordFor('E2E_ADMIN_PASSWORD', 'DEV_ADMIN_PASSWORD'),
    [
      { path: '/dashboard', heading: 'Welcome' },
      { path: '/membership', heading: 'Membership workspace' },
      { path: '/courses', heading: 'Courses' },
      { path: '/training', heading: 'Exercise catalogue' },
      { path: '/nutrition', heading: 'nutrition' },
      { path: '/feedback', heading: 'AI Customer Intelligence & Satisfaction Analytics' }
    ],
    ['/training/programmes', '/training/workouts', '/coach/nutrition']
  );

  await checkUserRoutes(
    browser,
    process.env.E2E_COACH_USERNAME ?? 'gym-coach',
    passwordFor('E2E_COACH_PASSWORD', 'DEV_COACH_PASSWORD'),
    [
      { path: '/dashboard', heading: 'Welcome' },
      { path: '/membership', heading: 'Membership workspace' },
      { path: '/courses', heading: 'Courses' },
      { path: '/training', heading: 'Exercise catalogue' },
      { path: '/training/programmes', heading: 'Training programmes' },
      { path: '/coach/nutrition', heading: 'Support your assigned members' },
      { path: '/feedback', heading: 'Your Feedback & Experience' }
    ],
    ['/nutrition', '/training/workouts']
  );

  await checkUserRoutes(
    browser,
    process.env.E2E_MEMBER_USERNAME ?? 'member-a',
    passwordFor('E2E_MEMBER_PASSWORD', 'DEV_MEMBER_A_PASSWORD'),
    [
      { path: '/dashboard', heading: 'Welcome' },
      { path: '/membership', heading: 'Membership workspace' },
      { path: '/courses', heading: 'Courses' },
      { path: '/training', heading: 'Exercise catalogue' },
      { path: '/training/programmes', heading: 'Training programmes' },
      { path: '/training/workouts', heading: 'Workout history' },
      { path: '/nutrition', heading: 'A calmer way to care for your nutrition' },
      { path: '/feedback', heading: 'Your Feedback & Experience' }
    ],
    ['/coach/nutrition']
  );
});
