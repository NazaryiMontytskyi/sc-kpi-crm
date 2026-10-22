import { defineConfig, devices } from '@playwright/test';
import { BASE_URL } from './support/env';

/**
 * End-to-end suite (INC-051). It runs against a live compose stack (see deploy/e2e.sh and the README) and creates
 * its own uniquely named fake data, so it can be repeated. Every scenario runs twice: at desktop size and at a
 * 375 px phone viewport (the minimum width the UI must support).
 */
export default defineConfig({
  testDir: './specs',
  outputDir: './.output/results',
  globalSetup: './support/global-setup.ts',
  timeout: 120_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 2,
  retries: 0,
  forbidOnly: !!process.env.CI,
  reporter: [['list'], ['html', { outputFolder: './.output/report', open: 'never' }]],
  use: {
    baseURL: BASE_URL,
    actionTimeout: 15_000,
    locale: 'en-US',
    timezoneId: 'Europe/Kyiv',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 } } },
    {
      name: 'mobile-375',
      use: { ...devices['Desktop Chrome'], viewport: { width: 375, height: 667 }, isMobile: true, hasTouch: true },
    },
  ],
});
