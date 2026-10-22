import { MEMBER_PASSWORD } from '../support/env';
import { createActor } from '../support/data';
import { expect, openLogin, signIn, test } from '../support/fixtures';

test('a new member signs in with the temporary password and must set their own', async ({ page, admin }) => {
  const member = await createActor(admin, 'newbie', { activate: false });

  await openLogin(page);
  await page.getByLabel(/^Login/).fill(member.login);
  await page.getByLabel(/^Password/).fill(member.temporaryPassword);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(/\/change-password$/);

  // The change is forced: no other screen is reachable until it is done.
  await page.goto('/members');
  await expect(page).toHaveURL(/\/change-password$/);

  await page.getByLabel(/^Current password/).fill(member.temporaryPassword);
  await page.getByLabel(/^New password/).fill(MEMBER_PASSWORD);
  await page.getByLabel(/^Repeat new password/).fill(MEMBER_PASSWORD);
  await page.getByRole('button', { name: 'Change password' }).click();
  await expect(page.getByRole('heading', { name: 'Welcome' })).toBeVisible();

  // Sign out and back in with the new password.
  await page.getByRole('button', { name: 'User menu' }).click();
  await page.getByRole('menuitem', { name: 'Sign out' }).click();
  await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible();
  await signIn(page, { login: member.login, password: MEMBER_PASSWORD });
});

test('wrong credentials are rejected without revealing which part was wrong', async ({ page, admin }) => {
  const member = await createActor(admin, 'wrongpw');
  await openLogin(page);
  await page.getByLabel(/^Login/).fill(member.login);
  await page.getByLabel(/^Password/).fill('Not-The-Password-1');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByTestId('login-error')).toBeVisible();
  await expect(page).toHaveURL(/\/login$/);
});

test('submitting right after navigation while /auth/me is slow still signs in', async ({ page, admin }) => {
  const member = await createActor(admin, 'earlybird');
  // Hold back the initial session probe until released: the XSRF cookie only arrives with its answer.
  let release!: () => void;
  const released = new Promise<void>((resolve) => (release = resolve));
  await page.route('**/api/v1/auth/me', async (route) => {
    await released;
    await route.continue();
  });

  await page.goto('/login');
  await page.getByLabel(/^Login/).fill(member.login);
  await page.getByLabel(/^Password/).fill(member.password);
  const submit = page.getByRole('button', { name: 'Sign in' });
  await expect(submit).toBeDisabled();
  release();
  // Clicking waits for the button to become enabled, i.e. for the bootstrap to finish.
  await submit.click();
  await expect(page.getByRole('heading', { name: 'Welcome' })).toBeVisible();
});
