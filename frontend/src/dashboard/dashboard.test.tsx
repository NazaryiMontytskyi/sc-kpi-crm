import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { configure, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CurrentUserProvider, STUB_AUTH_STATE, type CurrentUser } from '../auth/CurrentUser';
import { changeLanguage } from '../i18n';
import { HomePage } from '../pages/HomePage';
import { addDays, todayKyiv } from '../tasks/taskLogic';
import * as tasksApi from '../tasks/tasksApi';
import { renderWithProviders } from '../test/render';
import * as api from './dashboardApi';
import { isCounterHighlighted, WIDGET_LINKS, WIDGET_TASKS_PER_GROUP } from './dashboardLogic';

vi.mock('./dashboardApi', async (orig) => ({ ...(await orig<typeof api>()), getMyDashboard: vi.fn() }));
vi.mock('../tasks/tasksApi', async (orig) => ({
  ...(await orig<typeof tasksApi>()),
  listTasks: vi.fn(),
  getSpace: vi.fn(),
}));
vi.mock('../knowledge/OnboardingPrompt', () => ({ OnboardingPrompt: () => null }));

vi.setConfig({ testTimeout: 30000 });
configure({ asyncUtilTimeout: 8000 });

const SPACE: tasksApi.Space = {
  id: 'sp1',
  name: 'Фейковий простір',
  orgUnitId: null,
  general: true,
  archived: false,
  statuses: [{ id: 's1', name: 'Фейкове нове', category: 'TODO', position: 0, archived: false }],
  customFields: [],
};

const task = (id: string, title: string, deadline: string | null): tasksApi.TaskSummary => ({
  id,
  spaceId: 'sp1',
  title,
  statusId: 's1',
  priority: 'NORMAL',
  deadline,
  labels: [],
  attachmentCount: 0,
  authorId: 'boss',
  archived: false,
  createdAt: '2031-01-01T10:00:00Z',
  parentId: null,
  version: 1,
  assigneeIds: ['bob'],
});

const TODAY = todayKyiv();
const OVERDUE = [1, 2, 3, 4].map((n) => task(`o${n}`, `Фейкова прострочена ${n}`, addDays(TODAY, -n)));
const NO_DEADLINE = task('n1', 'Фейкова без дедлайну', null);

const user = (permissions: string[]): CurrentUser => ({ id: 'bob', displayName: 'Test', isAdmin: false, permissions });

function Where() {
  const loc = useLocation();
  return <output data-testid="where">{loc.pathname + loc.search}</output>;
}

function renderHome(who: CurrentUser) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderWithProviders(
    <QueryClientProvider client={queryClient}>
      <CurrentUserProvider value={{ ...STUB_AUTH_STATE, user: who }}>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="*" element={<Where />} />
        </Routes>
      </CurrentUserProvider>
    </QueryClientProvider>,
  );
}

const group = (key: string) => screen.getAllByTestId('overview-group').find((g) => g.dataset.group === key)!;

beforeEach(async () => {
  vi.clearAllMocks();
  await changeLanguage('uk');
  vi.mocked(api.getMyDashboard).mockResolvedValue({ active: 5, overdue: 4 });
  vi.mocked(tasksApi.getSpace).mockResolvedValue(SPACE);
  vi.mocked(tasksApi.listTasks).mockResolvedValue({ items: [...OVERDUE, NO_DEADLINE], page: 0, totalPages: 1, totalElements: 5 });
});

describe('dashboard logic', () => {
  it('links every counter to the "My tasks" overview of the tracker', () => {
    expect(WIDGET_LINKS.active).toBe('/tasks');
    expect(WIDGET_LINKS.overdue).toBe('/tasks');
  });

  it('highlights only a non-zero overdue counter', () => {
    expect(isCounterHighlighted('overdue', 1)).toBe(true);
    expect(isCounterHighlighted('overdue', 0)).toBe(false);
    expect(isCounterHighlighted('active', 5)).toBe(false);
  });
});

describe('MyTasksWidget', () => {
  it('shows "My metrics" from the tasks section and my tasks grouped by deadline', async () => {
    renderHome(user(['dashboard.view', 'tasks.view']));
    expect(await screen.findByRole('heading', { name: 'Мої задачі' })).toBeInTheDocument();
    await waitFor(() => expect(screen.getByTestId('dashboard-value-active')).toHaveTextContent('5'));
    expect(screen.getByTestId('dashboard-value-overdue')).toHaveTextContent('4');
    expect(screen.getByTestId('dashboard-counter-active')).toHaveAttribute('href', '/tasks');
    expect(screen.getByRole('link', { name: 'Відкрити трекер' })).toHaveAttribute('href', '/tasks');

    // The overview reads the tasks I am an assignee of (spec RR-5, UI-4).
    await screen.findByText('Фейкова без дедлайну');
    expect(vi.mocked(tasksApi.listTasks).mock.calls[0][0]).toMatchObject({ view: 'MY_TASKS' });
    expect(group('OVERDUE').dataset.count).toBe('4');
    // A few tasks per group on the main page; the count still covers all of them.
    expect(within(group('OVERDUE')).getAllByTestId('task-card')).toHaveLength(WIDGET_TASKS_PER_GROUP);
    expect(within(group('OVERDUE')).getByText('Ще задач: 1')).toBeInTheDocument();
    expect(within(group('NO_DEADLINE')).getByRole('link', { name: 'Фейкова без дедлайну' })).toBeInTheDocument();
  });

  it('opens a task in the tracker panel', async () => {
    renderHome(user(['dashboard.view', 'tasks.view']));
    await userEvent.click(await screen.findByRole('link', { name: 'Фейкова без дедлайну' }));
    expect(screen.getByTestId('where')).toHaveTextContent('/tasks/n1');
  });

  it('renders nothing and does not call the API without dashboard.view', () => {
    renderHome(user(['tasks.view']));
    expect(screen.queryByTestId('my-tasks-widget')).toBeNull();
    expect(api.getMyDashboard).not.toHaveBeenCalled();
  });

  it('shows only the counters without tasks.view', async () => {
    renderHome(user(['dashboard.view']));
    await waitFor(() => expect(screen.getByTestId('dashboard-value-active')).toHaveTextContent('5'));
    expect(screen.queryByTestId('task-overview')).toBeNull();
    expect(tasksApi.listTasks).not.toHaveBeenCalled();
  });

  it('shows the empty state in en', async () => {
    await changeLanguage('en');
    vi.mocked(api.getMyDashboard).mockResolvedValue({ active: 0, overdue: 0 });
    vi.mocked(tasksApi.listTasks).mockResolvedValue({ items: [], page: 0, totalPages: 0, totalElements: 0 });
    renderHome(user(['dashboard.view', 'tasks.view']));
    expect(await screen.findByRole('heading', { name: 'My tasks' })).toBeInTheDocument();
    expect(await screen.findByText('You have no open tasks.')).toBeInTheDocument();
    expect(screen.getByTestId('dashboard-value-overdue')).toHaveTextContent('0');
    await changeLanguage('uk');
  });
});
