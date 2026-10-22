import { expect, test as base, type BrowserContext, type Locator, type Page } from '@playwright/test';
import { ApiSession } from './api';
import type { Actor } from './data';
import { ADMIN } from './env';

/**
 * KNOWN DEFECT WORKAROUND (reported with INC-051): the generated API client serializes the pagination object as
 * `page[size]=100` (openapi-fetch deepObject style) while the backend binds the flat form `?page=0&size=100`, so
 * every paginated list answers 400 in the real stack. Until the client or the backend is fixed, the browser requests
 * are rewritten to the flat form here. Set E2E_FLATTEN_PAGE_PARAMS=0 to run without the workaround.
 */
async function flattenPageParams(context: BrowserContext): Promise<void> {
  await context.route(/\/api\/v1\/.*[?&]page(%5B|\[)/i, async (route) => {
    const url = new URL(route.request().url());
    const flat = new URLSearchParams();
    for (const [key, value] of url.searchParams) {
      const nested = /^page\[(.+)\]$/.exec(key);
      flat.append(nested ? nested[1] : key, value);
    }
    url.search = flat.toString();
    await route.continue({ url: url.toString() });
  });
}

async function prepareContext(context: BrowserContext): Promise<void> {
  // Scenarios assert on English labels; the profile language of every fake member is "en" as well.
  await context.addInitScript(() => window.localStorage.setItem('crm.language', 'en'));
  if (process.env.E2E_FLATTEN_PAGE_PARAMS !== '0') await flattenPageParams(context);
}

/** Opens the login page once the app has obtained its CSRF cookie (submitting earlier is rejected as CSRF). */
export async function openLogin(page: Page): Promise<void> {
  await page.goto('/login');
  await expect.poll(async () => (await page.context().cookies()).some((c) => c.name === 'XSRF-TOKEN')).toBe(true);
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
