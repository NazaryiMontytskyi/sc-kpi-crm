import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { configure, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { STUB_AUTH_STATE, CurrentUserProvider } from '../auth/CurrentUser';
import { changeLanguage } from '../i18n';
import * as memberApi from '../members/memberApi';
import { renderWithProviders } from '../test/render';
import { TasksPage } from './TasksPage';
import * as api from './tasksApi';
import { criteriaFromQuery, DEFAULT_QUERY, activeFilterCount, hasCriteria, queryFromCriteria, sameCriteria } from './taskLogic';

vi.mock('./tasksApi', async (orig) => ({
  ...(await orig<typeof api>()),
  listSpaces: vi.fn(),
  getSpace: vi.fn(),
  listTasks: vi.fn(),
  listSavedFilters: vi.fn(),
  createSavedFilter: vi.fn(),
  deleteSavedFilter: vi.fn(),
}));
vi.mock('../members/memberApi', async (orig) => ({ ...(await orig<typeof memberApi>()), listMembers: vi.fn() }));

vi.setConfig({ testTimeout: 30000 });
configure({ asyncUtilTimeout: 8000 });

const SAVED: api.SavedFilter = {
  id: 'f1',
  name: 'Фейковий фільтр',
  criteria: { labels: ['терміново'], view: 'OVERDUE', resolutionState: 'NEW' },
};

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderWithProviders(
    <QueryClientProvider client={queryClient}>
      <CurrentUserProvider value={{ ...STUB_AUTH_STATE, user: { id: 'me', displayName: 'Test', isAdmin: false, permissions: ['tasks.view'] } }}>
        <TasksPage />
      </CurrentUserProvider>
    </QueryClientProvider>,
    '/tasks',
  );
}

const lastQuery = () => vi.mocked(api.listTasks).mock.calls.at(-1)![0];

beforeEach(async () => {
  vi.clearAllMocks();
  await changeLanguage('uk');
  vi.mocked(api.listSpaces).mockResolvedValue([]);
  vi.mocked(api.listTasks).mockResolvedValue({ items: [], page: 0, totalPages: 0, totalElements: 0 });
  vi.mocked(api.listSavedFilters).mockResolvedValue([SAVED]);
  vi.mocked(memberApi.listMembers).mockResolvedValue([]);
});

describe('saved filter logic', () => {
  it('round-trips criteria through a query and keeps sorting', () => {
    const base = { ...DEFAULT_QUERY, sortBy: 'title' as const, direction: 'asc' as const, page: 3, includeArchived: true };
    const q = queryFromCriteria(SAVED.criteria, base);
    expect(q).toMatchObject({ view: 'OVERDUE', resolutionState: 'NEW', labels: ['терміново'], sortBy: 'title', page: 0, includeArchived: false });
    expect(sameCriteria(criteriaFromQuery(q), SAVED.criteria)).toBe(true);
  });

  it('ignores label order and detects empty criteria', () => {
    expect(sameCriteria({ labels: ['a', 'b'] }, { labels: ['b', 'a'] })).toBe(true);
    expect(sameCriteria({ labels: [], view: 'OVERDUE' }, { labels: [] })).toBe(false);
    expect(hasCriteria(criteriaFromQuery(DEFAULT_QUERY))).toBe(false);
    expect(hasCriteria({ labels: [], executorId: 'x' })).toBe(true);
  });

  it('counts the resolution filters', () => {
    expect(activeFilterCount({ ...DEFAULT_QUERY, executorId: 'x', authorId: 'y', resolutionState: 'NEW' })).toBe(3);
  });
});

describe('quick views and saved filters', () => {
  it('applies a quick view and clears it on the second click', async () => {
    const user = userEvent.setup();
    renderPage();
    await waitFor(() => expect(api.listTasks).toHaveBeenCalled());
    expect(lastQuery().view).toBeUndefined();
    await user.click(screen.getByLabelText('Мої резолюції'));
    await waitFor(() => expect(lastQuery().view).toBe('MY_RESOLUTIONS'));
    await user.click(screen.getByLabelText('Прострочені'));
    await waitFor(() => expect(lastQuery().view).toBe('OVERDUE'));
    await user.click(screen.getByLabelText('Прострочені'));
    await waitFor(() => expect(lastQuery().view).toBeUndefined());
  });

  it('applies a saved filter from the menu', async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole('button', { name: /Збережені фільтри/ }));
    await user.click(await screen.findByRole('menuitem', { name: 'Фейковий фільтр' }));
    await waitFor(() => expect(lastQuery()).toMatchObject({ view: 'OVERDUE', resolutionState: 'NEW', labels: ['терміново'] }));
  });

  it('saves the current filter under a name', async () => {
    const user = userEvent.setup();
    vi.mocked(api.createSavedFilter).mockResolvedValue({ id: 'f2', name: 'Мій', criteria: { labels: [], view: 'ISSUED_BY_ME' } });
    renderPage();
    await user.click(await screen.findByLabelText('Видані мною'));
    await waitFor(() => expect(lastQuery().view).toBe('ISSUED_BY_ME'));
    await user.click(screen.getByRole('button', { name: /Збережені фільтри/ }));
    await user.click(await screen.findByRole('menuitem', { name: /Зберегти поточний фільтр/ }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Зберегти' }));
    expect(await within(dialog).findByText('Вкажіть назву.')).toBeInTheDocument();
    expect(api.createSavedFilter).not.toHaveBeenCalled();
    await user.type(within(dialog).getByLabelText('Назва'), '  Мій  ');
    await user.click(within(dialog).getByRole('button', { name: 'Зберегти' }));
    await waitFor(() => expect(api.createSavedFilter).toHaveBeenCalled());
    expect(vi.mocked(api.createSavedFilter).mock.calls[0][0]).toBe('Мій');
    expect(vi.mocked(api.createSavedFilter).mock.calls[0][1]).toMatchObject({ view: 'ISSUED_BY_ME', labels: [] });
  });

  it('removes a saved filter', async () => {
    const user = userEvent.setup();
    vi.mocked(api.deleteSavedFilter).mockResolvedValue(undefined);
    renderPage();
    await user.click(await screen.findByRole('button', { name: /Збережені фільтри/ }));
    await user.click(await screen.findByRole('menuitem', { name: /Керувати збереженими/ }));
    await user.click(await screen.findByLabelText('Видалити фільтр Фейковий фільтр'));
    await waitFor(() => expect(api.deleteSavedFilter).toHaveBeenCalled());
    expect(vi.mocked(api.deleteSavedFilter).mock.calls[0][0]).toBe('f1');
  });
});
