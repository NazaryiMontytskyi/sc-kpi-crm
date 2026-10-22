import { capitalize, createActor, createRole, token } from '../support/data';
import { expect, signIn, test } from '../support/fixtures';
import { doc, sessionOf } from '../support/scenarios';

test('read an article, then create a new version of it', async ({ page, admin, openAs }) => {
  const editorRole = await createRole(admin, 'kbeditor', ['kb.view', 'kb.edit']);
  const readerRole = await createRole(admin, 'kbreader', ['kb.view']);
  const editor = await createActor(admin, 'kbed', { roleId: editorRole.id });
  const reader = await createActor(admin, 'kbread', { roleId: readerRole.id });

  const title = `Fake handbook ${capitalize(token())}`;
  const original = `Original paragraph ${token()}`;
  const addition = `Added paragraph ${token()}`;
  const article = await (await sessionOf(editor)).post<{ id: string; version: number }>('/articles', {
    title,
    content: doc(original),
    tags: ['e2e'],
  });
  expect(article.version).toBe(1);

  // A reader finds the article in the list, reads it and leaves the private "read" mark.
  const readerPage = await openAs(reader);
  await readerPage.goto('/knowledge');
  await readerPage.getByTestId('article-list').getByRole('link', { name: title }).click();
  await expect(readerPage.getByRole('heading', { name: title })).toBeVisible();
  await expect(readerPage.getByText(original)).toBeVisible();
  await expect(readerPage.getByRole('link', { name: 'Edit' })).toHaveCount(0);
  await readerPage.getByLabel('I have read this').click();
  await expect(readerPage.getByLabel('I have read this')).toBeChecked();
  await readerPage.reload();
  await expect(readerPage.getByLabel('I have read this')).toBeChecked();

  // An editor changes the page: saving creates version 2 and keeps version 1 in the history.
  await signIn(page, editor);
  await page.goto(`/knowledge/${article.id}`);
  await expect(page.getByText(original)).toBeVisible();
  await page.getByRole('link', { name: 'Edit' }).click();
  const content = page.getByRole('textbox', { name: 'Page content' });
  await content.click();
  await page.keyboard.press('Control+End');
  await page.keyboard.press('Enter');
  await page.keyboard.type(addition);
  await page.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`/knowledge/${article.id}$`));
  await expect(page.getByText(addition)).toBeVisible();

  await page.getByRole('button', { name: 'History' }).click();
  const history = page.getByRole('dialog', { name: 'Version history' });
  await expect(history.getByText('Version 2')).toBeVisible();
  await expect(history.getByText('Version 1')).toBeVisible();
  await history.getByText('Version 1').click();
  await expect(history.getByText(original)).toBeVisible();
  await expect(history.getByText(addition)).toHaveCount(0);

  // The reader sees the new version after a reload.
  await readerPage.reload();
  await expect(readerPage.getByText(addition)).toBeVisible();
});
