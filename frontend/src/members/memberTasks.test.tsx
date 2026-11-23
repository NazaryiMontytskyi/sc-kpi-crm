import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { configure, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CurrentUserProvider, STUB_AUTH_STATE, type CurrentUser } from '../auth/CurrentUser';
import { changeLanguage } from '../i18n';
import * as api from '../tasks/memberTasksApi';
import { renderWithProviders } from '../test/render';
import { MemberTasksSection } from './MemberTasksSection';

vi.mock('../tasks/memberTasksApi', async (orig) => ({
  ...(await orig<typeof api>()),
  listMemberTasks: vi.fn(),
  getMemberTaskCounts: vi.fn(),
}));

vi.setConfig({ testTimeout: 30000 });
configure({ asyncUtilTimeout: 8000 });

const item = (over: Partial<api.MemberTask>): api.MemberTask => ({
  id: 't1',
  parentId: null,
  title: 'Фейкова задача',
  statusName: 'Фейковий статус',
  deadline: '2031-03-05',
  overdue: false,
  ...over,
});

const viewer = (permissions: string[]): CurrentUser => ({ id: 'viewer', displayName: 'Test', isAdmin: false, permissions });

function Where() {
  const loc = useLocation();
  return <output data-testid="where">{loc.pathname}</output>;
}

function renderSection(who: CurrentUser) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderWithProviders(
    <QueryClientProvider client={queryClient}>
      <CurrentUserProvider value={{ ...STUB_AUTH_STATE, user: who }}>
        <Routes>
          <Route path="/members/:id" element={<MemberTasksSection userId="m1" />} />
          <Route path="*" element={<Where />} />
        </Routes>
      </CurrentUserProvider>
    </QueryClientProvider>,
    '/members/m1',
  );
}

beforeEach(async () => {
  vi.clearAllMocks();
  await changeLanguage('uk');
  vi.mocked(api.getMemberTaskCounts).mockResolvedValue({ active: 2, overdue: 1, done: 7 });
  vi.mocked(api.listMemberTasks).mockResolvedValue({
    items: [
      item({ id: 'o1', title: 'Фейкова прострочена', deadline: '2030-01-10', overdue: true }),
      item({ id: 's1', title: 'Фейкова підзадача', parentId: 'p1', deadline: null }),
    ],
    page: 0,
    totalPages: 1,
    totalElements: 2,
  });
});

describe('MemberTasksSection', () => {
  it('shows the counters and the open tasks the member is assigned to', async () => {
    renderSection(viewer(['tasks.view']));
    expect(await screen.findByRole('heading', { name: 'Задачі' })).toBeInTheDocument();
    await waitFor(() => expect(screen.getByTestId('count-active')).toHaveTextContent('2'));
    expect(screen.getByTestId('count-overdue')).toHaveTextContent('1');
    expect(screen.getByTestId('count-done')).toHaveTextContent('7');
    expect(api.getMemberTaskCounts).toHaveBeenCalledWith('m1');
    expect(api.listMemberTasks).toHaveBeenCalledWith('m1', 0);

    const [overdue, subtask] = await screen.findAllByTestId('member-task');
    expect(within(overdue).getByText('Прострочено')).toBeInTheDocument();
    expect(within(overdue).getByText('Дедлайн: 10.01.2030')).toBeInTheDocument();
    expect(within(overdue).getByText('Фейковий статус')).toBeInTheDocument();
    expect(within(subtask).getByText('Підзадача')).toBeInTheDocument();
    expect(within(subtask).getByText('Без дедлайну')).toBeInTheDocument();
  });

  it('opens a task in the tracker panel', async () => {
    renderSection(viewer(['tasks.view']));
    await userEvent.click(await screen.findByRole('link', { name: 'Фейкова прострочена' }));
    expect(screen.getByTestId('where')).toHaveTextContent('/tasks/o1');
  });

  it('loads further pages on demand', async () => {
    vi.mocked(api.listMemberTasks)
      .mockResolvedValueOnce({ items: [item({ id: 'a' })], page: 0, totalPages: 2, totalElements: 2 })
      .mockResolvedValueOnce({ items: [item({ id: 'b', title: 'Друга фейкова' })], page: 1, totalPages: 2, totalElements: 2 });
    renderSection(viewer(['tasks.view']));
    await userEvent.click(await screen.findByRole('button', { name: 'Показати ще' }));
    expect(await screen.findByText('Друга фейкова')).toBeInTheDocument();
    expect(api.listMemberTasks).toHaveBeenLastCalledWith('m1', 1);
    expect(screen.queryByRole('button', { name: 'Показати ще' })).toBeNull();
  });

  it('shows the empty state in en', async () => {
    await changeLanguage('en');
    vi.mocked(api.listMemberTasks).mockResolvedValue({ items: [], page: 0, totalPages: 0, totalElements: 0 });
    renderSection(viewer(['tasks.view']));
    expect(await screen.findByText('No open tasks.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Tasks' })).toBeInTheDocument();
    await changeLanguage('uk');
  });

  it('is hidden and calls nothing without tasks.view', () => {
    renderSection(viewer([]));
    expect(screen.queryByTestId('member-tasks')).toBeNull();
    expect(api.listMemberTasks).not.toHaveBeenCalled();
    expect(api.getMemberTaskCounts).not.toHaveBeenCalled();
  });
});
