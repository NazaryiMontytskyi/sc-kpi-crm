import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { configure, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { CurrentUserProvider, STUB_AUTH_STATE, type AuthState } from '../auth/CurrentUser';
import { changeLanguage } from '../i18n';
import { renderWithProviders } from '../test/render';
import { ArticleEditPage } from './ArticleEditPage';
import {
  clearDraft,
  draftIsUsable,
  draftKey,
  formFromArticle,
  isVersionConflict,
  loadDraft,
  normalizeTags,
  saveDraft,
  toArticleInput,
  validateTitle,
} from './articleLogic';
import { ArticlePage } from './ArticlePage';
import * as api from './knowledgeApi';
import { KnowledgeHomePage } from './KnowledgeHomePage';
import { parentOptions, pathTo, subtreeIds } from './treeLogic';

vi.mock('./knowledgeApi', async (orig) => ({
  ...(await orig<typeof api>()),
  getArticle: vi.fn(),
  getArticleTree: vi.fn(),
  getTags: vi.fn(),
  listArticles: vi.fn(),
  createArticle: vi.fn(),
  updateArticle: vi.fn(),
  moveArticle: vi.fn(),
  archiveArticle: vi.fn(),
  restoreArticle: vi.fn(),
  uploadArticleFile: vi.fn(),
  listArticleVersions: vi.fn(),
  getArticleVersion: vi.fn(),
  restoreArticleVersion: vi.fn(),
}));
vi.mock('../members/memberApi', async (orig) => ({ ...(await orig<typeof import('../members/memberApi')>()), listMembers: vi.fn().mockResolvedValue([]) }));
vi.mock('../tasks/tasksApi', async (orig) => ({
  ...(await orig<typeof import('../tasks/tasksApi')>()),
  listTasks: vi.fn().mockResolvedValue({ items: [], page: 0, totalPages: 0, totalElements: 0 }),
}));

// jsdom has no layout; ProseMirror asks ranges for their rectangles when it scrolls to the cursor.
Range.prototype.getClientRects = () => Object.assign([], { item: () => null }) as unknown as DOMRectList;
Range.prototype.getBoundingClientRect = () => new DOMRect(0, 0, 0, 0);

vi.setConfig({ testTimeout: 30000 });
configure({ asyncUtilTimeout: 8000 });

const doc = (text: string) => ({ type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text }] }] });
const article = (over: Partial<api.Article> = {}): api.Article => ({
  id: 'a1',
  title: 'Onboarding guide',
  parentArticleId: null,
  restricted: false,
  version: 3,
  tags: ['guide'],
  archived: false,
  updatedAt: '2031-01-01T10:00:00Z',
  content: doc('Welcome to the council'),
  attachments: [],
  ancestors: [{ id: 'root', title: 'Handbook' }],
  ...over,
});
const node = (id: string, title: string, children: api.TreeNode[] = []): api.TreeNode => ({ id, title, restricted: false, children });
const TREE = [node('root', 'Handbook', [node('a1', 'Onboarding guide', [node('a2', 'First week')])]), node('other', 'Rules')];

const authWith = (permissions: string[]): AuthState => ({
  ...STUB_AUTH_STATE,
  user: { id: 'u1', displayName: 'Test User', isAdmin: false, permissions },
});

function renderRoute(route: string, auth: AuthState) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderWithProviders(
    <QueryClientProvider client={queryClient}>
      <CurrentUserProvider value={auth}>
        <Routes>
          <Route path="/knowledge" element={<KnowledgeHomePage />} />
          <Route path="/knowledge/new" element={<ArticleEditPage />} />
          <Route path="/knowledge/:id" element={<ArticlePage />} />
          <Route path="/knowledge/:id/edit" element={<ArticleEditPage />} />
        </Routes>
      </CurrentUserProvider>
    </QueryClientProvider>,
    route,
  );
}

const memoryStorage = () => {
  const data = new Map<string, string>();
  return {
    getItem: (k: string) => data.get(k) ?? null,
    setItem: (k: string, v: string) => void data.set(k, v),
    removeItem: (k: string) => void data.delete(k),
  };
};

beforeEach(async () => {
  vi.clearAllMocks();
  window.localStorage.clear();
  await changeLanguage('uk');
  vi.mocked(api.getArticleTree).mockResolvedValue(TREE);
  vi.mocked(api.getTags).mockResolvedValue([{ tag: 'guide', count: 2 }]);
  vi.mocked(api.listArticles).mockResolvedValue([]);
  vi.mocked(api.getArticle).mockResolvedValue(article());
});

describe('article logic', () => {
  it('detects only the version-conflict problem', () => {
    expect(isVersionConflict(new ApiProblemError(409, 'ARTICLE_VERSION_CONFLICT'))).toBe(true);
    expect(isVersionConflict(new ApiProblemError(409, 'ARTICLE_ARCHIVED'))).toBe(false);
    expect(isVersionConflict(new Error('x'))).toBe(false);
  });

  it('normalizes tags and validates the title', () => {
    expect(normalizeTags([' Guide ', 'guide', '', 'FAQ'])).toEqual(['guide', 'faq']);
    expect(validateTitle('  ')).toBe(false);
    expect(validateTitle('x'.repeat(301))).toBe(false);
    expect(validateTitle('Ok')).toBe(true);
  });

  it('builds the request from the form: trimmed title, tags and the files used in the content', () => {
    const content = {
      type: 'doc',
      content: [{ type: 'fileAttachment', attrs: { fileId: 'f1', fileName: 'a.txt', mimeType: 'text/plain', size: 1 } }, { type: 'image', attrs: { fileId: 'f2', src: 'x' } }],
    };
    expect(toArticleInput({ title: ' T ', content, tags: ['A'], restricted: true })).toEqual({
      title: 'T',
      content,
      tags: ['a'],
      restricted: true,
      attachmentIds: ['f1', 'f2'],
    });
  });

  it('stores, loads and clears drafts; a draft is usable only for the same version and when it differs', () => {
    const storage = memoryStorage();
    const a = article();
    const form = { ...formFromArticle(a), title: 'Changed' };
    saveDraft(storage, a.id, { baseVersion: 3, form, savedAt: 1 });
    const draft = loadDraft(storage, a.id);
    expect(draft?.form.title).toBe('Changed');
    expect(draftIsUsable(draft, a)).toBe(true);
    expect(draftIsUsable(draft, { ...a, version: 4 })).toBe(false);
    expect(draftIsUsable({ baseVersion: 3, form: formFromArticle(a), savedAt: 1 }, a)).toBe(false);
    expect(draftIsUsable(null, a)).toBe(false);
    clearDraft(storage, a.id);
    expect(loadDraft(storage, a.id)).toBeNull();
    storage.setItem(draftKey('bad'), '{oops');
    expect(loadDraft(storage, 'bad')).toBeNull();
  });
});

describe('tree logic', () => {
  it('finds the path and the sub-tree', () => {
    expect(pathTo(TREE, 'a2')).toEqual(['root', 'a1', 'a2']);
    expect(pathTo(TREE, 'nope')).toEqual([]);
    expect([...subtreeIds(TREE, 'a1')].sort()).toEqual(['a1', 'a2']);
  });

  it('never offers the page itself or its sub-pages as a new parent', () => {
    expect(parentOptions(TREE, 'a1').map((o) => o.value)).toEqual(['root', 'other']);
  });
});

describe('article page (read mode)', () => {
  it('shows breadcrumbs, tags and content, and the edit actions with kb.edit', async () => {
    renderRoute('/knowledge/a1', authWith(['kb.view', 'kb.edit']));
    expect(await screen.findByRole('heading', { name: 'Onboarding guide' })).toBeInTheDocument();
    const crumbs = screen.getByLabelText('Навігаційний ланцюжок');
    expect(within(crumbs).getByRole('link', { name: 'Handbook' })).toHaveAttribute('href', '/knowledge/root');
    expect(await screen.findByText('Welcome to the council')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'guide' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Редагувати' })).toHaveAttribute('href', '/knowledge/a1/edit');
    expect(screen.queryByRole('button', { name: 'Архівувати' })).not.toBeInTheDocument();
  });

  it('hides editing and archiving without permissions, shows archive with kb.archive', async () => {
    const { unmount } = renderRoute('/knowledge/a1', authWith(['kb.view']));
    await screen.findByRole('heading', { name: 'Onboarding guide' });
    expect(screen.queryByRole('link', { name: 'Редагувати' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Архівувати' })).not.toBeInTheDocument();
    unmount();
    renderRoute('/knowledge/a1', authWith(['kb.view', 'kb.archive']));
    expect(await screen.findByRole('button', { name: 'Архівувати' })).toBeInTheDocument();
  });

  it('shows a not-found message for a hidden or missing article', async () => {
    vi.mocked(api.getArticle).mockRejectedValue(new ApiProblemError(404, 'NOT_FOUND'));
    renderRoute('/knowledge/zzz', authWith(['kb.view']));
    expect(await screen.findByText(/не існує/)).toBeInTheDocument();
  });
});

describe('knowledge home', () => {
  it('lists tags with counts and filters by the chosen tag', async () => {
    vi.mocked(api.listArticles).mockResolvedValue([
      { id: 'a1', title: 'Onboarding guide', parentArticleId: null, restricted: false, version: 1, tags: ['guide'], archived: false, updatedAt: '2031-01-01T10:00:00Z' },
    ]);
    renderRoute('/knowledge', authWith(['kb.view', 'kb.edit']));
    expect(await screen.findByRole('link', { name: 'Onboarding guide' })).toBeInTheDocument();
    await userEvent.click(await screen.findByRole('button', { name: 'guide · 2' }));
    await waitFor(() => expect(api.listArticles).toHaveBeenLastCalledWith('guide', false));
    expect(screen.getAllByRole('link', { name: 'Нова сторінка' }).length).toBeGreaterThan(0);
  });
});

describe('edit mode', () => {
  it('inserts a table from the toolbar Insert menu', async () => {
    renderRoute('/knowledge/a1/edit', authWith(['kb.view', 'kb.edit']));
    await screen.findByRole('toolbar');
    await userEvent.click(screen.getByRole('button', { name: 'Вставити блок' }));
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Таблиця', hidden: true }));
    await waitFor(() => expect(document.querySelector('.sc-editor table')).not.toBeNull());
  });

  it('offers every block type in the Insert menu', async () => {
    renderRoute('/knowledge/a1/edit', authWith(['kb.view', 'kb.edit']));
    await screen.findByRole('toolbar');
    await userEvent.click(screen.getByRole('button', { name: 'Вставити блок' }));
    // Mantine leaves the dropdown at display:none during its open transition in jsdom, so include hidden nodes.
    await waitFor(() => expect(screen.getAllByRole('menuitem', { hidden: true }).length).toBeGreaterThan(10));
    const names = screen.getAllByRole('menuitem', { hidden: true }).map((el) => el.textContent);
    for (const name of ['Заголовок 1', 'Маркований список', 'Нумерований список', 'Чекліст', 'Цитата', 'Блок коду', 'Роздільник', 'Виноска', 'Таблиця', 'Посилання', 'Зображення', 'Прикріплений файл', 'Вбудований PDF']) {
      expect(names).toContain(name);
    }
  });

  it('saves with the loaded version and goes back to the page', async () => {
    vi.mocked(api.updateArticle).mockResolvedValue(article({ version: 4, title: 'New title' }));
    renderRoute('/knowledge/a1/edit', authWith(['kb.view', 'kb.edit']));
    const title = await screen.findByLabelText(/Заголовок/, { selector: 'input' });
    await userEvent.clear(title);
    await userEvent.type(title, 'New title');
    await userEvent.click(screen.getByRole('button', { name: 'Зберегти' }));
    await waitFor(() => expect(api.updateArticle).toHaveBeenCalledTimes(1));
    expect(api.updateArticle).toHaveBeenCalledWith('a1', 3, expect.objectContaining({ title: 'New title', content: expect.objectContaining({ type: 'doc' }) }));
  });

  it('on a version conflict keeps the work and can save it over the latest version', async () => {
    vi.mocked(api.updateArticle).mockRejectedValueOnce(new ApiProblemError(409, 'ARTICLE_VERSION_CONFLICT')).mockResolvedValueOnce(article({ version: 6 }));
    renderRoute('/knowledge/a1/edit', authWith(['kb.view', 'kb.edit']));
    const title = await screen.findByLabelText(/Заголовок/, { selector: 'input' });
    await userEvent.clear(title);
    await userEvent.type(title, 'Mine');
    await userEvent.click(screen.getByRole('button', { name: 'Зберегти' }));
    const alert = await screen.findByTestId('conflict-alert');
    expect(title).toHaveValue('Mine');

    vi.mocked(api.getArticle).mockResolvedValue(article({ version: 5, title: 'Theirs' }));
    await userEvent.click(within(alert).getByRole('button', { name: /Зберегти мою версію/ }));
    await waitFor(() => expect(api.updateArticle).toHaveBeenCalledTimes(2));
    expect(vi.mocked(api.updateArticle).mock.calls[1][1]).toBe(5);
    expect(vi.mocked(api.updateArticle).mock.calls[1][2].title).toBe('Mine');
  });

  it('requires a title and does not call the API without one', async () => {
    renderRoute('/knowledge/new', authWith(['kb.view', 'kb.edit']));
    await screen.findByRole('toolbar');
    await userEvent.click(screen.getByRole('button', { name: 'Зберегти' }));
    expect(await screen.findByText(/Введіть заголовок/)).toBeInTheDocument();
    expect(api.createArticle).not.toHaveBeenCalled();
  });

  it('creates a sub-page under the parent from the query string', async () => {
    vi.mocked(api.createArticle).mockResolvedValue(article({ id: 'new1', version: 1 }));
    renderRoute('/knowledge/new?parent=a1', authWith(['kb.view', 'kb.edit']));
    await userEvent.type(await screen.findByLabelText(/Заголовок/, { selector: 'input' }), 'Child');
    await userEvent.click(screen.getByRole('button', { name: 'Зберегти' }));
    await waitFor(() => expect(api.createArticle).toHaveBeenCalled());
    expect(vi.mocked(api.createArticle).mock.calls[0][1]).toBe('a1');
  });

  it('restores an unsaved draft for the same version', async () => {
    const a = article();
    saveDraft(window.localStorage, a.id, { baseVersion: 3, form: { ...formFromArticle(a), title: 'Draft title' }, savedAt: 1 });
    renderRoute('/knowledge/a1/edit', authWith(['kb.view', 'kb.edit']));
    expect(await screen.findByDisplayValue('Draft title')).toBeInTheDocument();
    expect(screen.getByText(/Відновлено незбережену чернетку/)).toBeInTheDocument();
  });

  it('refuses to edit without kb.edit', async () => {
    renderRoute('/knowledge/a1/edit', authWith(['kb.view']));
    expect(await screen.findByText(/немає прав/)).toBeInTheDocument();
    expect(screen.queryByRole('toolbar')).not.toBeInTheDocument();
  });
});

describe('version history', () => {
  const VERSIONS = [
    { versionNumber: 3, title: 'Onboarding guide', editedAt: '2031-01-01T10:00:00Z' },
    { versionNumber: 2, title: 'Old title', editedAt: '2030-12-01T10:00:00Z' },
  ];
  beforeEach(() => {
    vi.mocked(api.listArticleVersions).mockResolvedValue(VERSIONS);
    vi.mocked(api.getArticleVersion).mockResolvedValue({ ...VERSIONS[1], content: doc('Old body') });
  });

  it('lists versions, previews one and restores it after confirmation with kb.edit', async () => {
    vi.mocked(api.restoreArticleVersion).mockResolvedValue(article({ version: 4 }));
    renderRoute('/knowledge/a1', authWith(['kb.view', 'kb.edit']));
    await userEvent.click(await screen.findByRole('button', { name: 'Історія' }));
    await userEvent.click(await screen.findByText('Old title'));
    expect(await screen.findByText('Old body')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Відновити цю версію' }));
    expect(screen.getByText(/Теги, вкладення та ознака обмеженого доступу/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Відновити' }));
    await waitFor(() => expect(api.restoreArticleVersion).toHaveBeenCalledWith('a1', 2, 3));
  });

  it('hides restore without kb.edit', async () => {
    renderRoute('/knowledge/a1', authWith(['kb.view']));
    await userEvent.click(await screen.findByRole('button', { name: 'Історія' }));
    await userEvent.click(await screen.findByText('Old title'));
    expect(await screen.findByText('Old body')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Відновити цю версію' })).not.toBeInTheDocument();
  });

  it('shows the problem when the server answers 403', async () => {
    vi.mocked(api.restoreArticleVersion).mockRejectedValue(new ApiProblemError(403, undefined));
    renderRoute('/knowledge/a1', authWith(['kb.view', 'kb.edit']));
    await userEvent.click(await screen.findByRole('button', { name: 'Історія' }));
    await userEvent.click(await screen.findByText('Old title'));
    await userEvent.click(await screen.findByRole('button', { name: 'Відновити цю версію' }));
    await userEvent.click(screen.getByRole('button', { name: 'Відновити' }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
});
