import { ApiSession } from '../support/api';
import { capitalize, createActor, createRole, token } from '../support/data';
import { expect, signIn, test } from '../support/fixtures';

const HR_PERMISSIONS = [
  'members.view',
  'members.create',
  'members.edit',
  'org.view',
  'org.manage',
  'roles.manage',
  'roles.assign',
];

test('HR creates a member, a department and a role', async ({ page, admin }) => {
  const hrRole = await createRole(admin, 'hr', HR_PERMISSIONS);
  const hr = await createActor(admin, 'hr', { roleId: hrRole.id });
  await signIn(page, hr);

  // --- member -------------------------------------------------------------------------------------------------
  const t = token();
  const newLogin = `e2e.hire.${t}`;
  const newLast = `Hired${capitalize(t)}`;
  await page.goto('/members');
  await page.getByRole('button', { name: 'Add member' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel(/^Login/).fill(newLogin);
  await dialog.getByLabel(/^Last name/).fill(newLast);
  await dialog.getByLabel(/^First name/).fill('Fake');
  await dialog.getByLabel(/^Faculty/).click();
  await page.getByRole('option').first().click();
  await dialog.getByLabel(/^Study group/).fill('KP-01');
  await dialog.getByLabel(/^Joined on/).fill('2024-09-01');
  await dialog.getByLabel(/^Telegram/).fill(`e2e_${t}`);
  await dialog.getByRole('button', { name: 'Save' }).click();

  // The temporary password is shown exactly once.
  const passwordDialog = page.getByRole('dialog', { name: 'Temporary password' });
  const temporaryPassword = await passwordDialog.getByLabel('Temporary password').inputValue();
  expect(temporaryPassword.length).toBeGreaterThanOrEqual(10);
  await passwordDialog.getByRole('button', { name: 'I have saved the password' }).click();
  await expect(passwordDialog).toBeHidden();

  await page.getByRole('searchbox', { name: 'Search' }).fill(newLast);
  await expect(page.getByText(newLast).first()).toBeVisible();

  // The new account really exists and is forced to change the issued password.
  const fresh = await new ApiSession().login(newLogin, temporaryPassword);
  expect(fresh.mustChangePassword).toBe(true);

  // --- department ---------------------------------------------------------------------------------------------
  const unitName = `E2E Department ${capitalize(token())}`;
  await page.goto('/org');
  await page.getByRole('button', { name: 'New unit' }).click();
  const unitDialog = page.getByRole('dialog');
  await unitDialog.getByLabel(/^Name/).fill(unitName);
  await unitDialog.getByLabel(/^Type/).click();
  await page.getByRole('option', { name: 'Department' }).click();
  await unitDialog.getByRole('button', { name: 'Save' }).click();
  // Saving opens the new unit; it must also be part of the structure tree.
  await expect(page.getByRole('heading', { name: unitName })).toBeVisible();
  await page.goto('/org');
  await expect(page.getByTestId('org-node').filter({ hasText: unitName })).toBeVisible();

  // --- role ---------------------------------------------------------------------------------------------------
  const roleName = `E2E Role ${capitalize(token())}`;
  await page.goto('/roles');
  await page.getByRole('button', { name: 'Create role' }).click();
  const roleDialog = page.getByRole('dialog');
  await roleDialog.getByLabel(/^Name/).fill(roleName);
  for (const key of ['kb.view', 'tasks.view']) {
    const row = roleDialog.locator('.mantine-Checkbox-root').filter({ has: page.getByText(key, { exact: true }) });
    await row.locator('label').first().click();
  }
  await roleDialog.getByRole('button', { name: 'Save' }).click();
  await expect(roleDialog).toBeHidden();
  const created = (await admin.get<{ content: { name: string; permissions: string[] }[] }>('/roles?page=0&size=100')).content.find(
    (r) => r.name === roleName,
  );
  expect(created?.permissions.sort()).toEqual(['kb.view', 'tasks.view']);
  await expect(page.getByText(roleName).first()).toBeVisible();
});

