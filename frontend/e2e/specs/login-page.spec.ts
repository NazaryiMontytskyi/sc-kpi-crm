import type { Page, Request } from '@playwright/test';
import { createActor } from '../support/data';
import { expect, openLogin, prepareContext, test } from '../support/fixtures';

/*
 * Hardening of the animated sign-in page (INC-062, MAIN_PAGE_FEATURE.md §7, §9.7-§9.9, §9.12). Runs in the desktop
 * and the 375 px project. The intro splash (once per session) is covered in auth.spec.ts.
 */

/** How far the document overflows the viewport horizontally (0 when there is no horizontal scroll). */
async function horizontalOverflow(page: Page): Promise<number> {
  return page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
}

/** The card is inside the viewport horizontally and, once scrolled to, fits it vertically too. */
async function expectCardFullyVisible(page: Page): Promise<void> {
  const card = page.getByTestId('login-card').locator('section').first();
  await card.scrollIntoViewIfNeeded();
  const box = await card.boundingBox();
  const viewport = page.viewportSize();
  expect(box).not.toBeNull();
  expect(viewport).not.toBeNull();
  expect(box!.x).toBeGreaterThanOrEqual(0);
  expect(box!.x + box!.width).toBeLessThanOrEqual(viewport!.width + 0.5);
  expect(box!.height).toBeLessThanOrEqual(viewport!.height);
}

async function fillCredentials(page: Page, login: string, password: string): Promise<void> {
  await page.getByLabel(/^Login/).fill(login);
  await page.getByLabel(/^Password/).fill(password);
}

/** Parallax offset written on the hero by the pointer loop ('' when the parallax is off). */
async function heroParallax(page: Page): Promise<string> {
  return page.getByTestId('login-hero').evaluate((hero) => hero.style.getPropertyValue('--login-parallax-x'));
}

/** spec §9.12: on the sign-in page only static assets of the app, the session probe and the login call. */
function allowedOnLoginPage(url: string, origin: string): boolean {
  const target = new URL(url);
  if (target.protocol === 'data:' || target.protocol === 'blob:') return true;
  if (target.origin !== origin) return false;
  const path = target.pathname;
  return (
    path === '/login' ||
    path === '/favicon.ico' ||
    path.startsWith('/assets/') ||
    path === '/api/v1/auth/me' ||
    path === '/api/v1/auth/login'
  );
}

test.describe('sign-in page layout', () => {
  test('has no horizontal scroll and shows the whole card at the project width and at 360 px', async ({ page }) => {
    await openLogin(page);
    expect(await horizontalOverflow(page)).toBeLessThanOrEqual(0);
    await expectCardFullyVisible(page);

    await page.setViewportSize({ width: 360, height: 640 });
    await expect(page.getByTestId('login-hero')).toHaveAttribute('data-layout', 'compact');
    expect(await horizontalOverflow(page)).toBeLessThanOrEqual(0);
    await expectCardFullyVisible(page);
  });

  test('stays usable at 200 % zoom (half the CSS viewport) and signs in', async ({ page, admin, isMobile }) => {
    // Browser zoom only reflows on desktop; phones pinch-zoom without reflow.
    test.skip(isMobile, 'page zoom reflow is a desktop concern');
    const member = await createActor(admin, 'zoomed');
    const size = page.viewportSize()!;
    await page.setViewportSize({ width: Math.round(size.width / 2), height: Math.round(size.height / 2) });
    await openLogin(page);
    expect(await horizontalOverflow(page)).toBeLessThanOrEqual(0);
    await fillCredentials(page, member.login, member.password);
    const submit = page.getByRole('button', { name: 'Sign in' });
    await submit.scrollIntoViewIfNeeded();
    await expect(submit).toBeInViewport();
    await submit.click();
    await expect(page.getByRole('heading', { name: 'Welcome' })).toBeVisible();
  });

  test('stays usable on a phone in landscape and signs in', async ({ browser, baseURL, locale, admin }) => {
    const member = await createActor(admin, 'landscape');
    const context = await browser.newContext({
      baseURL,
      locale,
      viewport: { width: 667, height: 375 },
      isMobile: true,
      hasTouch: true,
    });
    await prepareContext(context);
    const page = await context.newPage();
    try {
      await openLogin(page);
      expect(await horizontalOverflow(page)).toBeLessThanOrEqual(0);
      await fillCredentials(page, member.login, member.password);
      const submit = page.getByRole('button', { name: 'Sign in' });
      await submit.scrollIntoViewIfNeeded();
      await expect(submit).toBeInViewport();
      await submit.tap();
      await expect(page.getByRole('heading', { name: 'Welcome' })).toBeVisible();
    } finally {
      await context.close();
    }
  });
});

test.describe('sign-in page network (spec §9.12)', () => {
  test('requests only static assets, the session probe and the login call', async ({ page, admin, baseURL }) => {
    const origin = new URL(baseURL!).origin;
    // Self-check of the allow-list: anything else fails the scenario.
    expect(allowedOnLoginPage(`${origin}/api/v1/members`, origin)).toBe(false);
    expect(allowedOnLoginPage(`${origin}/api/v1/auth/logout`, origin)).toBe(false);
    expect(allowedOnLoginPage('https://fonts.example.org/font.woff2', origin)).toBe(false);
    expect(allowedOnLoginPage(`${origin}/assets/image_1-abc.webp`, origin)).toBe(true);

    const member = await createActor(admin, 'network');
    const seen: string[] = [];
    const record = (request: Request) => seen.push(request.url());
    page.on('request', record);
    await page.goto('/login');
    await expect(page.getByLabel(/^Login/)).toBeVisible();
    // Let the background chunk, the first photos and the preload of the next one arrive.
    await page.waitForLoadState('networkidle');
    await fillCredentials(page, member.login, member.password);
    const login = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/v1/auth/login');
    await page.getByRole('button', { name: 'Sign in' }).click();
    expect((await login).ok()).toBe(true);
    // From here on the app itself loads its data; that is no longer the sign-in page.
    page.off('request', record);

    expect(seen.filter((url) => !allowedOnLoginPage(url, origin))).toEqual([]);
    expect(seen.some((url) => new URL(url).pathname === '/api/v1/auth/login')).toBe(true);
    await expect(page.getByRole('heading', { name: 'Welcome' })).toBeVisible();
  });
});

test.describe('sign-in page motion', () => {
  test('with reduced motion: no intro, Ken Burns, particles, parallax or staggered reveals (spec §9.8)', async ({
    browser,
    baseURL,
    viewport,
    locale,
  }) => {
    const context = await browser.newContext({ baseURL, viewport, locale, reducedMotion: 'reduce' });
    await prepareContext(context, { intro: true });
    const page = await context.newPage();
    try {
      await openLogin(page);
      await expect(page.getByTestId('login-background')).toBeAttached();
      expect(await page.getByTestId('login-intro').count()).toBe(0);
      expect(await page.getByTestId('login-particles').count()).toBe(0);
      expect(await page.locator('[data-ken-burns]').count()).toBe(0);
      await expect(page.getByTestId('login-card')).toHaveAttribute('data-motion', 'off');
      await expect(page.locator('h1[data-reveal]')).toHaveAttribute('data-reveal', 'off');
      // The tagline is part of the desktop hero only.
      const tagline = page.getByTestId('login-tagline');
      if ((await tagline.count()) > 0) await expect(tagline).not.toHaveAttribute('data-mode', 'motion');
      await page.mouse.move(10, 10);
      await page.mouse.move(400, 300);
      expect(await heroParallax(page)).toBe('');
    } finally {
      await context.close();
    }
  });

  test('the pause button stops background motion and the choice survives a reload (spec §9.9)', async ({ page }) => {
    await openLogin(page);
    const toggle = page.getByTestId('login-motion-toggle');
    await expect(toggle).toHaveAttribute('aria-pressed', 'false');
    await expect(page.getByTestId('login-background')).toBeAttached();

    await toggle.click();
    await expect(toggle).toHaveAttribute('aria-pressed', 'true');
    await expect(page.getByTestId('login-particles')).toHaveCount(0);
    await expect(page.locator('[data-ken-burns]')).toHaveCount(0);

    await page.reload();
    await expect(page.getByLabel(/^Login/)).toBeVisible();
    await expect(page.getByTestId('login-motion-toggle')).toHaveAttribute('aria-pressed', 'true');
    await expect(page.getByTestId('login-background')).toBeAttached();
    await expect(page.getByTestId('login-particles')).toHaveCount(0);
    await expect(page.locator('[data-ken-burns]')).toHaveCount(0);
    await expect(page.getByTestId('login-card')).toHaveAttribute('data-motion', 'off');

    await page.getByTestId('login-motion-toggle').click();
    await expect(page.getByTestId('login-motion-toggle')).toHaveAttribute('aria-pressed', 'false');
    await expect(page.getByTestId('login-card')).toHaveAttribute('data-motion', 'on');
  });
});

test.describe('sign-in page keyboard (MAIN_PAGE_FEATURE.md §7)', () => {
  test('tab order: language switch -> fields -> password toggle -> submit -> (photo indicator) -> motion toggle', async ({
    page,
  }) => {
    await openLogin(page);
    await fillCredentials(page, 'fake.keyboard', 'Not-A-Real-Password-1');
    await expect(page.getByRole('button', { name: 'Sign in' })).toBeEnabled();

    const language = page.getByRole('radiogroup', { name: 'Language' }).getByRole('radio', { checked: true });
    await language.focus();
    await expect(language).toBeFocused();
    // Nothing focusable comes before the language switch.
    await page.keyboard.press('Shift+Tab');
    const focusInShell = () =>
      page.evaluate(() => !!document.querySelector('[data-testid="login-shell"]')?.contains(document.activeElement));
    expect(await focusInShell()).toBe(false);

    await language.focus();
    const describeFocus = () =>
      page.evaluate(() => {
        const el = document.activeElement as HTMLElement | null;
        if (!el) return 'none';
        if (el.dataset.testid === 'login-motion-toggle') return 'motion-toggle';
        if (el.closest('[data-testid="login-indicator"]')) return 'indicator';
        if (el.getAttribute('autocomplete') === 'username') return 'login';
        if (el.getAttribute('autocomplete') === 'current-password') return 'password';
        if (el.getAttribute('type') === 'submit') return 'submit';
        if (el.tagName === 'BUTTON' && el.closest('[data-testid="login-card"]')) return 'password-toggle';
        return `${el.tagName.toLowerCase()}:${el.getAttribute('aria-label') ?? el.textContent?.trim() ?? ''}`;
      });

    const visited: string[] = [];
    for (let i = 0; i < 20 && visited.at(-1) !== 'motion-toggle'; i++) {
      await page.keyboard.press('Tab');
      visited.push(await describeFocus());
    }
    const withoutIndicator = visited.filter((stop) => stop !== 'indicator');
    expect(withoutIndicator).toEqual(['login', 'password', 'password-toggle', 'submit', 'motion-toggle']);
    // The photo indicator segments (CAP-4), if any, sit between submit and the motion toggle.
    const firstIndicator = visited.indexOf('indicator');
    if (firstIndicator >= 0) expect(firstIndicator).toBeGreaterThan(visited.indexOf('submit'));
    // Keyboard focus is visible on every stop (focus ring through :focus-visible).
    await expect(page.getByTestId('login-motion-toggle')).toBeFocused();
    expect(
      await page.getByTestId('login-motion-toggle').evaluate((el) => getComputedStyle(el).outlineStyle !== 'none'),
    ).toBe(true);
  });
});
