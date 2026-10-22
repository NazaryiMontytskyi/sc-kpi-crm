import { expect, test as base, type BrowserContext, type Locator, type Page } from '@playwright/test';
import { ApiSession } from './api';
import type { Actor } from './data';
import { ADMIN } from './env';

async function prepareContext(context: BrowserContext): Promise<void> {
  // Scenarios assert on English labels; the profile language of every fake member is "en" as well.
  await context.addInitScript(() => window.localStorage.setItem('crm.language', 'en'));
}

/** Opens the login page; the form itself keeps submit disabled until the session/CSRF bootstrap is done. */
export async function openLogin(page: Page): Promise<void> {
  await page.goto('/login');
  await expect(page.getByLabel(/^Login/)).toBeVisible();
}

/** Signs in through the login form and waits until the app shell is shown. */
export async function signIn(page: Page, who: Pick<Actor, 'login' | 'password'>): Promise<void> {
  await openLogin(page);
  await page.getByLabel(/^Login/).fill(who.login);
  await page.getByLabel(/^Password/).fill(who.password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { name: 'Welcome' })).toBeVisible();
}

interface WorkerFixtures {
  /** API session of the bootstrap Admin, used for setup only (users, roles, spaces). */
  admin: ApiSession;
}

interface TestFixtures {
  /** Opens a signed-in page of another member in its own browser context (same viewport as the test). */
  openAs: (who: Actor) => Promise<Page>;
}

export const test = base.extend<TestFixtures, WorkerFixtures>({
  admin: [
    // eslint-disable-next-line no-empty-pattern
    async ({}, use) => {
      const session = new ApiSession();
      await session.login(ADMIN.login, ADMIN.password);
      await use(session);
    },
    { scope: 'worker' },
  ],
  context: async ({ context }, use) => {
    await prepareContext(context);
    await use(context);
  },
  openAs: async ({ browser, baseURL, viewport, isMobile, hasTouch, locale, timezoneId }, use) => {
    const contexts: BrowserContext[] = [];
    await use(async (who) => {
      const context = await browser.newContext({ baseURL, viewport, isMobile, hasTouch, locale, timezoneId });
      contexts.push(context);
      await prepareContext(context);
      const page = await context.newPage();
      await signIn(page, who);
      return page;
    });
    await Promise.all(contexts.map((c) => c.close()));
  },
});

export { expect };

/** Picks an option of a Mantine Select / MultiSelect by typing part of its label. */
export async function pickOption(
  page: Page,
  label: string | RegExp,
  option: string,
  search = option,
  scope: Page | Locator = page,
): Promise<void> {
  const field = scope.getByLabel(label);
  await field.click();
  await field.fill(search);
  await page.getByRole('option', { name: option }).first().click();
}
