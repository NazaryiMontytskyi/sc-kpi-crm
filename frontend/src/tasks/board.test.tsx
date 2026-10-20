import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { configure, fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { CurrentUserProvider, STUB_AUTH_STATE } from '../auth/CurrentUser';
import { changeLanguage } from '../i18n';
import { renderWithProviders } from '../test/render';
import { BoardPage } from './BoardPage';
import { boardColumns, DRAG_MIME, groupByStatus, withTaskStatus } from './boardLogic';
import * as api from './tasksApi';

vi.mock('./tasksApi', async (orig) => ({
  ...(await orig<typeof api>()),
  listSpaces: vi.fn(),
  getSpace: vi.fn(),
  listTasks: vi.fn(),
  changeTaskStatus: vi.fn(),
}));

vi.setConfig({ testTimeout: 30000 });
configure({ asyncUtilTimeout: 8000 });

const st = (id: string, name: string, position: number, archived = false): api.SpaceStatus => ({
  id,
  name,
  category: 'TODO',
  position,
  archived,
});
// Deliberately unordered to prove columns follow `position`.
const STATUSES = [st('s2', 'Фейкове в роботі', 1), st('s1', 'Фейкове нове', 0), st('s3', 'Фейкове стара', 2, true), st('s4', 'Фейкове готово', 3)];
const SPACE: api.Space = { id: 'sp1', name: 'Фейковий простір', orgUnitId: null, general: true, archived: false, statuses: STATUSES, customFields: [] };
const task = (over: Partial<api.TaskSummary>): api.TaskSummary => ({
  id: 't1',
  spaceId: 'sp1',
  title: 'Фейкова задача',
  statusId: 's1',
  priority: 'NORMAL',
  dueDate: null,
  labels: [],
  attachmentCount: 0,
  authorId: 'a',
  archived: false,
  createdAt: '2031-01-01T10:00:00Z',
  ...over,
});
const T1 = task({ id: 't1', title: 'Перша', dueDate: '2000-01-01', labels: ['мітка'] });
const T2 = task({ id: 't2', title: 'Друга', statusId: 's2' });

function renderBoard() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderWithProviders(
    <QueryClientProvider client={queryClient}>
      <CurrentUserProvider value={{ ...STUB_AUTH_STATE, user: { id: 'u', displayName: 'U', isAdmin: false, permissions: ['tasks.view'] } }}>
        <BoardPage />
      </CurrentUserProvider>
    </QueryClientProvider>,
    '/tasks/board?space=sp1',
  );
}

beforeEach(async () => {
  vi.clearAllMocks();
  await changeLanguage('uk');
  vi.mocked(api.listSpaces).mockResolvedValue([SPACE]);
  vi.mocked(api.getSpace).mockResolvedValue(SPACE);
  vi.mocked(api.listTasks).mockResolvedValue({ items: [T1, T2], page: 0, totalPages: 1, totalElements: 2 });
  vi.mocked(api.changeTaskStatus).mockResolvedValue({ ...T1, statusId: 's2', description: null, customFieldValues: {}, relatedTaskIds: [], attachmentIds: [] });
});

describe('board logic', () => {
  it('orders columns by position and drops archived statuses', () => {
    expect(boardColumns(STATUSES).map((c) => c.id)).toEqual(['s1', 's2', 's4']);
  });
  it('groups tasks by status keeping empty columns', () => {
    const g = groupByStatus([T1, T2], boardColumns(STATUSES));
    expect(g.get('s1')).toEqual([T1]);
    expect(g.get('s4')).toEqual([]);
  });
  it('moves a task immutably', () => {
    const next = withTaskStatus([T1, T2], 't1', 's4');
    expect(next[0].statusId).toBe('s4');
    expect(T1.statusId).toBe('s1');
  });
});

describe('BoardPage', () => {
  it('renders one column per active status in order, with overdue highlight and labels', async () => {
    renderBoard();
    const cols = await screen.findAllByTestId('board-column');
    expect(cols.map((c) => c.getAttribute('aria-label'))).toEqual(['Фейкове нове', 'Фейкове в роботі', 'Фейкове готово']);
    expect(within(cols[0]).getByText('Перша')).toBeInTheDocument();
    expect(within(cols[0]).getByText('мітка')).toBeInTheDocument();
    expect(within(cols[0]).getByTestId('board-overdue')).toBeInTheDocument();
    expect(within(cols[1]).getByText('Друга')).toBeInTheDocument();
  });

  it('moves a card via the menu without drag-and-drop and calls the status API', async () => {
    const user = userEvent.setup();
    renderBoard();
    const cols = await screen.findAllByTestId('board-column');
    await user.click(within(cols[0]).getByRole('button', { name: /Перша/ }));
    vi.mocked(api.listTasks).mockResolvedValue({ items: [{ ...T1, statusId: 's4' }, T2], page: 0, totalPages: 1, totalElements: 2 }); // server state after the move
    await user.click(await screen.findByRole('menuitem', { name: 'Фейкове готово' }));
    expect(api.changeTaskStatus).toHaveBeenCalledWith('t1', 's4');
    await waitFor(() => expect(within(screen.getAllByTestId('board-column')[2]).getByText('Перша')).toBeInTheDocument());
  });

  it('moves a card by dropping it on a column', async () => {
    renderBoard();
    const cols = await screen.findAllByTestId('board-column');
    const data: Record<string, string> = { [DRAG_MIME]: 't1' };
    const dataTransfer = { types: [DRAG_MIME], getData: (k: string) => data[k] ?? '', setData: vi.fn() };
    fireEvent.drop(cols[1], { dataTransfer });
    await waitFor(() => expect(api.changeTaskStatus).toHaveBeenCalledWith('t1', 's2'));
  });

  it('rolls back and shows a message when the move fails', async () => {
    vi.mocked(api.changeTaskStatus).mockRejectedValue(new ApiProblemError(403, 'TASK_EDIT_FORBIDDEN'));
    const user = userEvent.setup();
    renderBoard();
    const cols = await screen.findAllByTestId('board-column');
    await user.click(within(cols[0]).getByRole('button', { name: /Перша/ }));
    await user.click(await screen.findByRole('menuitem', { name: 'Фейкове готово' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Не вдалося перенести задачу');
    await waitFor(() => expect(within(screen.getAllByTestId('board-column')[0]).getByText('Перша')).toBeInTheDocument());
  });
});
