import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MantineProvider } from '@mantine/core';
import { configure, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { CurrentUserProvider, STUB_AUTH_STATE, type AuthState } from '../auth/CurrentUser';
import i18n, { changeLanguage } from '../i18n';
import * as commentsApi from './commentsApi';
import * as memberApi from '../members/memberApi';
import { renderWithProviders } from '../test/render';
import { cssVariablesResolver, theme } from '../theme/theme';
import { openTaskNavigation } from './trackerNav';
import { panelContrastVars } from './panelLogic';
import { TaskPanelRoute } from './TaskPanel';
import { TasksPage } from './TasksPage';
import * as api from './tasksApi';
import {
  canChangeStatus,
  isOpenAndOverdue,
  isSafeHttpUrl,
  isVersionConflict,
  panelVariant,
  taskErrorKey,
  trackerSearch,
} from './taskLogic';

vi.mock('./tasksApi', async (orig) => ({
  ...(await orig<typeof api>()),
  listSpaces: vi.fn(),
  getSpace: vi.fn(),
  listTasks: vi.fn(),
  getTask: vi.fn(),
  changeTaskStatus: vi.fn(),
  listSavedFilters: vi.fn(),
}));
vi.mock('./commentsApi', async (orig) => ({
  ...(await orig<typeof commentsApi>()),
  listComments: vi.fn(),
}));
vi.mock('../members/memberApi', async (orig) => ({ ...(await orig<typeof memberApi>()), listMembers: vi.fn() }));

vi.setConfig({ testTimeout: 30000 });
configure({ asyncUtilTimeout: 8000 });

// ---- viewport emulation (jsdom has no layout; Mantine reads breakpoints through matchMedia) -----------------

const originalMatchMedia = window.matchMedia;

function setViewport(width: number, { reducedMotion = false } = {}) {
  window.matchMedia = ((query: string) => {
    const min = /min-width:\s*([\d.]+)(em|px)/.exec(query);
    let matches = false;
    if (min) matches = width >= Number(min[1]) * (min[2] === 'em' ? 16 : 1);
    else if (query.includes('prefers-reduced-motion')) matches = reducedMotion;
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

// ---- fake data ------------------------------------------------------------------------------------------------

const TODO: api.SpaceStatus = { id: 's1', name: 'Фейкове нове', category: 'TODO', position: 0, archived: false };
const DONE: api.SpaceStatus = { id: 's2', name: 'Фейкове готово', category: 'DONE', position: 1, archived: false };
const SPACE: api.Space = {
  id: 'sp1',
  name: 'Фейковий простір',
  orgUnitId: null,
  general: true,
  archived: false,
  statuses: [TODO, DONE],
  customFields: [],
};

const summary = (over: Partial<api.TaskSummary>): api.TaskSummary => ({
  id: 't1',
  spaceId: 'sp1',
  title: 'Фейкова задача',
  statusId: 's1',
  priority: 'NORMAL',
  deadline: null,
  labels: [],
  attachmentCount: 0,
  authorId: 'creator',
  archived: false,
  createdAt: '2031-01-01T10:00:00Z',
  parentId: null,
  version: 7,
  assigneeIds: [],
  ...over,
});

const LIST_TASK = summary({ id: 't1', title: 'Фейкова задача' });
const OTHER_TASK = summary({ id: 't2', title: 'Друга фейкова задача' });

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

const DETAIL: api.Task = {
  ...LIST_TASK,
  deadline: '2000-01-05',
  description: { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text: 'Фейковий опис' }] }] },
  customFieldValues: {},
  relatedTaskIds: [],
  attachmentIds: [],
  attachments: [],
  issuedAt: '2031-01-01T10:00:00Z',
  creatorId: 'creator',
  assigneeIds: ['u1', 'u2'],
  assignees: [
    { userId: 'u1', assignedBy: 'creator', assignedAt: '2031-01-01T10:00:00Z', active: true },
    { userId: 'u2', assignedBy: 'creator', assignedAt: '2031-01-01T10:00:00Z', active: true },
  ],
  subtasks: [summary({ id: 'sub1', title: 'Фейкова підзадача', parentId: 't1', statusId: 's2' })],
  artifacts: [
    {
      id: 'a1',
      type: 'FILE',
      name: 'фейк.pdf',
      fileId: 'f1',
      fileAvailable: true,
      size: 2048,
      url: null,
      addedBy: 'u1',
      addedAt: '2031-01-02T10:00:00Z',
      archived: false,
    },
    {
      id: 'a2',
      type: 'LINK',
      name: 'Фейкове посилання',
      fileId: null,
      fileAvailable: false,
      size: null,
      url: 'https://example.org/fake',
      addedBy: 'u1',
      addedAt: '2031-01-02T10:00:00Z',
      archived: false,
    },
    {
      id: 'a3',
      type: 'LINK',
      name: 'Прибране посилання',
      fileId: null,
      fileAvailable: false,
      size: null,
      url: 'https://example.org/old',
      addedBy: 'u1',
      addedAt: '2031-01-02T10:00:00Z',
      archived: true,
    },
  ],
  openSubtaskCount: 0,
  suggestClose: true,
};

const authAs = (id: string, permissions: string[] = ['tasks.view']): AuthState => ({
  ...STUB_AUTH_STATE,
  user: { id, displayName: 'Тестовий користувач', isAdmin: false, permissions },
});

function LocationProbe() {
  const location = useLocation();
  const navigate = useNavigate();
  return (
    <>
      <output data-testid="location">{`${location.pathname}${location.search}`}</output>
      <button type="button" data-testid="history-back" onClick={() => navigate(-1)}>
        history back
      </button>
    </>
  );
}

function renderTracker(route: string, auth: AuthState = authAs('u1')) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderWithProviders(
    <QueryClientProvider client={queryClient}>
      <CurrentUserProvider value={auth}>
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

/** Cold load of a deep link whose previous history entry is outside the tracker (e.g. another page or site). */
function renderColdDeepLink(route: string, auth: AuthState = authAs('u1')) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <MantineProvider theme={theme} cssVariablesResolver={cssVariablesResolver}>
      <MemoryRouter initialEntries={['/', route]} initialIndex={1}>
        <QueryClientProvider client={queryClient}>
          <CurrentUserProvider value={auth}>
            <Routes>
              <Route path="/" element={<p>outside the tracker</p>} />
              <Route path="/tasks" element={<TasksPage />}>
                <Route path=":id" element={<TaskPanelRoute />} />
              </Route>
            </Routes>
            <LocationProbe />
          </CurrentUserProvider>
        </QueryClientProvider>
      </MemoryRouter>
    </MantineProvider>,
  );
}

const location = () => screen.getByTestId('location').textContent;
const cardLink = (title: string) => within(screen.getByTestId('task-list')).getByRole('link', { name: title });

beforeEach(async () => {
  vi.clearAllMocks();
  // jsdom does not implement scrollIntoView, which the Mantine combobox calls.
  Element.prototype.scrollIntoView = vi.fn();
  await changeLanguage('uk');
  setViewport(1280);
  vi.mocked(api.listSpaces).mockResolvedValue([SPACE]);
  vi.mocked(api.getSpace).mockResolvedValue(SPACE);
  vi.mocked(api.listTasks).mockResolvedValue({ items: [LIST_TASK, OTHER_TASK], page: 0, totalPages: 1, totalElements: 2 });
  vi.mocked(api.getTask).mockImplementation(async (id) => (id === 't2' ? { ...DETAIL, ...OTHER_TASK } : DETAIL));
  vi.mocked(api.listSavedFilters).mockResolvedValue([]);
  vi.mocked(commentsApi.listComments).mockResolvedValue({ items: [], page: 0, totalPages: 0, totalElements: 0 });
  vi.mocked(memberApi.listMembers).mockResolvedValue([MEMBER('u1', 'Фейкова', 'Олена'), MEMBER('u2', 'Тестовий', 'Іван')]);
});

// ---- pure logic --------------------------------------------------------------------------------------------------

describe('panel logic', () => {
  it('raises secondary, overdue and outline text to AA contrast with theme tokens only', () => {
    expect(panelContrastVars('light')).toEqual({
      '--mantine-color-dimmed': 'var(--mantine-color-gray-7)',
      '--mantine-color-red-text': 'var(--mantine-color-red-8)',
      '--mantine-color-gray-outline': 'var(--mantine-color-gray-7)',
    });
    expect(panelContrastVars('dark')).toEqual({ '--mantine-color-dimmed': 'var(--mantine-color-dark-1)' });
    for (const value of [...Object.values(panelContrastVars('light')), ...Object.values(panelContrastVars('dark'))]) {
      expect(value).toMatch(/^var\(--mantine-color-[a-z]+-\d\)$/);
    }
  });

  it('applies them to the side panel', async () => {
    renderTracker('/tasks/t1');
    await screen.findByRole('region', { name: 'Фейкова задача' });
    expect(screen.getByTestId('task-panel').style.getPropertyValue('--mantine-color-dimmed')).toBe('var(--mantine-color-gray-7)');
  });

  it('picks side panel, overlay or sheet by breakpoint', () => {
    expect(panelVariant(true, true)).toBe('side');
    expect(panelVariant(false, true)).toBe('overlay');
    expect(panelVariant(false, false)).toBe('sheet');
  });

  it('keeps only the tracker params when opening or closing a task', () => {
    expect(trackerSearch('?layout=board&space=sp1&tab=history')).toBe('?layout=board&space=sp1');
    expect(trackerSearch('?tab=comments')).toBe('');
    expect(trackerSearch('?section=issued&tab=comments')).toBe('?section=issued');
    expect(trackerSearch('')).toBe('');
  });

  it('marks only entries pushed on top of the tracker, and keeps the marker when switching tasks', () => {
    expect(openTaskNavigation(null, false)).toEqual({ state: { fromTracker: true }, replace: false });
    expect(openTaskNavigation({ fromTracker: true }, true)).toEqual({ state: { fromTracker: true }, replace: true });
    // Cold deep link: the entry below is not the tracker, so the replaced entry must stay unmarked.
    expect(openTaskNavigation(null, true)).toEqual({ state: undefined, replace: true });
    expect(openTaskNavigation({ other: 1 }, true)).toEqual({ state: undefined, replace: true });
  });

  it('renders only http(s) links', () => {
    expect(isSafeHttpUrl('https://example.org')).toBe(true);
    expect(isSafeHttpUrl('http://example.org')).toBe(true);
    expect(isSafeHttpUrl('javascript:alert(1)')).toBe(false);
    expect(isSafeHttpUrl('ftp://example.org')).toBe(false);
    expect(isSafeHttpUrl('not a url')).toBe(false);
    expect(isSafeHttpUrl(null)).toBe(false);
  });

  it('offers the status selector to creator, author, assignees, tasks.edit.any and Admin only', () => {
    const task = { authorId: 'author', creatorId: 'creator', assigneeIds: ['u1'] };
    const user = (id: string, permissions: string[] = [], isAdmin = false) => ({ id, displayName: 'x', isAdmin, permissions });
    expect(canChangeStatus(user('creator'), task)).toBe(true);
    expect(canChangeStatus(user('author'), task)).toBe(true);
    expect(canChangeStatus(user('u1'), task)).toBe(true);
    expect(canChangeStatus(user('other', ['tasks.edit.any']), task)).toBe(true);
    expect(canChangeStatus(user('other', [], true), task)).toBe(true);
    expect(canChangeStatus(user('other', ['tasks.view']), task)).toBe(false);
    expect(canChangeStatus(null, task)).toBe(false);
  });

  it('does not flag closed tasks as overdue', () => {
    const t = { deadline: '2031-01-01', archived: false };
    expect(isOpenAndOverdue(t, '2031-01-02', 'TODO')).toBe(true);
    expect(isOpenAndOverdue(t, '2031-01-02', 'DONE')).toBe(false);
    expect(isOpenAndOverdue(t, '2031-01-02', 'CANCELED')).toBe(false);
    expect(isOpenAndOverdue(t, '2031-01-01', 'TODO')).toBe(false);
  });

  it('maps conflict and contributor errors to their own messages in uk and en', () => {
    const conflict = new ApiProblemError(409, 'TASK_VERSION_CONFLICT');
    expect(isVersionConflict(conflict)).toBe(true);
    expect(isVersionConflict(new ApiProblemError(409, 'TASK_ARCHIVED'))).toBe(false);
    expect(taskErrorKey(conflict)).toBe('tasks.errors.TASK_VERSION_CONFLICT');
    expect(taskErrorKey(new ApiProblemError(403, 'TASK_CONTRIBUTE_FORBIDDEN'))).toBe('tasks.errors.TASK_CONTRIBUTE_FORBIDDEN');
    expect(taskErrorKey(new ApiProblemError(403, 'TASK_EDIT_FORBIDDEN'))).toBe('problems.FORBIDDEN');
    for (const key of ['tasks.errors.TASK_VERSION_CONFLICT', 'tasks.errors.TASK_CONTRIBUTE_FORBIDDEN', 'tasks.panel.back']) {
      expect(i18n.getResource('uk', 'translation', key)).toBeTruthy();
      expect(i18n.getResource('en', 'translation', key)).toBeTruthy();
    }
  });
});

// ---- desktop: side panel -----------------------------------------------------------------------------------------

describe('master/detail at >= 1024 px', () => {
  it('opens the side panel in place on /tasks/:id with every field, in one task request', async () => {
    const user = userEvent.setup();
    renderTracker('/tasks?section=all');
    await user.click(await screen.findByRole('link', { name: 'Фейкова задача' }));

    expect(location()).toBe('/tasks/t1?section=all');
    const panel = await screen.findByRole('region', { name: 'Фейкова задача' });
    expect(panel).toHaveAttribute('data-variant', 'side');
    // The list stays on screen next to the panel and marks the open task.
    expect(screen.getByTestId('tracker')).toHaveAttribute('data-panel', 'side');
    expect(cardLink('Фейкова задача')).toHaveAttribute('aria-current', 'true');

    const body = await within(panel).findByTestId('task-panel-body');
    expect(within(body).getByLabelText('Статус')).toBeInTheDocument();
    expect(await within(body).findByText('Фейкова Олена')).toBeInTheDocument();
    expect(within(body).getByText('Тестовий Іван')).toBeInTheDocument();
    expect(within(body).getByTestId('panel-issued')).toHaveTextContent('01.01.2031');
    expect(within(body).getByTestId('panel-deadline')).toHaveTextContent('05.01.2000');
    expect(within(body).getByTestId('panel-overdue')).toHaveTextContent('Прострочено');
    expect(within(body).getByText('Фейковий опис')).toBeInTheDocument();
    // Artifacts: active ones only; files and links open in a new tab.
    expect(within(body).getAllByTestId('panel-artifact')).toHaveLength(2);
    expect(within(body).getByRole('link', { name: 'Фейкове посилання' })).toHaveAttribute('href', 'https://example.org/fake');
    expect(within(body).getByRole('link', { name: 'фейк.pdf' })).toHaveAttribute('target', '_blank');
    expect(within(body).queryByText('Прибране посилання')).toBeNull();
    // Subtasks come from the `subtasks` array of the same response.
    expect(within(body).getByTestId('panel-subtask')).toHaveTextContent('Фейкова підзадача');
    expect(within(body).getByRole('tab', { name: 'Коментарі' })).toBeInTheDocument();

    expect(api.getTask).toHaveBeenCalledTimes(1);
    expect(api.getTask).toHaveBeenCalledWith('t1');
    // Comments are a separate, later request.
    await waitFor(() => expect(commentsApi.listComments).toHaveBeenCalled());
  });

  it('moves focus to the panel heading and back to the card link on close', async () => {
    const user = userEvent.setup();
    renderTracker('/tasks?section=all');
    await user.click(await screen.findByRole('link', { name: 'Фейкова задача' }));
    const panel = await screen.findByRole('region', { name: 'Фейкова задача' });
    await waitFor(() => expect(within(panel).getByRole('heading', { level: 2, name: 'Фейкова задача' })).toHaveFocus());

    await user.click(within(panel).getByRole('button', { name: 'Закрити панель задачі' }));
    await waitFor(() => expect(screen.queryByTestId('task-panel')).toBeNull());
    expect(location()).toBe('/tasks?section=all');
    await waitFor(() => expect(cardLink('Фейкова задача')).toHaveFocus());
  });

  it('switches tasks in place and the browser back closes the panel instead of walking through tasks', async () => {
    const user = userEvent.setup();
    renderTracker('/tasks?section=all');
    await user.click(await screen.findByRole('link', { name: 'Фейкова задача' }));
    await screen.findByRole('region', { name: 'Фейкова задача' });
    await user.click(cardLink('Друга фейкова задача'));
    await screen.findByRole('region', { name: 'Друга фейкова задача' });
    expect(location()).toBe('/tasks/t2?section=all');

    await user.click(screen.getByTestId('history-back'));
    await waitFor(() => expect(screen.queryByTestId('task-panel')).toBeNull());
    expect(location()).toBe('/tasks?section=all');
  });

  it('opens a deep link on a cold load and closes to the plain tracker', async () => {
    const user = userEvent.setup();
    renderTracker('/tasks/t1?tab=comments');
    const panel = await screen.findByRole('region', { name: 'Фейкова задача' });
    expect(await screen.findByRole('link', { name: 'Друга фейкова задача' })).toBeInTheDocument();
    await user.click(within(panel).getByRole('button', { name: 'Закрити панель задачі' }));
    await waitFor(() => expect(location()).toBe('/tasks'));
  });

  it('after a cold deep link, switching tasks and closing returns to the tracker, never out of it', async () => {
    const user = userEvent.setup();
    renderColdDeepLink('/tasks/t1?section=all');
    await screen.findByRole('region', { name: 'Фейкова задача' });
    await user.click(await screen.findByRole('link', { name: 'Друга фейкова задача' }));
    const panel = await screen.findByRole('region', { name: 'Друга фейкова задача' });
    expect(location()).toBe('/tasks/t2?section=all');

    await user.click(within(panel).getByRole('button', { name: 'Закрити панель задачі' }));
    await waitFor(() => expect(screen.queryByTestId('task-panel')).toBeNull());
    expect(location()).toBe('/tasks?section=all');
    expect(screen.queryByText('outside the tracker')).toBeNull();
  });

  it('after a cold deep link, opening a subtask and closing returns to the tracker', async () => {
    const user = userEvent.setup();
    renderColdDeepLink('/tasks/t1');
    const panel = await screen.findByRole('region', { name: 'Фейкова задача' });
    await user.click(await within(panel).findByRole('link', { name: 'Фейкова підзадача' }));
    expect(location()).toBe('/tasks/sub1');
    const close = await screen.findAllByRole('button', { name: 'Закрити панель задачі' });
    await user.click(close[0]);
    await waitFor(() => expect(location()).toBe('/tasks'));
    expect(screen.queryByText('outside the tracker')).toBeNull();
  });

  it('opens the same panel from a kanban card', async () => {
    const user = userEvent.setup();
    renderTracker('/tasks?layout=board&space=sp1');
    const columns = await screen.findAllByTestId('board-column');
    await user.click(within(columns[0]).getByRole('link', { name: 'Фейкова задача' }));
    expect(location()).toBe('/tasks/t1?layout=board&space=sp1');
    const panel = await screen.findByRole('region', { name: 'Фейкова задача' });
    expect(await within(panel).findByTestId('task-panel-body')).toBeInTheDocument();
    // The board stays (the view lives in the URL).
    expect(screen.getAllByTestId('board-column').length).toBeGreaterThan(0);
  });

  it('sends the task version on a status change and shows a reload hint on 409', async () => {
    vi.mocked(api.changeTaskStatus).mockRejectedValue(new ApiProblemError(409, 'TASK_VERSION_CONFLICT'));
    const user = userEvent.setup();
    renderTracker('/tasks/t1');
    const panel = await screen.findByRole('region', { name: 'Фейкова задача' });
    await user.click(await within(panel).findByLabelText('Статус'));
    await user.click(await screen.findByRole('option', { name: 'Фейкове готово', hidden: true }));
    await waitFor(() => expect(api.changeTaskStatus).toHaveBeenCalledWith('t1', 's2', 7));

    const hint = await screen.findByTestId('version-conflict');
    expect(hint).toHaveTextContent('Цю задачу щойно змінив хтось інший');
    const calls = vi.mocked(api.getTask).mock.calls.length;
    await user.click(within(hint).getByRole('button', { name: 'Перезавантажити задачу' }));
    await waitFor(() => expect(vi.mocked(api.getTask).mock.calls.length).toBeGreaterThan(calls));
    await waitFor(() => expect(screen.queryByTestId('version-conflict')).toBeNull());
  });

  it('shows the contributor rule when the status change is forbidden', async () => {
    vi.mocked(api.changeTaskStatus).mockRejectedValue(new ApiProblemError(403, 'TASK_CONTRIBUTE_FORBIDDEN'));
    const user = userEvent.setup();
    renderTracker('/tasks/t1');
    const panel = await screen.findByRole('region', { name: 'Фейкова задача' });
    await user.click(await within(panel).findByLabelText('Статус'));
    await user.click(await screen.findByRole('option', { name: 'Фейкове готово', hidden: true }));
    expect(await screen.findByText(/Змінювати статус можуть автор задачі/)).toBeInTheDocument();
  });

  it('shows the status read-only to users who may not change it', async () => {
    renderTracker('/tasks/t1', authAs('outsider'));
    const panel = await screen.findByRole('region', { name: 'Фейкова задача' });
    expect(await within(panel).findByTestId('panel-status')).toHaveTextContent('Фейкове нове');
    expect(within(panel).queryByLabelText('Статус')).toBeNull();
  });
});

// ---- tablet and phone ----------------------------------------------------------------------------------------

describe('panel presentation below 1024 px', () => {
  it('overlays the list on a tablet', async () => {
    setViewport(800);
    renderTracker('/tasks/t1');
    const dialog = await screen.findByRole('dialog');
    expect(screen.getByTestId('task-panel')).toHaveAttribute('data-variant', 'overlay');
    expect(within(dialog).getByRole('button', { name: 'Закрити панель задачі' })).toBeInTheDocument();
    expect(screen.getByTestId('tracker')).not.toHaveAttribute('data-panel');
  });

  it('opens a full-screen sheet at 375 px with a back control, a focus trap and focus return', async () => {
    setViewport(375);
    const user = userEvent.setup();
    renderTracker('/tasks?section=all');
    await user.click(await screen.findByRole('link', { name: 'Фейкова задача' }));

    const dialog = await screen.findByRole('dialog');
    expect(screen.getByTestId('task-panel')).toHaveAttribute('data-variant', 'sheet');
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    const back = within(dialog).getByRole('button', { name: '← Назад' });
    await waitFor(() => expect(dialog.contains(document.activeElement)).toBe(true));
    expect(await within(dialog).findByTestId('task-panel-body')).toBeInTheDocument();

    await user.click(back);
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(location()).toBe('/tasks?section=all');
    await waitFor(() => expect(cardLink('Фейкова задача')).toHaveFocus());
  });

  it('renders picker dropdowns inside the sheet, not in a portal (keeps its scroll position)', async () => {
    setViewport(375);
    const user = userEvent.setup();
    renderTracker('/tasks/t1');
    const dialog = await screen.findByRole('dialog');
    await user.click((await within(dialog).findAllByLabelText('Статус')).find((el) => el.tagName === 'INPUT') as HTMLElement);
    const option = await screen.findByRole('option', { name: 'Фейкове готово', hidden: true });
    expect(dialog.contains(option)).toBe(true);
  });

  it('keeps picker dropdowns of the desktop side panel in a portal', async () => {
    const user = userEvent.setup();
    renderTracker('/tasks/t1');
    const panel = await screen.findByRole('region', { name: 'Фейкова задача' });
    await user.click(await within(panel).findByLabelText('Статус'));
    const option = await screen.findByRole('option', { name: 'Фейкове готово', hidden: true });
    expect(panel.contains(option)).toBe(false);
  });

  it('closes the sheet with Escape', async () => {
    setViewport(375);
    const user = userEvent.setup();
    renderTracker('/tasks?section=all');
    await user.click(await screen.findByRole('link', { name: 'Фейкова задача' }));
    const dialog = await screen.findByRole('dialog');
    await waitFor(() => expect(dialog.contains(document.activeElement)).toBe(true));
    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(location()).toBe('/tasks?section=all');
  });

  /** Inline transition durations Mantine writes on the drawer while it animates. */
  const panelTransitionDurations = () =>
    Array.from(document.querySelectorAll<HTMLElement>('[style*="transition"]'))
      .map((el) => el.style.transitionDuration)
      .filter((d) => d !== '' && d !== '0ms' && d !== '0s');

  it('slides the sheet in by default', async () => {
    setViewport(375);
    renderTracker('/tasks/t1');
    await screen.findByRole('dialog');
    expect(panelTransitionDurations()).toContain('200ms');
  });

  it('opens without motion under prefers-reduced-motion', async () => {
    setViewport(375, { reducedMotion: true });
    renderTracker('/tasks/t1');
    await screen.findByRole('dialog');
    expect(screen.getByTestId('task-panel')).toBeInTheDocument();
    expect(panelTransitionDurations()).toEqual([]);
  });
});
