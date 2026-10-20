import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { configure, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CurrentUserProvider, STUB_AUTH_STATE } from '../auth/CurrentUser';
import { changeLanguage } from '../i18n';
import * as memberApi from '../members/memberApi';
import { renderWithProviders } from '../test/render';
import { doneStatuses, flattenChain, isInChain, MAX_INDENT_LEVELS, showSuggestDone } from './resolutionLogic';
import * as api from './resolutionsApi';
import { ResolutionsPanel } from './ResolutionsPanel';
import { SuggestDoneBanner } from './SuggestDoneBanner';
import * as tasksApi from './tasksApi';

vi.mock('./resolutionsApi', async (orig) => ({
  ...(await orig<typeof api>()),
  listTaskResolutions: vi.fn(),
  getResolutionChain: vi.fn(),
}));
vi.mock('./tasksApi', async (orig) => ({ ...(await orig<typeof tasksApi>()), changeTaskStatus: vi.fn() }));
vi.mock('../members/memberApi', async (orig) => ({ ...(await orig<typeof memberApi>()), listMembers: vi.fn() }));

vi.setConfig({ testTimeout: 30000 });
configure({ asyncUtilTimeout: 8000 });

const member = (id: string, lastName: string): memberApi.MemberSummary => ({
  id,
  lastName,
  firstName: 'Фейк',
  middleName: '',
  faculty: null,
  studyGroup: '',
  telegram: '',
  joinedAt: '2031-01-01',
  avatarFileId: null,
  archived: false,
});

const res = (id: string, over: Partial<api.Resolution> = {}): api.Resolution => ({
  id,
  taskId: 't1',
  authorId: 'alice',
  executorId: 'bob',
  coExecutorIds: [],
  instruction: `Доручення ${id}`,
  dueDate: '2031-06-01',
  parentResolutionId: null,
  state: 'NEW',
  overdue: false,
  createdAt: '2031-01-01T10:00:00Z',
  updatedAt: '2031-01-01T10:00:00Z',
  ...over,
});
const node = (id: string, over: Partial<api.Resolution> = {}, children: api.ResolutionChainNode[] = []): api.ResolutionChainNode => ({
  ...res(id, over),
  children,
});

// r1 -> r2 -> r3 -> r4 -> r5 (5 levels) plus a sibling branch r2b under r1
const CHAIN = node('r1', { state: 'REDIRECTED', executorId: 'alice' }, [
  node('r2', { state: 'REDIRECTED', executorId: 'bob', parentResolutionId: 'r1' }, [
    node('r3', { state: 'REDIRECTED', executorId: 'carol', parentResolutionId: 'r2' }, [
      node('r4', { state: 'REDIRECTED', parentResolutionId: 'r3' }, [node('r5', { state: 'ACCEPTED', parentResolutionId: 'r4' })]),
    ]),
  ]),
  node('r2b', { state: 'CANCELED', parentResolutionId: 'r1' }),
]);

const TODO: tasksApi.SpaceStatus = { id: 's1', name: 'Фейкове до виконання', category: 'TODO', position: 0, archived: false };
const DONE1: tasksApi.SpaceStatus = { id: 's2', name: 'Фейкове виконано', category: 'DONE', position: 1, archived: false };
const DONE2: tasksApi.SpaceStatus = { id: 's3', name: 'Фейкове перевірено', category: 'DONE', position: 2, archived: false };
const DONE_OLD: tasksApi.SpaceStatus = { id: 's4', name: 'Фейкове старе', category: 'DONE', position: 3, archived: true };

const TASK: tasksApi.Task = {
  id: 't1',
  spaceId: 'sp1',
  title: 'Фейкова задача',
  statusId: 's1',
  priority: 'NORMAL',
  dueDate: null,
  labels: [],
  attachmentCount: 0,
  authorId: 'alice',
  archived: false,
  createdAt: '2031-01-01T10:00:00Z',
  description: null,
  customFieldValues: {},
  relatedTaskIds: [],
  attachmentIds: [],
  suggestDone: true,
};

function wrap(ui: React.ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderWithProviders(
    <QueryClientProvider client={queryClient}>
      <CurrentUserProvider value={{ ...STUB_AUTH_STATE, user: { id: 'zed', displayName: 'Test', isAdmin: false, permissions: [] } }}>
        {ui}
      </CurrentUserProvider>
    </QueryClientProvider>,
  );
}

beforeEach(async () => {
  vi.clearAllMocks();
  // jsdom does not implement scrollIntoView, which the Mantine combobox calls
  Element.prototype.scrollIntoView = vi.fn();
  await changeLanguage('uk');
  vi.mocked(memberApi.listMembers).mockResolvedValue([member('alice', 'Фейкова'), member('bob', 'Фейковий'), member('carol', 'Тестова')]);
  vi.mocked(api.listTaskResolutions).mockResolvedValue({ items: [], page: 0, totalPages: 1, totalElements: 0 });
  vi.mocked(api.getResolutionChain).mockResolvedValue(CHAIN);
  vi.mocked(tasksApi.changeTaskStatus).mockResolvedValue({ ...TASK, statusId: 's2', suggestDone: false });
});

describe('chain logic', () => {
  it('detects resolutions that belong to a chain', () => {
    expect(isInChain({ state: 'REDIRECTED', parentResolutionId: null })).toBe(true);
    expect(isInChain({ state: 'NEW', parentResolutionId: 'r1' })).toBe(true);
    expect(isInChain({ state: 'NEW', parentResolutionId: null })).toBe(false);
  });

  it('flattens depth-first, caps the indent and skips collapsed branches', () => {
    const rows = flattenChain(CHAIN, new Set());
    expect(rows.map((r) => r.node.id)).toEqual(['r1', 'r2', 'r3', 'r4', 'r5', 'r2b']);
    expect(rows.map((r) => r.depth)).toEqual([0, 1, 2, 3, 4, 1]);
    expect(Math.max(...rows.map((r) => r.indent))).toBe(MAX_INDENT_LEVELS);
    expect(flattenChain(CHAIN, new Set(['r2'])).map((r) => r.node.id)).toEqual(['r1', 'r2', 'r2b']);
  });

  it('offers only active DONE statuses and gates the banner', () => {
    expect(doneStatuses([TODO, DONE1, DONE2, DONE_OLD]).map((s) => s.id)).toEqual(['s2', 's3']);
    expect(showSuggestDone(TASK, [TODO, DONE1])).toBe(true);
    expect(showSuggestDone({ ...TASK, suggestDone: false }, [TODO, DONE1])).toBe(false);
    expect(showSuggestDone({ ...TASK, archived: true }, [TODO, DONE1])).toBe(false);
    expect(showSuggestDone(TASK, [TODO, DONE_OLD])).toBe(false);
  });
});

describe('redirect chain tree', () => {
  it('is offered only for resolutions that are part of a chain', async () => {
    vi.mocked(api.listTaskResolutions).mockResolvedValue({
      items: [res('plain'), res('child', { parentResolutionId: 'r1' })],
      page: 0,
      totalPages: 1,
      totalElements: 2,
    });
    wrap(<ResolutionsPanel taskId="t1" taskArchived={false} />);
    expect(await screen.findAllByTestId('resolution')).toHaveLength(2);
    expect(screen.getAllByTestId('resolution-chain')).toHaveLength(1);
    expect(api.getResolutionChain).not.toHaveBeenCalled();
  });

  it('expands to a nested tree, collapses branches and marks the current resolution', async () => {
    vi.mocked(api.listTaskResolutions).mockResolvedValue({
      items: [res('r3', { state: 'REDIRECTED', parentResolutionId: 'r2' })],
      page: 0,
      totalPages: 1,
      totalElements: 1,
    });
    wrap(<ResolutionsPanel taskId="t1" taskArchived={false} />);
    await userEvent.click(await screen.findByRole('button', { name: 'Ланцюг переадресацій' }));
    expect(await screen.findAllByTestId('chain-node')).toHaveLength(6);
    expect(vi.mocked(api.getResolutionChain).mock.calls[0][0]).toBe('r3');
    const nodes = screen.getAllByTestId('chain-node');
    expect(nodes.map((n) => n.getAttribute('data-indent'))).toEqual(['0', '1', '2', '3', '3', '1']);
    expect(within(nodes[4]).getByText('Прийнята')).toBeInTheDocument();
    expect(within(nodes[5]).getByText('Скасована')).toBeInTheDocument();
    expect(screen.getAllByText('ця резолюція')).toHaveLength(1);

    await userEvent.click(within(nodes[1]).getByRole('button', { name: 'Згорнути переадресації' }));
    expect(screen.getAllByTestId('chain-node')).toHaveLength(3);
    await userEvent.click(screen.getByRole('button', { name: 'Показати переадресації' }));
    expect(screen.getAllByTestId('chain-node')).toHaveLength(6);

    await userEvent.click(screen.getByRole('button', { name: 'Сховати ланцюг' }));
    expect(screen.queryAllByTestId('chain-node')).toHaveLength(0);
  });
});

describe('SuggestDoneBanner', () => {
  it('is hidden unless suggestDone is true', () => {
    const { unmount } = wrap(<SuggestDoneBanner task={{ ...TASK, suggestDone: false }} statuses={[TODO, DONE1]} />);
    expect(screen.queryByTestId('suggest-done')).toBeNull();
    unmount();
    wrap(<SuggestDoneBanner task={TASK} statuses={[TODO, DONE1]} />);
    expect(screen.getByTestId('suggest-done')).toBeInTheDocument();
  });

  it('does not move the task until confirmed, then moves it to the chosen DONE status', async () => {
    wrap(<SuggestDoneBanner task={TASK} statuses={[TODO, DONE1, DONE2, DONE_OLD]} />);
    expect(screen.getByText('Усі резолюції прийнято')).toBeInTheDocument();
    expect(tasksApi.changeTaskStatus).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole('combobox', { name: 'Статус' }));
    expect(screen.queryByRole('option', { name: 'Фейкове старе', hidden: true })).toBeNull();
    expect(screen.queryByRole('option', { name: 'Фейкове до виконання', hidden: true })).toBeNull();
    await userEvent.click(await screen.findByRole('option', { name: 'Фейкове перевірено', hidden: true }));
    expect(tasksApi.changeTaskStatus).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole('button', { name: 'Перемістити' }));
    await waitFor(() => expect(vi.mocked(tasksApi.changeTaskStatus).mock.calls[0]).toEqual(['t1', 's3']));
    expect(tasksApi.changeTaskStatus).toHaveBeenCalledTimes(1);
  });

  it('dismissing hides the banner without moving the task', async () => {
    wrap(<SuggestDoneBanner task={TASK} statuses={[TODO, DONE1]} />);
    await userEvent.click(screen.getByRole('button', { name: 'Не зараз' }));
    expect(screen.queryByTestId('suggest-done')).toBeNull();
    expect(tasksApi.changeTaskStatus).not.toHaveBeenCalled();
  });

  it('moves to the only DONE status without a picker', async () => {
    wrap(<SuggestDoneBanner task={TASK} statuses={[TODO, DONE1]} />);
    expect(screen.queryByRole('combobox', { name: 'Статус' })).toBeNull();
    await userEvent.click(screen.getByRole('button', { name: 'Перемістити' }));
    await waitFor(() => expect(vi.mocked(tasksApi.changeTaskStatus).mock.calls[0]).toEqual(['t1', 's2']));
  });
});
