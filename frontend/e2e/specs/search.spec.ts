import type { ApiSession } from '../support/api';
import { capitalize, createActor, createRole, token } from '../support/data';
import { expect, signIn, test } from '../support/fixtures';
import { doc, sessionOf } from '../support/scenarios';

interface SearchResponse {
  articles?: { total: number };
}

/** Waits until the search index knows the article; rebuilds the index once as Admin if events were not enough. */
async function waitForIndexed(admin: ApiSession, user: ApiSession, query: string): Promise<void> {
  const found = async () => ((await user.get<SearchResponse>(`/search?q=${encodeURIComponent(query)}`)).articles?.total ?? 0) > 0;
  for (let i = 0; i < 10; i += 1) {
    if (await found()) return;
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  await admin.post('/search/reindex');
  expect(await found()).toBe(true);
}

test('search finds an article and a person from the header search', async ({ page, admin }) => {
  const role = await createRole(admin, 'searcher', ['kb.view', 'kb.edit', 'members.view']);
  const user = await createActor(admin, 'search', { roleId: role.id });
  const word = `Zorblax${capitalize(token())}`;
  const title = `Fake onboarding guide ${word}`;
  const session = await sessionOf(user);
  const article = await session.post<{ id: string }>('/articles', {
    title,
    content: doc(`This fake page is about ${word} and nothing else`),
  });
  await waitForIndexed(admin, session, word);

  await signIn(page, user);
  await page.getByRole('button', { name: 'Open search' }).click();
  const dialog = page.getByRole('dialog', { name: 'Search' });
  await dialog.getByRole('searchbox', { name: 'Search query' }).fill(word);
  const hit = dialog.getByRole('link', { name: new RegExp(word) }).first();
  await expect(hit).toBeVisible();
  await hit.click();
  await expect(page).toHaveURL(new RegExp(`/knowledge/${article.id}$`));
  await expect(page.getByRole('heading', { name: title })).toBeVisible();

  // The full results page groups hits by type and also finds the member by last name.
  await page.goto(`/search?q=${encodeURIComponent(word)}`);
  await expect(page.getByRole('region', { name: 'Articles' })).toContainText(title);
  await page.goto(`/search?q=${encodeURIComponent(user.lastName)}`);
  await expect(page.getByRole('region', { name: 'People' })).toContainText(user.lastName);

  // A query without hits says so instead of failing.
  await page.goto(`/search?q=${encodeURIComponent(`nomatch${token()}`)}`);
  await expect(page.getByText(/Nothing found/i)).toBeVisible();
});
