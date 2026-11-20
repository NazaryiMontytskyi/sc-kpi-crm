import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { configure, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CurrentUserProvider, STUB_AUTH_STATE, type AuthState } from '../auth/CurrentUser';
import i18n, { changeLanguage } from '../i18n';
import * as memberApi from '../members/memberApi';
import { renderWithProviders } from '../test/render';
import * as commentsApi from './commentsApi';
import { MyTasksOverview } from './MyTasksOverview';
import { TaskPanelRoute } from './TaskPanel';
import { TasksPage } from './TasksPage';
import * as api from './tasksApi';
import {
  addDays,
  deadlineGroup,
  endOfWeek,
  groupTasksByDeadline,
  todayKyiv,
  trackerSection,
  trackerSearch,
} from './taskLogic';

vi.mock('./tasksApi', async (orig) => ({
  ...(await orig<typeof api>()),
  listSpaces: vi.fn(),
  getSpace: vi.fn(),
  listTasks: vi.fn(),
  getTask: vi.fn(),
  listSavedFilters: vi.fn(),
}));
vi.mock('./commentsApi', async (orig) => ({ ...(await orig<typeof commentsApi>()), listComments: vi.fn() }));
vi.mock('../members/memberApi', async (orig) => ({ ...(await orig<typeof memberApi>()), listMembers: vi.fn() }));

vi.setConfig({ testTimeout: 30000 });
configure({ asyncUtilTimeout: 8000 });

// ---- fake data ------------------------------------------------------------------------------------------------

const TODO: api.SpaceStatus = { id: 's1', name: 'Фейкове нове', category: 'TODO', position: 0, archived: false };
const DONE: api.SpaceStatus = { id: 's2', name: 'Фейкове готово', category: 'DONE', position: 1, archived: false };
const CANCELED: api.SpaceStatus = { id: 's3', name: 'Фейкове скасовано', category: 'CANCELED', position: 2, archived: false };
const SPACE: api.Space = {
  id: 'sp1',
  name: 'Фейковий простір',
  orgUnitId: null,
  general: true,
  archived: false,
  statuses: [TODO, DONE, CANCELED],
  customFields: [],
};

const task = (over: Partial<api.TaskSummary>): api.TaskSummary => ({
  id: 't1',
  spaceId: 'sp1',
  title: 'Фейкова задача',
  statusId: 's1',
  priority: 'NORMAL',
  deadline: null,
  labels: [],
  attachmentCount: 0,
  authorId: 'me',
  archived: false,
  createdAt: '2031-01-01T10:00:00Z',
  parentId: null,
  version: 1,
  assigneeIds: ['me'],
  ...over,
});

// Deadlines relative to the real "today" in Kyiv, so the component (which uses the clock) groups them predictably.
const TODAY = todayKyiv();
const OVERDUE_TASK = task({ id: 'o1', title: 'Фейкова прострочена', deadline: addDays(TODAY, -2) });
const WEEK_TASK = task({ id: 'w1', title: 'Фейкова на цей тиждень', deadline: TODAY });
const LATER_TASK = task({ id: 'l1', title: 'Фейкова пізніше', deadline: addDays(endOfWeek(TODAY), 1) });
const NONE_TASK = task({ id: 'n1', title: 'Фейкова без дедлайну' });

const MEMBER = (id: string, lastName: string, firstName: string): memberApi.MemberSummary => ({
  id,
  lastName,
  firstName,
  middleName: '',
  faculty: null,
  studyGroup: '',
  telegram: '',
  joinedAt: '2030-09-01',
  avatarFileId: null,
  archived: false,
});

const DETAIL = (summary: api.TaskSummary): api.Task => ({
  ...summary,
  description: null,
  customFieldValues: {},
  relatedTaskIds: [],
  attachmentIds: [],
  attachments: [],
  suggestDone: false,
  issuedAt: '2031-01-01T10:00:00Z',
  creatorId: 'me',
  assignees: summary.assigneeIds.map((userId) => ({ userId, assignedBy: 'me', assignedAt: '2031-01-01T10:00:00Z', active: true })),
  subtasks: [],
  artifacts: [],
  openSubtaskCount: 0,
  suggestClose: false,
});

const page = (items: api.TaskSummary[]): api.Paged<api.TaskSummary> => ({ items, page: 0, totalPages: 1, totalElements: items.length });

const ME: AuthState = {
  ...STUB_AUTH_STATE,
  user: { id: 'me', displayName: 'Тестовий користувач', isAdmin: false, permissions: ['tasks.view'] },
};

// ---- viewport emulation (Mantine reads breakpoints through matchMedia) --------------------------------------

const originalMatchMedia = window.matchMedia;

function setViewport(width: number) {
  window.matchMedia = ((query: string) => {
    const min = /min-width:\s*([\d.]+)(em|px)/.exec(query);
    const matches = min ? width >= Number(min[1]) * (min[2] === 'em' ? 16 : 1) : false;
    return {
      matches,
      media: query,
      onchange: null,
      addEventListener: () => {},
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
      dispatchEvent: () => false,
    };
  }) as typeof window.matchMedia;
}

afterEach(() => {
  window.matchMedia = originalMatchMedia;
});

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{`${location.pathname}${location.search}`}</output>;
}

function renderTracker(route: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderWithProviders(
    <QueryClientProvider client={queryClient}>
      <CurrentUserProvider value={ME}>
        <Routes>
          <Route path="/tasks" element={<TasksPage />}>
            <Route path=":id" element={<TaskPanelRoute />} />
          </Route>
        </Routes>
        <LocationProbe />
      </CurrentUserProvider>
    </QueryClientProvider>,
    route,
  );
}

const location = () => screen.getByTestId('location').textContent;
const group = (key: string) => screen.getAllByTestId('overview-group').find((g) => g.dataset.group === key)!;
const viewsRequested = () => vi.mocked(api.listTasks).mock.calls.map(([q]) => q.view);

beforeEach(async () => {
  vi.clearAllMocks();
  await changeLanguage('uk');
  setViewport(1280);
  vi.mocked(api.listSpaces).mockResolvedValue([SPACE]);
  vi.mocked(api.getSpace).mockResolvedValue(SPACE);
  vi.mocked(api.listTasks).mockImplementation(async (q) => {
    if (q.view === 'MY_TASKS') return page([NONE_TASK, LATER_TASK, WEEK_TASK, OVERDUE_TASK]);
    if (q.view === 'ISSUED_BY_ME') {
      return page([
        task({ id: 'i1', title: 'Фейкова видана', deadline: addDays(TODAY, -1), assigneeIds: ['u1', 'u2'] }),
        task({ id: 'i2', title: 'Фейкова виконана', deadline: addDays(TODAY, -3), statusId: 's2', assigneeIds: ['u1'] }),
      ]);
    }
    return page([OVERDUE_TASK]);
  });
  vi.mocked(api.getTask).mockImplementation(async (id) => DETAIL([OVERDUE_TASK, WEEK_TASK, LATER_TASK, NONE_TASK].find((t) => t.id === id) ?? NONE_TASK));
  vi.mocked(api.listSavedFilters).mockResolvedValue([]);
  vi.mocked(commentsApi.listComments).mockResolvedValue({ items: [], page: 0, totalPages: 0, totalElements: 0 });
  vi.mocked(memberApi.listMembers).mockResolvedValue([MEMBER('u1', 'Фейкова', 'Олена'), MEMBER('u2', 'Тестовий', 'Іван')]);
});

// ---- pure grouping (spec UI-4) ----------------------------------------------------------------------------------

describe('overview grouping', () => {
  it('ends the week on Sunday (Monday-first weeks)', () => {
    expect(endOfWeek('2031-01-13')).toBe('2031-01-19'); // Monday
    expect(endOfWeek('2031-01-15')).toBe('2031-01-19'); // Wednesday
    expect(endOfWeek('2031-01-19')).toBe('2031-01-19'); // Sunday
    expect(endOfWeek('2031-12-29')).toBe('2032-01-04'); // across the year boundary
    expect(addDays('2031-02-28', 1)).toBe('2031-03-01');
    expect(addDays('2031-03-01', -1)).toBe('2031-02-28');
  });

  it('assigns a deadline to Overdue, This week, Later or No deadline relative to today', () => {
    const today = '2031-01-15'; // Wednesday
    expect(deadlineGroup('2031-01-14', today)).toBe('OVERDUE');
    expect(deadlineGroup('2031-01-15', today)).toBe('THIS_WEEK'); // due at the end of today: not overdue yet
    expect(deadlineGroup('2031-01-19', today)).toBe('THIS_WEEK');
    expect(deadlineGroup('2031-01-20', today)).toBe('LATER');
    expect(deadlineGroup(null, today)).toBe('NO_DEADLINE');
  });

  it('uses Europe/Kyiv day boundaries, not UTC', () => {
    const t = [task({ id: 'a', deadline: '2031-01-12' }), task({ id: 'b', deadline: '2031-01-13' })];
    // 21:30 UTC on Sunday 12 Jan is still Sunday in Kyiv (UTC+2): the 12th is due today, the 13th is next week.
    const sunday = groupTasksByDeadline(t, new Date('2031-01-12T21:30:00Z'));
    expect(sunday.groups.THIS_WEEK.map((x) => x.id)).toEqual(['a']);
    expect(sunday.groups.LATER.map((x) => x.id)).toEqual(['b']);
    // 22:30 UTC is already Monday 13 Jan in Kyiv: the 12th is overdue, the 13th is this week.
    const monday = groupTasksByDeadline(t, new Date('2031-01-12T22:30:00Z'));
    expect(monday.groups.OVERDUE.map((x) => x.id)).toEqual(['a']);
    expect(monday.groups.THIS_WEEK.map((x) => x.id)).toEqual(['b']);
    // Summer time (UTC+3): 21:30 UTC on 30 Mar is 31 Mar in Kyiv.
    expect(todayKyiv(new Date('2031-03-30T21:30:00Z'))).toBe('2031-03-31');
  });

  it('counts per group, sorts soonest first and leaves out archived and closed tasks', () => {
    const now = new Date('2031-01-15T10:00:00Z'); // Wednesday in Kyiv
    const tasks = [
      task({ id: 'late2', title: 'Б', deadline: '2031-02-10' }),
      task({ id: 'late1', title: 'А', deadline: '2031-02-01' }),
      task({ id: 'od', deadline: '2031-01-10' }),
      task({ id: 'wk', deadline: '2031-01-18' }),
      task({ id: 'old', createdAt: '2031-01-01T00:00:00Z' }),
      task({ id: 'new', createdAt: '2031-01-05T00:00:00Z' }),
      task({ id: 'arch', deadline: '2031-01-01', archived: true }),
      task({ id: 'done', deadline: '2031-01-01', statusId: 's2' }),
    ];
    const r = groupTasksByDeadline(tasks, now, (x) => x.statusId === 's2');
    expect(r.groups.OVERDUE.map((x) => x.id)).toEqual(['od']);
    expect(r.groups.THIS_WEEK.map((x) => x.id)).toEqual(['wk']);
    expect(r.groups.LATER.map((x) => x.id)).toEqual(['late1', 'late2']);
    expect(r.groups.NO_DEADLINE.map((x) => x.id)).toEqual(['new', 'old']);
    expect(r.closed.map((x) => x.id)).toEqual(['done']);
    expect(r.openCount).toBe(6);
  });

  it('keeps the section in tracker URLs and defaults to My tasks', () => {
    expect(trackerSection(null)).toBe('my');
    expect(trackerSection('issued')).toBe('issued');
    expect(trackerSection('all')).toBe('all');
    expect(trackerSection('bogus')).toBe('my');
    expect(trackerSearch('?section=issued&tab=comments')).toBe('?section=issued');
  });

  it('has the overview strings in uk and en', () => {
    for (const key of [
      'tasks.sections.my',
      'tasks.sections.issued',
      'tasks.sections.all',
      'tasks.overview.groups.OVERDUE',
      'tasks.overview.groups.THIS_WEEK',
      'tasks.overview.groups.LATER',
      'tasks.overview.groups.NO_DEADLINE',
      'tasks.overview.groups.CLOSED',
      'tasks.overview.emptyMine',
      'tasks.overview.emptyIssued',
    ]) {
      expect(i18n.getResource('uk', 'translation', key)).toBeTruthy();
      expect(i18n.getResource('en', 'translation', key)).toBeTruthy();
    }
  });
});

// ---- tracker sections ----------------------------------------------------------------------------------------

describe('"My tasks" as the default section of /tasks', () => {
  it('groups my open tasks with a count per group', async () => {
    renderTracker('/tasks');
    expect(await screen.findByTestId('task-overview')).toHaveAttribute('data-view', 'MY_TASKS');
    expect(screen.getByRole('tab', { name: 'Мої завдання' })).toHaveAttribute('aria-selected', 'true');
    expect(viewsRequested()).toEqual(['MY_TASKS']);
    expect(vi.mocked(api.listTasks).mock.calls[0][0]).toMatchObject({ sortBy: 'deadline', direction: 'asc', includeArchived: false });

    expect(screen.getAllByTestId('overview-group').map((g) => g.dataset.group)).toEqual(['OVERDUE', 'THIS_WEEK', 'LATER', 'NO_DEADLINE']);
    const expectations: [string, string, string][] = [
      ['OVERDUE', 'Прострочені', 'Фейкова прострочена'],
      ['THIS_WEEK', 'Цього тижня', 'Фейкова на цей тиждень'],
      ['LATER', 'Пізніше', 'Фейкова пізніше'],
      ['NO_DEADLINE', 'Без дедлайну', 'Фейкова без дедлайну'],
    ];
    for (const [key, heading, title] of expectations) {
      const g = group(key);
      expect(within(g).getByRole('heading', { name: heading })).toBeInTheDocument();
      expect(within(g).getByTestId('overview-count')).toHaveTextContent('1');
      expect(within(g).getByRole('link', { name: title })).toBeInTheDocument();
    }
    // Every group is a labelled list for assistive technology.
    expect(within(group('OVERDUE')).getByRole('list', { name: 'Прострочені' })).toBeInTheDocument();
  });

  it('opens the selected task in the panel and keeps the overview next to it', async () => {
    const user = userEvent.setup();
    renderTracker('/tasks');
    await user.click(await within(await screen.findByTestId('task-overview')).findByRole('link', { name: 'Фейкова прострочена' }));
    expect(location()).toBe('/tasks/o1');
    const panel = await screen.findByRole('region', { name: 'Фейкова прострочена' });
    expect(await within(panel).findByTestId('task-panel-body')).toBeInTheDocument();
    expect(within(group('OVERDUE')).getByRole('link', { name: 'Фейкова прострочена' })).toHaveAttribute('aria-current', 'true');

    await user.click(within(panel).getByRole('button', { name: 'Закрити панель задачі' }));
    await waitFor(() => expect(screen.queryByTestId('task-panel')).toBeNull());
    expect(location()).toBe('/tasks');
    await waitFor(() => expect(within(group('OVERDUE')).getByRole('link', { name: 'Фейкова прострочена' })).toHaveFocus());
  });

  it('opens the full-screen sheet at 375 px', async () => {
    setViewport(375);
    const user = userEvent.setup();
    renderTracker('/tasks');
    await user.click(await within(await screen.findByTestId('task-overview')).findByRole('link', { name: 'Фейкова пізніше' }));
    await screen.findByRole('dialog');
    expect(screen.getByTestId('task-panel')).toHaveAttribute('data-variant', 'sheet');
    expect(location()).toBe('/tasks/l1');
  });

  it('shows "Issued by me" with assignees and a closed group, and keeps the section in task links', async () => {
    const user = userEvent.setup();
    renderTracker('/tasks');
    await screen.findByTestId('task-overview');
    await user.click(screen.getByRole('tab', { name: 'Видані мною' }));
    await waitFor(() => expect(location()).toBe('/tasks?section=issued'));
    await waitFor(() => expect(screen.getByTestId('task-overview')).toHaveAttribute('data-view', 'ISSUED_BY_ME'));
    expect(viewsRequested()).toContain('ISSUED_BY_ME');

    // A done task is never overdue; it sits in its own group once the space statuses are known.
    await waitFor(() => expect(within(group('CLOSED')).getByRole('link', { name: 'Фейкова виконана' })).toBeInTheDocument());
    expect(within(group('CLOSED')).getByTestId('overview-count')).toHaveTextContent('1');
    const overdue = group('OVERDUE');
    expect(within(overdue).getByTestId('overview-count')).toHaveTextContent('1');
    expect(within(overdue).getByRole('link', { name: 'Фейкова видана' })).toBeInTheDocument();
    expect(await within(overdue).findByText('Виконавці: Фейкова Олена, Тестовий Іван')).toBeInTheDocument();

    expect(within(overdue).getByRole('link', { name: 'Фейкова видана' })).toHaveAttribute('href', '/tasks/i1?section=issued');
  });

  it('keeps the full list with filters under "All tasks"', async () => {
    const user = userEvent.setup();
    renderTracker('/tasks');
    await screen.findByTestId('task-overview');
    await user.click(screen.getByRole('tab', { name: 'Усі задачі' }));
    await waitFor(() => expect(location()).toBe('/tasks?section=all'));
    expect(await within(await screen.findByTestId('task-list')).findByRole('link', { name: 'Фейкова прострочена' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Фільтри' })).toBeInTheDocument();
    await user.click(screen.getByRole('tab', { name: 'Мої завдання' }));
    await waitFor(() => expect(location()).toBe('/tasks'));
  });

  it('says so when there is nothing to show', async () => {
    vi.mocked(api.listTasks).mockResolvedValue(page([]));
    renderTracker('/tasks?section=issued');
    expect(await screen.findByTestId('overview-empty')).toHaveTextContent('Ви ще не видали задач іншим.');
  });

  it('renders in English', async () => {
    await changeLanguage('en');
    renderTracker('/tasks');
    await screen.findByTestId('task-overview');
    expect(screen.getByRole('tab', { name: 'My tasks' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Issued by me' })).toBeInTheDocument();
    for (const heading of ['Overdue', 'This week', 'Later', 'No deadline']) {
      expect(screen.getByRole('heading', { name: heading })).toBeInTheDocument();
    }
  });
});

describe('MyTasksOverview for the main-page widget', () => {
  it('limits the tasks per group but counts them all', async () => {
    vi.mocked(api.listTasks).mockResolvedValue(page([OVERDUE_TASK, task({ id: 'o2', title: 'Друга прострочена', deadline: addDays(TODAY, -1) })]));
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    renderWithProviders(
      <QueryClientProvider client={queryClient}>
        <CurrentUserProvider value={ME}>
          <MyTasksOverview limitPerGroup={1} headingOrder={3} />
        </CurrentUserProvider>
      </QueryClientProvider>,
      '/',
    );
    const overdue = await screen.findByRole('heading', { level: 3, name: 'Прострочені' });
    const section = overdue.closest('section')!;
    expect(within(section).getByTestId('overview-count')).toHaveTextContent('2');
    expect(within(section).getAllByTestId('task-card')).toHaveLength(1);
    expect(within(section).getByText('Ще задач: 1')).toBeInTheDocument();
    expect(within(section).getByRole('link', { name: 'Фейкова прострочена' })).toHaveAttribute('href', '/tasks/o1');
  });

  it('reports a truncated list beyond the page cap', async () => {
    vi.mocked(api.listTasks).mockImplementation(async (q) => ({
      items: [task({ id: `p${q.page}`, title: `Фейкова ${q.page}` })],
      page: q.page,
      totalPages: 99,
      totalElements: 99,
    }));
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    renderWithProviders(
      <QueryClientProvider client={queryClient}>
        <CurrentUserProvider value={ME}>
          <MyTasksOverview />
        </CurrentUserProvider>
      </QueryClientProvider>,
      '/',
    );
    expect(await screen.findByText('Показано перші 5 задач.')).toBeInTheDocument();
    expect(api.listTasks).toHaveBeenCalledTimes(5);
  });
});
