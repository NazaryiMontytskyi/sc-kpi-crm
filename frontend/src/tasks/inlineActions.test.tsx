import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { configure, fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { CurrentUserProvider, STUB_AUTH_STATE, type AuthState, type CurrentUser } from '../auth/CurrentUser';
import i18n, { changeLanguage } from '../i18n';
import * as memberApi from '../members/memberApi';
import { renderWithProviders } from '../test/render';
import * as attachmentsApi from './attachmentsApi';
import * as commentsApi from './commentsApi';
import {
  artifactErrorKey,
  canAddSubtask,
  canAssignOthers,
  canManageArtifacts,
  canManageAssignee,
  deadlineConflictsOf,
  linkUrlError,
  needsOpenSubtaskWarning,
  showSuggestClose,
  subtaskDeadlineError,
  subtaskRequest,
  subtasksBlockingDeadline,
  subtaskToggleTarget,
  validateSubtaskDraft,
} from './panelLogic';
import { TaskPanelRoute } from './TaskPanel';
import { taskErrorKey } from './taskLogic';
import * as api from './tasksApi';

vi.mock('./tasksApi', async (orig) => ({
  ...(await orig<typeof api>()),
  listSpaces: vi.fn(),
  getSpace: vi.fn(),
  getTask: vi.fn(),
  changeTaskStatus: vi.fn(),
  updateTask: vi.fn(),
  createSubtask: vi.fn(),
  addAssignee: vi.fn(),
  removeAssignee: vi.fn(),
  addArtifact: vi.fn(),
  removeArtifact: vi.fn(),
}));
vi.mock('./commentsApi', async (orig) => ({ ...(await orig<typeof commentsApi>()), listComments: vi.fn() }));
vi.mock('./attachmentsApi', async (orig) => ({ ...(await orig<typeof attachmentsApi>()), uploadAttachment: vi.fn() }));
vi.mock('../members/memberApi', async (orig) => ({ ...(await orig<typeof memberApi>()), listMembers: vi.fn() }));

vi.setConfig({ testTimeout: 30000 });
configure({ asyncUtilTimeout: 8000 });

// ---- desktop viewport (side panel) -----------------------------------------------------------------------------

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

// ---- fake data ---------------------------------------------------------------------------------------------------

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

const LINK: api.TaskArtifact = {
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
};

const SUBTASK = summary({ id: 'sub1', title: 'Фейкова підзадача', parentId: 't1', statusId: 's2', version: 3, assigneeIds: ['u1'] });

const DETAIL: api.Task = {
  ...summary({}),
  deadline: '2031-03-10',
  description: null,
  customFieldValues: {},
  relatedTaskIds: ['rel1'],
  attachmentIds: ['old1'],
  attachments: [],
  suggestDone: false,
  issuedAt: '2031-01-01T10:00:00Z',
  creatorId: 'creator',
  assigneeIds: ['u1', 'u2'],
  assignees: [
    { userId: 'u1', assignedBy: 'creator', assignedAt: '2031-01-01T10:00:00Z', active: true },
    { userId: 'u2', assignedBy: 'creator', assignedAt: '2031-01-01T10:00:00Z', active: true },
  ],
  subtasks: [SUBTASK],
  artifacts: [LINK],
  openSubtaskCount: 0,
  suggestClose: false,
};

const user = (id: string, permissions: string[] = ['tasks.view'], isAdmin = false): CurrentUser => ({
  id,
  displayName: 'Тестовий користувач',
  isAdmin,
  permissions,
});
const authAs = (u: CurrentUser): AuthState => ({ ...STUB_AUTH_STATE, user: u });

/** Assignee u1 with tasks.create only: may contribute and add subtasks for self, may not assign others. */
const ASSIGNEE = user('u1', ['tasks.view', 'tasks.create']);
/** A coordinator holding tasks.assign. */
const COORDINATOR = user('coord', ['tasks.view', 'tasks.create', 'tasks.assign']);
/** The creator of the task (edits its fields, e.g. the deadline). */
const CREATOR = user('creator', ['tasks.view', 'tasks.create']);
/** A viewer without any rights on the task. */
const OUTSIDER = user('outsider', ['tasks.view']);

function renderPanel(task: api.Task, who: CurrentUser) {
  vi.mocked(api.getTask).mockResolvedValue(task);
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderWithProviders(
    <QueryClientProvider client={queryClient}>
      <CurrentUserProvider value={authAs(who)}>
        <Routes>
          <Route path="/tasks/:id" element={<TaskPanelRoute />} />
        </Routes>
      </CurrentUserProvider>
    </QueryClientProvider>,
    `/tasks/${task.id}`,
  );
}

/** Opens a Mantine Select and picks an option from its own listbox (several selects may be in the DOM). */
async function pick(u: ReturnType<typeof userEvent.setup>, input: HTMLElement, option: string) {
  await u.click(input);
  await waitFor(() => expect(input.getAttribute('aria-controls')).toBeTruthy());
  const list = document.getElementById(input.getAttribute('aria-controls') ?? '') as HTMLElement;
  await u.click(await within(list).findByRole('option', { name: option, hidden: true }));
}

async function body() {
  const panel = await screen.findByRole('region', { name: 'Фейкова задача' });
  return within(panel).findByTestId('task-panel-body');
}

beforeEach(async () => {
  vi.clearAllMocks();
  Element.prototype.scrollIntoView = vi.fn();
  await changeLanguage('uk');
  setViewport(1280);
  vi.mocked(api.listSpaces).mockResolvedValue([SPACE]);
  vi.mocked(api.getSpace).mockResolvedValue(SPACE);
  vi.mocked(commentsApi.listComments).mockResolvedValue({ items: [], page: 0, totalPages: 0, totalElements: 0 });
  vi.mocked(memberApi.listMembers).mockResolvedValue([
    MEMBER('u1', 'Фейкова', 'Олена'),
    MEMBER('u2', 'Тестовий', 'Іван'),
    MEMBER('u3', 'Вигаданий', 'Петро'),
  ]);
  vi.mocked(api.changeTaskStatus).mockResolvedValue(DETAIL);
  vi.mocked(api.updateTask).mockResolvedValue(DETAIL);
  vi.mocked(api.createSubtask).mockResolvedValue(DETAIL);
  vi.mocked(api.addAssignee).mockResolvedValue();
  vi.mocked(api.removeAssignee).mockResolvedValue();
  vi.mocked(api.addArtifact).mockResolvedValue(LINK);
  vi.mocked(api.removeArtifact).mockResolvedValue();
});

// ---- pure rules --------------------------------------------------------------------------------------------------

describe('inline action rules', () => {
  const task = { creatorId: 'creator', authorId: 'creator', assigneeIds: ['u1'], archived: false, parentId: null };

  it('offers "for someone" and managing other assignees only with tasks.assign (AS-3, ST-2)', () => {
    expect(canAssignOthers(COORDINATOR)).toBe(true);
    expect(canAssignOthers(user('admin', [], true))).toBe(true);
    expect(canAssignOthers(ASSIGNEE)).toBe(false);
    expect(canAssignOthers(null)).toBe(false);

    expect(canManageAssignee(COORDINATOR, task, 'u2')).toBe(true);
    expect(canManageAssignee(ASSIGNEE, task, 'u2')).toBe(false);
    // Oneself: as the creator with tasks.create, or with tasks.edit.any; a plain assignee cannot remove themselves.
    expect(canManageAssignee(CREATOR, task, 'creator')).toBe(true);
    expect(canManageAssignee(user('creator', ['tasks.view']), task, 'creator')).toBe(false);
    expect(canManageAssignee(user('editor', ['tasks.edit.any']), task, 'editor')).toBe(true);
    expect(canManageAssignee(ASSIGNEE, task, 'u1')).toBe(false);
  });

  it('adds subtasks with tasks.create to active parents only (ST-1, ST-2)', () => {
    expect(canAddSubtask(ASSIGNEE, task)).toBe(true);
    expect(canAddSubtask(OUTSIDER, task)).toBe(false);
    expect(canAddSubtask(ASSIGNEE, { ...task, parentId: 't0' })).toBe(false);
    expect(canAddSubtask(ASSIGNEE, { ...task, archived: true })).toBe(false);
  });

  it('lets the creator, assignees and tasks.edit.any manage artifacts (AR-3)', () => {
    expect(canManageArtifacts(ASSIGNEE, task)).toBe(true);
    expect(canManageArtifacts(CREATOR, task)).toBe(true);
    expect(canManageArtifacts(user('editor', ['tasks.edit.any']), task)).toBe(true);
    expect(canManageArtifacts(OUTSIDER, task)).toBe(false);
    expect(canManageArtifacts(ASSIGNEE, { ...task, archived: true })).toBe(false);
  });

  it('toggles a subtask to the first DONE status and back to the first TODO (else IN_PROGRESS) status', () => {
    const wip: api.SpaceStatus = { id: 'w', name: 'w', category: 'IN_PROGRESS', position: 2, archived: false };
    const oldDone: api.SpaceStatus = { ...DONE, id: 'old', archived: true, position: 0 };
    expect(subtaskToggleTarget([oldDone, TODO, DONE], true)).toBe('s2');
    expect(subtaskToggleTarget([TODO, DONE], false)).toBe('s1');
    expect(subtaskToggleTarget([wip, DONE], false)).toBe('w');
    expect(subtaskToggleTarget([TODO], true)).toBeUndefined();
  });

  it('warns before DONE only when open subtasks exist and the task is not done yet (ST-5)', () => {
    expect(needsOpenSubtaskWarning({ openSubtaskCount: 2 }, 'TODO', 'DONE')).toBe(true);
    expect(needsOpenSubtaskWarning({ openSubtaskCount: 0 }, 'TODO', 'DONE')).toBe(false);
    expect(needsOpenSubtaskWarning({ openSubtaskCount: 2 }, 'TODO', 'IN_PROGRESS')).toBe(false);
    expect(needsOpenSubtaskWarning({ openSubtaskCount: 2 }, 'DONE', 'DONE')).toBe(false);
  });

  it('shows the suggest-close banner from the backend flag only (ST-4)', () => {
    const t = { suggestClose: true, archived: false };
    expect(showSuggestClose(t, 'TODO', [TODO, DONE])).toBe(true);
    expect(showSuggestClose({ ...t, suggestClose: false }, 'TODO', [TODO, DONE])).toBe(false);
    expect(showSuggestClose({ ...t, archived: true }, 'TODO', [TODO, DONE])).toBe(false);
    expect(showSuggestClose(t, 'DONE', [TODO, DONE])).toBe(false);
    expect(showSuggestClose(t, 'TODO', [TODO])).toBe(false);
  });

  it('names the subtasks that block a parent deadline (ST-3)', () => {
    const subs = [
      summary({ id: 'a', title: 'A', deadline: '2031-03-12' }),
      summary({ id: 'b', title: 'B', deadline: '2031-03-01' }),
      summary({ id: 'c', title: 'C', deadline: '2031-03-20', archived: true }),
      summary({ id: 'd', title: 'D', deadline: null }),
    ];
    expect(subtasksBlockingDeadline('2031-03-05', subs)).toEqual([{ id: 'a', title: 'A', deadline: '2031-03-12' }]);
    expect(subtasksBlockingDeadline(null, subs)).toEqual([]);
    const server = new api.SubtaskDeadlineConflictError(422, [{ id: 'x', title: 'X', deadline: '2031-04-01' }]);
    expect(deadlineConflictsOf(server, '2031-03-05', subs)).toEqual([{ id: 'x', title: 'X', deadline: '2031-04-01' }]);
    expect(deadlineConflictsOf(new ApiProblemError(422, 'TASK_DEADLINE_BEFORE_SUBTASKS'), '2031-03-05', subs)).toHaveLength(1);
    expect(api.conflictingSubtasksOf({ conflictingSubtasks: [{ id: 'x', title: 'X', deadline: '2031-04-01' }, { id: 1 }] })).toEqual([
      { id: 'x', title: 'X', deadline: '2031-04-01' },
    ]);
    expect(api.conflictingSubtasksOf({})).toEqual([]);
  });

  it('validates the inline subtask form before sending', () => {
    expect(subtaskDeadlineError('', '2031-03-10')).toBeNull();
    expect(subtaskDeadlineError('2031-03-10', '2031-03-10')).toBeNull();
    expect(subtaskDeadlineError('2031-03-11', '2031-03-10')).toBe('tasks.errors.SUBTASK_DEADLINE_AFTER_PARENT');
    expect(subtaskDeadlineError('2031-03-11', null)).toBeNull();
    expect(subtaskDeadlineError('2031-02-30', null)).toBe('tasks.validation.dueDateInvalid');

    const draft = { title: ' Фейк ', deadline: '', target: 'someone' as const, assigneeId: null };
    expect(validateSubtaskDraft(draft, null)).toEqual({ assignee: 'tasks.subtasks.assigneeRequired' });
    expect(validateSubtaskDraft({ ...draft, title: ' ', target: 'me' }, null)).toEqual({ title: 'tasks.validation.titleRequired' });
    expect(subtaskRequest({ ...draft, target: 'me' })).toEqual({ title: 'Фейк', deadline: undefined, assigneeIds: undefined });
    expect(subtaskRequest({ ...draft, assigneeId: 'u3', deadline: '2031-03-01' })).toEqual({
      title: 'Фейк',
      deadline: '2031-03-01',
      assigneeIds: ['u3'],
    });
  });

  it('accepts only http(s) links with a host, at most 2048 characters (AR-2)', () => {
    expect(linkUrlError('https://example.org/fake')).toBeNull();
    expect(linkUrlError('  http://example.org  ')).toBeNull();
    expect(linkUrlError('')).toBe('tasks.artifacts.validation.urlRequired');
    for (const bad of ['javascript:alert(1)', 'ftp://example.org/x', 'data:text/html,x', 'https:example.org', 'https://exa mple.org', '//example.org', 'example.org']) {
      expect(linkUrlError(bad)).toBe('tasks.artifacts.validation.urlInvalid');
    }
    expect(linkUrlError(`https://example.org/${'a'.repeat(2049)}`)).toBe('tasks.artifacts.validation.urlTooLong');
  });

  it('maps the new task problems to messages present in uk and en', () => {
    expect(taskErrorKey(new ApiProblemError(403, 'TASK_ASSIGN_FORBIDDEN'))).toBe('tasks.errors.TASK_ASSIGN_FORBIDDEN');
    expect(taskErrorKey(new ApiProblemError(422, 'TASK_ASSIGNEE_INACTIVE'))).toBe('tasks.errors.TASK_ASSIGNEE_INACTIVE');
    expect(artifactErrorKey(new ApiProblemError(403, 'TASK_CONTRIBUTE_FORBIDDEN'))).toBe('tasks.artifacts.forbidden');
    expect(artifactErrorKey(new ApiProblemError(422, 'TASK_ARTIFACT_URL_INVALID'))).toBe('tasks.errors.TASK_ARTIFACT_URL_INVALID');
    for (const key of [
      'tasks.errors.TASK_ASSIGN_FORBIDDEN',
      'tasks.errors.TASK_ASSIGNEE_INACTIVE',
      'tasks.errors.TASK_ASSIGNEE_DUPLICATE',
      'tasks.errors.SUBTASK_DEPTH_EXCEEDED',
      'tasks.errors.TASK_ARTIFACT_URL_TOO_LONG',
      'tasks.artifacts.forbidden',
      'tasks.suggestClose.title',
      'tasks.subtasks.forSomeone',
    ]) {
      expect(i18n.getResource('uk', 'translation', key)).toBeTruthy();
      expect(i18n.getResource('en', 'translation', key)).toBeTruthy();
    }
  });
});

// ---- subtasks ----------------------------------------------------------------------------------------------------

describe('inline subtasks', () => {
  it('adds a subtask for oneself; "for someone" is not offered without tasks.assign (ST-2)', async () => {
    const u = userEvent.setup();
    renderPanel(DETAIL, ASSIGNEE);
    const b = await body();
    await u.click(within(b).getByRole('button', { name: 'Нова підзадача' }));
    const form = within(b).getByTestId('subtask-form');
    expect(within(form).queryByText('Для іншого учасника')).toBeNull();
    expect(within(form).getByText('Підзадачу буде призначено вам.')).toBeInTheDocument();

    await u.type(within(form).getByLabelText(/Назва підзадачі/), 'Фейкова нова підзадача');
    await u.click(within(form).getByRole('button', { name: 'Додати підзадачу' }));
    await waitFor(() =>
      expect(api.createSubtask).toHaveBeenCalledWith('t1', { title: 'Фейкова нова підзадача', deadline: undefined, assigneeIds: undefined }),
    );
    await waitFor(() => expect(within(b).queryByTestId('subtask-form')).toBeNull());
  });

  it('adds a subtask for someone else with tasks.assign', async () => {
    const u = userEvent.setup();
    renderPanel(DETAIL, COORDINATOR);
    const b = await body();
    await u.click(within(b).getByRole('button', { name: 'Нова підзадача' }));
    const form = within(b).getByTestId('subtask-form');
    await u.type(within(form).getByLabelText(/Назва підзадачі/), 'Доручення');
    await u.click(within(form).getByText('Для іншого учасника'));
    // Submitting without a person is refused before any request.
    await u.click(within(form).getByRole('button', { name: 'Додати підзадачу' }));
    expect(await within(form).findByText('Оберіть виконавця.')).toBeInTheDocument();
    expect(api.createSubtask).not.toHaveBeenCalled();

    await pick(u, within(form).getByLabelText(/Виконавець/), 'Вигаданий Петро');
    await u.click(within(form).getByRole('button', { name: 'Додати підзадачу' }));
    await waitFor(() =>
      expect(api.createSubtask).toHaveBeenCalledWith('t1', { title: 'Доручення', deadline: undefined, assigneeIds: ['u3'] }),
    );
  });

  it('refuses a subtask deadline later than the parent before sending, and shows a backend refusal (ST-3)', async () => {
    const u = userEvent.setup();
    renderPanel(DETAIL, ASSIGNEE);
    const b = await body();
    await u.click(within(b).getByRole('button', { name: 'Нова підзадача' }));
    const form = within(b).getByTestId('subtask-form');
    await u.type(within(form).getByLabelText(/Назва підзадачі/), 'Пізня');
    fireEvent.change(within(form).getByLabelText('Дедлайн підзадачі'), { target: { value: '2031-03-11' } });
    await u.click(within(form).getByRole('button', { name: 'Додати підзадачу' }));
    expect(await within(form).findByText(/Дедлайн підзадачі не може бути пізніше/)).toBeInTheDocument();
    expect(api.createSubtask).not.toHaveBeenCalled();

    vi.mocked(api.createSubtask).mockRejectedValue(new ApiProblemError(403, 'TASK_ASSIGN_FORBIDDEN'));
    fireEvent.change(within(form).getByLabelText('Дедлайн підзадачі'), { target: { value: '2031-03-10' } });
    await u.click(within(form).getByRole('button', { name: 'Додати підзадачу' }));
    expect(await within(form).findByText(/Призначати інших можна лише з правом/)).toBeInTheDocument();
  });

  it('toggles a subtask status inline with its version', async () => {
    const u = userEvent.setup();
    renderPanel(DETAIL, ASSIGNEE);
    const b = await body();
    const box = await within(b).findByRole('checkbox', { name: '«Фейкова підзадача» виконано' });
    expect(box).toBeChecked();
    await u.click(box);
    await waitFor(() => expect(api.changeTaskStatus).toHaveBeenCalledWith('sub1', 's1', 3));
  });

  it('offers neither the toggle nor the add form to a viewer without rights', async () => {
    renderPanel(DETAIL, OUTSIDER);
    const b = await body();
    expect(await within(b).findByTestId('panel-subtask')).toHaveTextContent('Фейкова підзадача');
    expect(within(b).queryByRole('checkbox')).toBeNull();
    expect(within(b).queryByRole('button', { name: 'Нова підзадача' })).toBeNull();
  });
});

// ---- assignees ---------------------------------------------------------------------------------------------------

describe('inline assignees', () => {
  it('adds and removes assignees with tasks.assign', async () => {
    const u = userEvent.setup();
    renderPanel(DETAIL, COORDINATOR);
    const b = await body();
    await u.click(await within(b).findByRole('button', { name: 'Прибрати Тестовий Іван з виконавців' }));
    await waitFor(() => expect(api.removeAssignee).toHaveBeenCalledWith('t1', 'u2'));

    await u.click(within(b).getByLabelText('Додати виконавця'));
    // Already assigned members are not offered.
    expect(screen.queryByRole('option', { name: 'Фейкова Олена', hidden: true })).toBeNull();
    await u.click(await screen.findByRole('option', { name: 'Вигаданий Петро', hidden: true }));
    await waitFor(() => expect(api.addAssignee).toHaveBeenCalledWith('t1', 'u3'));
  });

  it('renders a permission error as an i18n message (AS-3)', async () => {
    vi.mocked(api.addAssignee).mockRejectedValue(new ApiProblemError(403, 'TASK_ASSIGN_FORBIDDEN'));
    const u = userEvent.setup();
    renderPanel(DETAIL, COORDINATOR);
    const b = await body();
    await u.click(await within(b).findByLabelText('Додати виконавця'));
    await u.click(await screen.findByRole('option', { name: 'Вигаданий Петро', hidden: true }));
    expect(await within(b).findByText(/Призначати інших можна лише з правом/)).toBeInTheDocument();
  });

  it('lets the creator assign only themselves; others see no assignee controls', async () => {
    const u = userEvent.setup();
    const view = renderPanel({ ...DETAIL, assigneeIds: ['u1'] }, CREATOR);
    let b = await body();
    expect(within(b).queryByLabelText('Додати виконавця')).toBeNull();
    expect(within(b).queryByRole('button', { name: /з виконавців/ })).toBeNull();
    await u.click(await within(b).findByRole('button', { name: 'Призначити мене' }));
    await waitFor(() => expect(api.addAssignee).toHaveBeenCalledWith('t1', 'creator'));
    view.unmount();

    renderPanel(DETAIL, OUTSIDER);
    b = await body();
    expect(await within(b).findByText('Фейкова Олена')).toBeInTheDocument();
    expect(within(b).queryByRole('button', { name: 'Призначити мене' })).toBeNull();
    expect(within(b).queryByRole('button', { name: /з виконавців/ })).toBeNull();
  });
});

// ---- artifacts ---------------------------------------------------------------------------------------------------

describe('inline artifacts', () => {
  it('rejects an invalid link before submit and attaches a valid https link (AR-2)', async () => {
    const u = userEvent.setup();
    renderPanel(DETAIL, ASSIGNEE);
    const b = await body();
    await u.click(within(b).getByRole('button', { name: 'Додати посилання' }));
    const form = within(b).getByTestId('artifact-link-form');
    const url = within(form).getByLabelText(/^Посилання/);
    await u.type(url, 'javascript:alert(1)');
    await u.click(within(form).getByRole('button', { name: 'Прикріпити посилання' }));
    expect(await within(form).findByText(/Дозволено лише повні посилання http або https/)).toBeInTheDocument();
    expect(api.addArtifact).not.toHaveBeenCalled();

    await u.clear(url);
    await u.type(url, 'https://example.org/agenda');
    await u.type(within(form).getByLabelText('Назва (необовʼязково)'), 'Фейковий порядок денний');
    await u.click(within(form).getByRole('button', { name: 'Прикріпити посилання' }));
    await waitFor(() =>
      expect(api.addArtifact).toHaveBeenCalledWith('t1', { type: 'LINK', url: 'https://example.org/agenda', title: 'Фейковий порядок денний' }),
    );
  });

  it('uploads a file and attaches it as a FILE artifact (AR-1)', async () => {
    vi.mocked(attachmentsApi.uploadAttachment).mockResolvedValue({ id: 'f9', fileName: 'фейк.pdf', mimeType: 'application/pdf', size: 10 });
    const u = userEvent.setup();
    renderPanel(DETAIL, ASSIGNEE);
    const b = await body();
    const section = within(b).getByRole('region', { name: 'Артефакти' });
    const input = section.querySelector<HTMLInputElement>('input[type="file"]');
    expect(input).not.toBeNull();
    await u.upload(input as HTMLInputElement, new File(['fake'], 'фейк.pdf', { type: 'application/pdf' }));
    await waitFor(() => expect(api.addArtifact).toHaveBeenCalledWith('t1', { type: 'FILE', fileId: 'f9' }));
  });

  it('removes an artifact; a forbidden removal shows the artifact rule (AR-3)', async () => {
    const u = userEvent.setup();
    renderPanel(DETAIL, ASSIGNEE);
    const b = await body();
    expect(within(b).getByTestId('artifact-added')).toHaveTextContent('Фейкова Олена');
    await u.click(within(b).getByRole('button', { name: 'Прибрати артефакт Фейкове посилання' }));
    await waitFor(() => expect(api.removeArtifact).toHaveBeenCalledWith('t1', 'a2'));

    vi.mocked(api.removeArtifact).mockRejectedValue(new ApiProblemError(403, 'TASK_CONTRIBUTE_FORBIDDEN'));
    await u.click(within(b).getByRole('button', { name: 'Прибрати артефакт Фейкове посилання' }));
    expect(await within(b).findByText(/Додавати й прибирати артефакти можуть/)).toBeInTheDocument();
  });

  it('offers no artifact controls to a viewer without rights', async () => {
    renderPanel(DETAIL, OUTSIDER);
    const b = await body();
    expect(within(b).getByRole('link', { name: 'Фейкове посилання' })).toHaveAttribute('rel', 'noopener noreferrer');
    expect(within(b).queryByRole('button', { name: /Прибрати артефакт/ })).toBeNull();
    expect(within(b).queryByRole('button', { name: 'Додати посилання' })).toBeNull();
    expect(within(b).queryByRole('button', { name: 'Додати файл' })).toBeNull();
  });
});

// ---- suggest close, status warning, deadline ---------------------------------------------------------------------

describe('quick status and deadline actions', () => {
  it('suggests closing when suggestClose is true and changes the status only on click (ST-4)', async () => {
    const u = userEvent.setup();
    renderPanel({ ...DETAIL, suggestClose: true }, ASSIGNEE);
    const b = await body();
    const banner = await within(b).findByTestId('suggest-close');
    expect(banner).toHaveTextContent('Усі підзадачі виконано');
    // Showing and dismissing the banner changes nothing.
    await waitFor(() => expect(api.getSpace).toHaveBeenCalled());
    expect(api.changeTaskStatus).not.toHaveBeenCalled();
    await u.click(within(banner).getByRole('button', { name: 'Не зараз' }));
    expect(within(b).queryByTestId('suggest-close')).toBeNull();
    expect(api.changeTaskStatus).not.toHaveBeenCalled();
  });

  it('closes the parent with one click on the banner', async () => {
    const u = userEvent.setup();
    renderPanel({ ...DETAIL, suggestClose: true }, ASSIGNEE);
    const b = await body();
    const banner = await within(b).findByTestId('suggest-close');
    await u.click(within(banner).getByRole('button', { name: 'Закрити задачу: «Фейкове готово»' }));
    await waitFor(() => expect(api.changeTaskStatus).toHaveBeenCalledWith('t1', 's2', 7));
    expect(api.changeTaskStatus).toHaveBeenCalledTimes(1);
  });

  it('shows no banner without the flag or to users who may not change the status', async () => {
    const view = renderPanel(DETAIL, ASSIGNEE);
    let b = await body();
    expect(within(b).queryByTestId('suggest-close')).toBeNull();
    view.unmount();
    renderPanel({ ...DETAIL, suggestClose: true }, OUTSIDER);
    b = await body();
    await within(b).findByTestId('panel-status');
    expect(within(b).queryByTestId('suggest-close')).toBeNull();
  });

  it('warns before moving a parent with open subtasks to DONE and moves only on confirmation (ST-5)', async () => {
    const u = userEvent.setup();
    renderPanel({ ...DETAIL, openSubtaskCount: 2 }, ASSIGNEE);
    const b = await body();
    await u.click(await within(b).findByLabelText('Статус'));
    await u.click(await screen.findByRole('option', { name: 'Фейкове готово', hidden: true }));
    const warning = await within(b).findByTestId('open-subtasks-warning');
    expect(warning).toHaveTextContent('Невиконаних підзадач: 2');
    expect(api.changeTaskStatus).not.toHaveBeenCalled();

    await u.click(within(warning).getByRole('button', { name: 'Скасувати' }));
    expect(within(b).queryByTestId('open-subtasks-warning')).toBeNull();
    expect(api.changeTaskStatus).not.toHaveBeenCalled();

    await u.click(within(b).getByLabelText('Статус'));
    await u.click(await screen.findByRole('option', { name: 'Фейкове готово', hidden: true }));
    await u.click(within(await within(b).findByTestId('open-subtasks-warning')).getByRole('button', { name: 'Усе одно перевести' }));
    await waitFor(() => expect(api.changeTaskStatus).toHaveBeenCalledWith('t1', 's2', 7));
  });

  it('sets the deadline inline, sending the other fields unchanged (UI-5)', async () => {
    const u = userEvent.setup();
    renderPanel(DETAIL, CREATOR);
    const b = await body();
    await u.click(within(b).getByRole('button', { name: 'Змінити дедлайн' }));
    fireEvent.change(within(b).getByLabelText('Новий дедлайн'), { target: { value: '2031-03-20' } });
    await u.click(within(b).getByRole('button', { name: 'Зберегти дедлайн' }));
    await waitFor(() =>
      expect(api.updateTask).toHaveBeenCalledWith(
        't1',
        { title: 'Фейкова задача', description: undefined, priority: 'NORMAL', deadline: '2031-03-20', labels: [], customFieldValues: {} },
        ['rel1'],
        ['old1'],
        7,
      ),
    );
  });

  it('lists the conflicting subtasks when the backend rejects a deadline (ST-3)', async () => {
    vi.mocked(api.updateTask).mockRejectedValue(
      new api.SubtaskDeadlineConflictError(422, [{ id: 'sub7', title: 'Фейкова пізня підзадача', deadline: '2031-03-09' }]),
    );
    const u = userEvent.setup();
    renderPanel(DETAIL, CREATOR);
    const b = await body();
    await u.click(within(b).getByRole('button', { name: 'Змінити дедлайн' }));
    fireEvent.change(within(b).getByLabelText('Новий дедлайн'), { target: { value: '2031-03-05' } });
    await u.click(within(b).getByRole('button', { name: 'Зберегти дедлайн' }));
    const alert = await within(b).findByTestId('deadline-conflict');
    expect(alert).toHaveTextContent('Дедлайн не може бути раніше за дедлайни підзадач');
    expect(alert).toHaveTextContent('«Фейкова пізня підзадача» — до 09.03.2031');
    // Nothing changed: the shown deadline is still the old one.
    expect(within(b).getByTestId('panel-deadline')).toHaveTextContent('10.03.2031');
  });

  it('refuses a parent deadline before a known subtask deadline without a request (ST-3)', async () => {
    const u = userEvent.setup();
    renderPanel({ ...DETAIL, subtasks: [{ ...SUBTASK, deadline: '2031-03-08' }] }, CREATOR);
    const b = await body();
    await u.click(within(b).getByRole('button', { name: 'Змінити дедлайн' }));
    fireEvent.change(within(b).getByLabelText('Новий дедлайн'), { target: { value: '2031-03-01' } });
    await u.click(within(b).getByRole('button', { name: 'Зберегти дедлайн' }));
    expect(await within(b).findByTestId('deadline-conflict')).toHaveTextContent('«Фейкова підзадача» — до 08.03.2031');
    expect(api.updateTask).not.toHaveBeenCalled();
  });

  it('shows the reload hint when the deadline edit hits a version conflict', async () => {
    vi.mocked(api.updateTask).mockRejectedValue(new ApiProblemError(409, 'TASK_VERSION_CONFLICT'));
    const u = userEvent.setup();
    renderPanel(DETAIL, CREATOR);
    const b = await body();
    await u.click(within(b).getByRole('button', { name: 'Змінити дедлайн' }));
    fireEvent.change(within(b).getByLabelText('Новий дедлайн'), { target: { value: '2031-03-20' } });
    await u.click(within(b).getByRole('button', { name: 'Зберегти дедлайн' }));
    expect(await within(b).findByTestId('version-conflict')).toBeInTheDocument();
  });

  it('does not offer the deadline editor to assignees who are not the creator', async () => {
    renderPanel(DETAIL, ASSIGNEE);
    const b = await body();
    expect(within(b).getByTestId('panel-deadline')).toHaveTextContent('10.03.2031');
    expect(within(b).queryByRole('button', { name: 'Змінити дедлайн' })).toBeNull();
  });
});

describe('inline actions at 375 px', () => {
  it('renders the same controls inside the full-screen sheet', async () => {
    setViewport(375);
    const u = userEvent.setup();
    renderPanel(DETAIL, COORDINATOR);
    const dialog = await screen.findByRole('dialog');
    const b = await within(dialog).findByTestId('task-panel-body');
    await u.click(within(b).getByRole('button', { name: 'Нова підзадача' }));
    expect(within(b).getByText('Для іншого учасника')).toBeInTheDocument();
    expect(within(b).getByLabelText('Додати виконавця')).toBeInTheDocument();
  });
});
