import { defineConfig, devices } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const localCredentialNames = new Set([
  'E2E_ADMIN_USERNAME',
  'E2E_ADMIN_PASSWORD',
  'E2E_COACH_USERNAME',
  'E2E_COACH_PASSWORD',
  'E2E_MEMBER_USERNAME',
  'E2E_MEMBER_PASSWORD',
  'DEV_ADMIN_PASSWORD',
  'DEV_COACH_PASSWORD',
  'DEV_MEMBER_A_PASSWORD'
]);

function loadLocalE2ECredentials(): void {
  const envFile = resolve(__dirname, '..', '.env');
  let contents: string;
  try {
    contents = readFileSync(envFile, 'utf8');
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code === 'ENOENT') return;
    throw error;
  }

  for (const line of contents.split(/\r?\n/)) {
    const match = line.match(/^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$/);
    if (!match || !localCredentialNames.has(match[1]) || process.env[match[1]]) continue;

    const value = match[2];
    process.env[match[1]] = value.length >= 2
      && ((value.startsWith('"') && value.endsWith('"'))
        || (value.startsWith("'") && value.endsWith("'")))
      ? value.slice(1, -1)
      : value.replace(/\s+#.*$/, '');
  }
}

loadLocalE2ECredentials();

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  timeout: 5 * 60 * 1000,
  expect: { timeout: 15_000 },
  reporter: 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8088',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure'
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] }
    }
  ]
});
