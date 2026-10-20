import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { configure, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { STUB_AUTH_STATE, CurrentUserProvider, type AuthState } from '../auth/CurrentUser';
import i18n, { changeLanguage } from '../i18n';
import * as memberApi from '../members/memberApi';
import * as orgApi from '../org/orgApi';
import { renderWithProviders } from '../test/render';
import { SpaceSettingsPage } from './SpaceSettingsPage';
import { SpacesPage } from './SpacesPage';
import { TaskDetailPage } from './TaskDetailPage';
import { TaskFormModal } from './TaskFormModal';
import { TasksPage } from './TasksPage';
import * as api from './tasksApi';
import {
  activeFilterCount,
  canEditTask,
  DEFAULT_QUERY,
  moveItem,
  normalizeLabels,
  taskErrorKey,
  todayKyiv,
  validateFieldDrafts,
  validateFieldValue,
  validateTaskForm,
  valuesFromDraft,
} from './taskLogic';

vi.mock('./tasksApi', async (orig) => ({
  ...(await orig<typeof api>()),
  listSpaces: vi.fn(),
  getSpace: vi.fn(),
  listTasks: vi.fn(),
  getTask: vi.fn(),
  createTask: vi.fn(),
  updateTask: vi.fn(),
  changeTaskStatus: vi.fn(),
  reorderStatuses: vi.fn(),
  archiveTask: vi.fn(),
}));
vi.mock('../members/memberApi', async (orig) => ({ ...(await orig<typeof memberApi>()), listMembers: vi.fn() }));
vi.mock('../org/orgApi', async (orig) => ({ ...(await orig<typeof orgApi>()), getOrgTree: vi.fn() }));

vi.setConfig({ testTimeout: 30000 });
configure({ asyncUtilTimeout: 8000 });

const field = (over: Partial<api.CustomField>): api.CustomField => ({
  id: 'f',
  name: 'Поле',
  type: 'TEXT',
  required: false,
  options: [],
  archived: false,
  ...over,
});

const STATUS_TODO: api.SpaceStatus = { id: 's1', name: 'Фейкове до виконання', category: 'TODO', position: 0, archived: false };
const STATUS_DONE: api.SpaceStatus = { id: 's2', name: 'Фейкове виконано', category: 'DONE', position: 1, archived: false };
const F_TEXT = field({ id: 'f1', name: 'Фейковий текст', type: 'TEXT', required: true });
const F_NUM = field({ id: 'f2', name: 'Фейкове число', type: 'NUMBER' });
const F_SEL = field({ id: 'f3', name: 'Фейковий вибір', type: 'SELECT', options: ['Так', 'Ні'] });
const F_DATE = field({ id: 'f4', name: 'Фейкова дата', type: 'DATE' });

const SPACE: api.Space = {
  id: 'sp1',
  name: 'Фейковий простір',
  orgUnitId: null,
  general: true,
  archived: false,
  statuses: [STATUS_TODO, STATUS_DONE],
  customFields: [F_TEXT, F_NUM, F_SEL, F_DATE],
};
const TASK: api.Task = {
  id: 't1',
  spaceId: 'sp1',
  title: 'Фейкова задача',
  statusId: 's1',
  priority: 'HIGH',
  dueDate: '2031-03-05',
  labels: ['тест'],
  attachmentCount: 0,
  authorId: 'author',
  archived: false,
  createdAt: '2031-01-01T10:00:00Z',
  description: null,
  customFieldValues: { f1: 'значення' },
  relatedTaskIds: ["rel1"],
  attachmentIds: ["att1", "att2"],
};

const authWith = (permissions: string[], id = 'someone', isAdmin = false): AuthState => ({
  ...STUB_AUTH_STATE,
  user: { id, displayName: 'Test User', isAdmin, permissions },
});

function renderIn(ui: React.ReactElement, auth: AuthState, route = '/') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderWithProviders(
    <QueryClientProvider client={queryClient}>
      <CurrentUserProvider value={auth}>{ui}</CurrentUserProvider>
    </QueryClientProvider>,
    route,
  );
}

beforeEach(async () => {
  vi.clearAllMocks();
  await changeLanguage('uk');
  vi.mocked(api.listSpaces).mockResolvedValue([SPACE]);
  vi.mocked(api.getSpace).mockResolvedValue(SPACE);
  vi.mocked(api.listTasks).mockResolvedValue({ items: [TASK], page: 0, totalPages: 1, totalElements: 1 });
  vi.mocked(api.getTask).mockResolvedValue(TASK);
  vi.mocked(memberApi.listMembers).mockResolvedValue([]);
  vi.mocked(orgApi.getOrgTree).mockResolvedValue([]);
});

describe('task logic', () => {
  it('validates custom field values by type', () => {
    expect(validateFieldValue(F_TEXT, '  ')).toBe('required');
    expect(validateFieldValue(F_TEXT, 'ok')).toBeNull();
    expect(validateFieldValue(F_NUM, '')).toBeNull();
    expect(validateFieldValue(F_NUM, 'abc')).toBe('number');
    expect(validateFieldValue(F_NUM, '3,5')).toBeNull();
    expect(validateFieldValue(F_SEL, 'Можливо')).toBe('option');
    expect(validateFieldValue(F_SEL, 'Так')).toBeNull();
    expect(validateFieldValue(F_DATE, '2031-02-30')).toBe('date');
    expect(validateFieldValue(F_DATE, '2031-02-28')).toBeNull();
    expect(validateFieldValue(field({ type: 'TEXT' }), 'x'.repeat(2001))).toBe('tooLong');
  });

  it('collects errors per field and converts drafts to typed values', () => {
    const fields = [F_TEXT, F_NUM, F_SEL];
    expect(validateFieldDrafts(fields, { f1: '', f2: 'x', f3: 'Так' })).toEqual({ f1: 'required', f2: 'number' });
    expect(valuesFromDraft(fields, { f1: ' текст ', f2: '3,5', f3: '' })).toEqual({ f1: 'текст', f2: 3.5 });
  });

  it('validates the task form', () => {
    expect(validateTaskForm({ title: ' ', priority: 'NORMAL', dueDate: '', labels: [] }).title).toBeDefined();
    expect(validateTaskForm({ title: 'a'.repeat(301), priority: 'NORMAL', dueDate: '', labels: [] }).title).toBeDefined();
    expect(validateTaskForm({ title: 'a', priority: 'NORMAL', dueDate: '2031-13-01', labels: [] }).dueDate).toBeDefined();
    expect(validateTaskForm({ title: 'a', priority: 'NORMAL', dueDate: '', labels: ['x'.repeat(51)] }).labels).toBeDefined();
    expect(validateTaskForm({ title: 'a', priority: 'NORMAL', dueDate: '2031-03-05', labels: ['x'] })).toEqual({});
  });

  it('normalizes labels, reorders items and counts filters', () => {
    expect(normalizeLabels([' a ', 'A', '', 'b'])).toEqual(['a', 'b']);
    expect(moveItem([1, 2, 3], 2, 0)).toEqual([3, 1, 2]);
    expect(moveItem([1, 2, 3], 0, 5)).toEqual([1, 2, 3]);
    expect(activeFilterCount(DEFAULT_QUERY)).toBe(0);
    expect(activeFilterCount({ ...DEFAULT_QUERY, spaceId: 'x', labels: ['a', 'b'], includeArchived: true })).toBe(3);
  });

  it('offers edit to the author or Admin only and maps errors', () => {
    const author = authWith([], 'author').user;
    expect(canEditTask(author, TASK)).toBe(true);
    expect(canEditTask(authWith([], 'other').user, TASK)).toBe(false);
    expect(canEditTask(authWith([], 'other', true).user, TASK)).toBe(true);
    expect(canEditTask(null, TASK)).toBe(false);
    expect(taskErrorKey(new ApiProblemError(403, 'TASK_EDIT_FORBIDDEN'))).toBe('problems.FORBIDDEN');
    expect(taskErrorKey(new ApiProblemError(409, 'TASK_ARCHIVED'))).toBe('problems.TASK_ARCHIVED');
  });

  it('computes the Kyiv calendar date across midnight', () => {
    expect(todayKyiv(new Date('2031-01-10T22:30:00Z'))).toBe('2031-01-11');
  });

  it('has matching uk and en problem texts for task codes', () => {
    expect(i18n.getResource('uk', 'translation', 'problems.TASK_EDIT_FORBIDDEN')).toBeTruthy();
    expect(i18n.getResource('en', 'translation', 'problems.SPACE_STATUS_IN_USE')).toBeTruthy();
  });
});

describe('TasksPage', () => {
  it('lists tasks as cards with the status name', async () => {
    renderIn(<TasksPage />, authWith(['tasks.view']), '/tasks');
    expect(await screen.findByText('Фейкова задача')).toBeInTheDocument();
    expect(await screen.findByText(STATUS_TODO.name)).toBeInTheDocument();
    expect(screen.getAllByTestId('task-card')).toHaveLength(1);
  });

  it('hides create without tasks.create and shows it with', async () => {
    const { unmount } = renderIn(<TasksPage />, authWith(['tasks.view']));
    await screen.findByText('Фейкова задача');
    expect(screen.queryByRole('button', { name: 'Нова задача' })).toBeNull();
    unmount();
    renderIn(<TasksPage />, authWith(['tasks.view', 'tasks.create']));
    expect(await screen.findByRole('button', { name: 'Нова задача' })).toBeInTheDocument();
  });

  it('keeps all filters inside a drawer and queries with them', async () => {
    const user = userEvent.setup();
    renderIn(<TasksPage />, authWith(['tasks.view']));
    await screen.findByText('Фейкова задача');
    expect(screen.queryByLabelText('Строк від')).toBeNull();
    await user.click(screen.getByRole('button', { name: 'Фільтри' }));
    const from = await screen.findByLabelText('Строк від');
    await user.type(from, '2031-03-01');
    await waitFor(() => expect(vi.mocked(api.listTasks).mock.calls.some(([q]) => q.dueFrom === '2031-03-01')).toBe(true));
    expect(screen.getByLabelText('Строк до')).toBeInTheDocument();
    expect(screen.getAllByText('Мітки').length).toBeGreaterThan(0);
  });

  it('shows a localized error when loading fails', async () => {
    vi.mocked(api.listTasks).mockRejectedValue(new ApiProblemError(403, 'FORBIDDEN'));
    renderIn(<TasksPage />, authWith(['tasks.view']));
    expect(await screen.findByText('У вас немає прав на цю дію.')).toBeInTheDocument();
  });
});

describe('TaskFormModal', () => {
  it('renders custom fields by type and blocks submit on invalid values', async () => {
    const user = userEvent.setup();
    renderIn(<TaskFormModal task={null} spaces={[SPACE]} defaultSpaceId="sp1" onClose={() => undefined} onSaved={() => undefined} />, authWith(['tasks.create']));
    await screen.findByLabelText(/Фейковий текст/);
    expect(screen.getByLabelText(/Фейкове число/)).toBeInTheDocument();
    expect(screen.getByLabelText(/Фейкова дата/)).toHaveAttribute('type', 'date');
    expect(screen.getAllByText(/Фейковий вибір/).length).toBeGreaterThan(0);

    await user.click(screen.getByRole('button', { name: 'Зберегти' }));
    expect(await screen.findByText('Вкажіть назву.')).toBeInTheDocument();
    expect(screen.getByText("Обов'язкове поле.")).toBeInTheDocument();
    expect(api.createTask).not.toHaveBeenCalled();
  });

  it('submits typed custom values', async () => {
    const user = userEvent.setup();
    vi.mocked(api.createTask).mockResolvedValue(TASK);
    const onSaved = vi.fn();
    renderIn(<TaskFormModal task={null} spaces={[SPACE]} defaultSpaceId="sp1" onClose={() => undefined} onSaved={onSaved} />, authWith(['tasks.create']));
    await user.type(await screen.findByLabelText(/^Назва/), 'Нова фейкова задача');
    await user.type(screen.getByLabelText(/Фейковий текст/), 'abc');
    await user.type(screen.getByLabelText(/Фейкове число/), '42');
    await user.click(screen.getByRole('button', { name: 'Зберегти' }));
    await waitFor(() => expect(api.createTask).toHaveBeenCalled());
    const input = vi.mocked(api.createTask).mock.calls[0][0];
    expect(input).toMatchObject({ spaceId: 'sp1', title: 'Нова фейкова задача', priority: 'NORMAL', customFieldValues: { f1: 'abc', f2: 42 } });
    await waitFor(() => expect(onSaved).toHaveBeenCalled());
  });
});

describe('TaskFormModal edit', () => {
  it('sends existing attachments and related tasks back on PUT', async () => {
    const user = userEvent.setup();
    vi.mocked(api.updateTask).mockResolvedValue(TASK);
    renderIn(<TaskFormModal task={TASK} spaces={[SPACE]} onClose={() => undefined} onSaved={() => undefined} />, authWith(["tasks.view"], "author"));
    await screen.findByDisplayValue("Фейкова задача");
    await user.click(screen.getByRole("button", { name: "Зберегти" }));
    await waitFor(() => expect(api.updateTask).toHaveBeenCalled());
    const [id, input, related, attachments] = vi.mocked(api.updateTask).mock.calls[0];
    expect(id).toBe("t1");
    expect(related).toEqual(["rel1"]);
    expect(attachments).toEqual(["att1", "att2"]);
    expect(input).toMatchObject({ title: "Фейкова задача", priority: "HIGH", dueDate: "2031-03-05", labels: ["тест"], customFieldValues: { f1: "значення" } });
  });
});

describe('TaskDetailPage', () => {
  const renderDetail = (auth: AuthState) =>
    renderIn(
      <Routes>
        <Route path="/tasks/:id" element={<TaskDetailPage />} />
      </Routes>,
      auth,
      '/tasks/t1',
    );

  it('offers edit to the author but not to others', async () => {
    const { unmount } = renderDetail(authWith(['tasks.view'], 'author'));
    expect(await screen.findByRole('button', { name: 'Редагувати' })).toBeInTheDocument();
    unmount();
    renderDetail(authWith(['tasks.view'], 'other'));
    await screen.findByText('Фейкова задача');
    expect(screen.queryByRole('button', { name: 'Редагувати' })).toBeNull();
  });

  it('gates archive by tasks.archive and shows the placeholder tabs', async () => {
    renderDetail(authWith(['tasks.view'], 'author'));
    await screen.findByText('Фейкова задача');
    expect(screen.queryByRole('button', { name: 'В архів' })).toBeNull();
    expect(screen.getByRole('tab', { name: 'Коментарі' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Рішення' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Історія' })).toBeInTheDocument();
  });
});

describe('spaces', () => {
  it('hides space creation without spaces.manage', async () => {
    renderIn(<SpacesPage />, authWith(['tasks.view']), '/spaces');
    await screen.findByText('Фейковий простір');
    expect(screen.queryByRole('button', { name: 'Новий простір' })).toBeNull();
  });

  it('shows 403 on the settings page without spaces.manage', () => {
    renderIn(
      <Routes>
        <Route path="/spaces/:id" element={<SpaceSettingsPage />} />
      </Routes>,
      authWith(['tasks.view']),
      '/spaces/sp1',
    );
    expect(api.getSpace).not.toHaveBeenCalled();
  });

  it('shows statuses and custom fields editors and reorders statuses', async () => {
    const user = userEvent.setup();
    vi.mocked(api.reorderStatuses).mockResolvedValue();
    renderIn(
      <Routes>
        <Route path="/spaces/:id" element={<SpaceSettingsPage />} />
      </Routes>,
      authWith(['tasks.view', 'spaces.manage']),
      '/spaces/sp1',
    );
    expect(await screen.findByText(STATUS_TODO.name)).toBeInTheDocument();
    expect(screen.getByText(F_SEL.name)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: `Опустити статус «${STATUS_TODO.name}»` }));
    await waitFor(() => expect(api.reorderStatuses).toHaveBeenCalledWith('sp1', ['s2', 's1']));
  });
});
