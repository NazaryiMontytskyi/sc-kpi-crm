import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { configure, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { CurrentUserProvider, STUB_AUTH_STATE, type AuthState, type CurrentUser } from '../auth/CurrentUser';
import { changeLanguage } from '../i18n';
import * as memberApi from '../members/memberApi';
import { renderWithProviders } from '../test/render';
import {
  availableActions,
  canIssueResolution,
  resolutionErrorKey,
  resolutionWarningKey,
  STATE_COLORS,
  validateResolutionForm,
} from './resolutionLogic';
import * as api from './resolutionsApi';
import { RESOLUTION_STATES } from './resolutionsApi';
import { ResolutionsPanel } from './ResolutionsPanel';

vi.mock('./resolutionsApi', async (orig) => ({
  ...(await orig<typeof api>()),
  listTaskResolutions: vi.fn(),
  getResolution: vi.fn(),
  issueResolution: vi.fn(),
  simpleResolutionAction: vi.fn(),
  submitResolution: vi.fn(),
  reworkResolution: vi.fn(),
  cancelResolution: vi.fn(),
  redirectResolution: vi.fn(),
}));
vi.mock('../members/memberApi', async (orig) => ({ ...(await orig<typeof memberApi>()), listMembers: vi.fn() }));

vi.setConfig({ testTimeout: 30000 });
configure({ asyncUtilTimeout: 8000 });

const member = (id: string, lastName: string, archived = false): memberApi.MemberSummary => ({
  id,
  lastName,
  firstName: 'Фейк',
  middleName: '',
  faculty: null,
  studyGroup: '',
  telegram: '',
  joinedAt: '2031-01-01',
  avatarFileId: null,
  archived,
});
const ALICE = member('alice', 'Фейкова');
const BOB = member('bob', 'Фейковий');
const CAROL = member('carol', 'Тестова');
const GONE = member('gone', 'Архівний', true);

const userOf = (id: string, permissions: string[] = [], isAdmin = false): CurrentUser => ({ id, displayName: 'Test', isAdmin, permissions });
const authOf = (user: CurrentUser): AuthState => ({ ...STUB_AUTH_STATE, user });

const resolution = (over: Partial<api.Resolution> = {}): api.Resolution => ({
  id: 'r1',
  taskId: 't1',
  authorId: 'alice',
  executorId: 'bob',
  coExecutorIds: [],
  instruction: 'Підготувати звіт',
  dueDate: '2031-06-01',
  parentResolutionId: null,
  state: 'NEW',
  overdue: false,
  createdAt: '2031-01-01T10:00:00Z',
  updatedAt: '2031-01-01T10:00:00Z',
  ...over,
});
const detail = (over: Partial<api.ResolutionDetail> = {}): api.ResolutionDetail => ({
  ...resolution(),
  completionNote: '',
  history: [],
  ...over,
});
const page = (items: api.Resolution[]): api.ResolutionPage => ({ items, page: 0, totalPages: 1, totalElements: items.length });

describe('resolution action availability', () => {
  const alice = userOf('alice');
  const bob = userOf('bob');
  const bobRedirect = userOf('bob', ['resolutions.redirect']);

  it('offers the executor start / submit / resume per state', () => {
    expect(availableActions(bob, resolution({ state: 'NEW' }))).toEqual(['start']);
    expect(availableActions(bob, resolution({ state: 'IN_PROGRESS' }))).toEqual(['submit']);
    expect(availableActions(bob, resolution({ state: 'REWORK' }))).toEqual(['resume']);
    expect(availableActions(bob, resolution({ state: 'SUBMITTED' }))).toEqual([]);
  });

  it('offers redirect only to an executor holding resolutions.redirect, only while in progress', () => {
    expect(availableActions(bobRedirect, resolution({ state: 'IN_PROGRESS' }))).toEqual(['submit', 'redirect']);
    expect(availableActions(bobRedirect, resolution({ state: 'NEW' }))).toEqual(['start']);
    expect(availableActions(userOf('bob', [], true), resolution({ state: 'IN_PROGRESS' }))).toContain('redirect');
  });

  it('offers the author accept / rework when submitted and cancel while not final', () => {
    expect(availableActions(alice, resolution({ state: 'SUBMITTED' }))).toEqual(['accept', 'rework', 'cancel']);
    expect(availableActions(alice, resolution({ state: 'NEW' }))).toEqual(['cancel']);
    expect(availableActions(alice, resolution({ state: 'IN_PROGRESS' }))).toEqual(['cancel']);
    expect(availableActions(alice, resolution({ state: 'REWORK' }))).toEqual(['cancel']);
  });

  it('offers nothing in final states or to bystanders', () => {
    for (const state of ['ACCEPTED', 'REDIRECTED', 'CANCELED'] as const) {
      expect(availableActions(alice, resolution({ state }))).toEqual([]);
      expect(availableActions(bobRedirect, resolution({ state }))).toEqual([]);
    }
    expect(availableActions(userOf('zed', ['resolutions.redirect']), resolution({ state: 'SUBMITTED' }))).toEqual([]);
    expect(availableActions(null, resolution())).toEqual([]);
  });

  it('combines both roles when author and executor are the same person', () => {
    const self = userOf('alice');
    expect(availableActions(self, resolution({ executorId: 'alice', state: 'IN_PROGRESS' }))).toEqual(['submit', 'cancel']);
  });

  it('offers issue only with resolutions.create on an active task', () => {
    expect(canIssueResolution(userOf('a', ['resolutions.create']), false)).toBe(true);
    expect(canIssueResolution(userOf('a', ['resolutions.create']), true)).toBe(false);
    expect(canIssueResolution(userOf('a', ['tasks.view']), false)).toBe(false);
    expect(canIssueResolution(userOf('a', [], true), false)).toBe(true);
    expect(canIssueResolution(null, false)).toBe(false);
  });
});

describe('resolution form logic', () => {
  it('validates executor, instruction and due date', () => {
    expect(validateResolutionForm({ executorId: 'bob', instruction: 'x', dueDate: '2031-06-01' }, '2031-01-01')).toEqual({});
    expect(Object.keys(validateResolutionForm({ executorId: null, instruction: '  ', dueDate: '' }, '2031-01-01'))).toEqual([
      'executorId',
      'instruction',
      'dueDate',
    ]);
    expect(validateResolutionForm({ executorId: 'bob', instruction: 'x', dueDate: '2030-12-31' }, '2031-01-01').dueDate).toBe(
      'tasks.resolutions.validation.dueInPast',
    );
    expect(validateResolutionForm({ executorId: 'bob', instruction: 'x', dueDate: '2031-01-01' }, '2031-01-01')).toEqual({});
  });

  it('maps problem codes to i18n keys', () => {
    expect(resolutionErrorKey(new ApiProblemError(422, 'RESOLUTION_REDIRECT_TO_SELF'))).toBe('problems.RESOLUTION_REDIRECT_TO_SELF');
    expect(resolutionErrorKey(new ApiProblemError(422, 'RESOLUTION_REDIRECT_CYCLE'))).toBe('problems.RESOLUTION_REDIRECT_CYCLE');
    expect(resolutionErrorKey(new ApiProblemError(422, 'RESOLUTION_DUE_DATE_IN_PAST'))).toBe('problems.RESOLUTION_DUE_DATE_IN_PAST');
    expect(resolutionErrorKey(new ApiProblemError(422, 'RESOLUTION_EXECUTOR_INVALID'))).toBe('problems.RESOLUTION_EXECUTOR_INVALID');
    expect(resolutionErrorKey(new ApiProblemError(403, 'RESOLUTION_NOT_EXECUTOR'))).toBe('problems.RESOLUTION_NOT_EXECUTOR');
    expect(resolutionErrorKey(new ApiProblemError(403, 'RESOLUTION_NOT_AUTHOR'))).toBe('problems.RESOLUTION_NOT_AUTHOR');
    expect(resolutionErrorKey(new ApiProblemError(422, 'RESOLUTION_TRANSITION_NOT_ALLOWED'))).toBe('problems.RESOLUTION_TRANSITION_NOT_ALLOWED');
    expect(resolutionErrorKey(new ApiProblemError(403, undefined))).toBe('problems.FORBIDDEN');
    expect(resolutionWarningKey('RESOLUTION_CHILD_DUE_AFTER_PARENT')).toBe('problems.RESOLUTION_CHILD_DUE_AFTER_PARENT');
    expect(resolutionWarningKey('SOMETHING_ELSE')).toBeNull();
  });

  it('never uses the brand color for state badges', () => {
    for (const s of RESOLUTION_STATES) expect(STATE_COLORS[s]).not.toBe('studrada');
    expect(STATE_COLORS.ACCEPTED).toBe('green');
  });
});

function renderPanel(user: CurrentUser, taskArchived = false) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderWithProviders(
    <QueryClientProvider client={queryClient}>
      <CurrentUserProvider value={authOf(user)}>
        <ResolutionsPanel taskId="t1" taskArchived={taskArchived} />
      </CurrentUserProvider>
    </QueryClientProvider>,
  );
}

beforeEach(async () => {
  vi.clearAllMocks();
  await changeLanguage('uk');
  vi.mocked(memberApi.listMembers).mockResolvedValue([ALICE, BOB, CAROL, GONE]);
  vi.mocked(api.listTaskResolutions).mockResolvedValue(page([]));
  vi.mocked(api.getResolution).mockResolvedValue(detail());
});

describe('ResolutionsPanel', () => {
  it('lists resolutions with author, executor, due date, state and overdue highlighting', async () => {
    vi.mocked(api.listTaskResolutions).mockResolvedValue(
      page([resolution({ state: 'IN_PROGRESS', overdue: true, coExecutorIds: ['carol'] }), resolution({ id: 'r2', state: 'ACCEPTED' })]),
    );
    renderPanel(userOf('zed'));
    expect(await screen.findAllByTestId('resolution')).toHaveLength(2);
    expect(screen.getAllByText('Фейкова Фейк', { exact: false }).length).toBeGreaterThan(0);
    expect(screen.getByText('Виконується')).toBeInTheDocument();
    expect(screen.getByText('Прийнята')).toBeInTheDocument();
    expect(screen.getByText('Прострочено')).toBeInTheDocument();
    expect(screen.getAllByText('01.06.2031', { exact: false }).length).toBe(2);
    // a bystander gets no issue button and no action buttons
    expect(screen.queryByRole('button', { name: 'Видати резолюцію' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Здати результат' })).toBeNull();
  });

  it('shows the empty state and hides issue without the permission or on an archived task', async () => {
    const { unmount } = renderPanel(userOf('zed', ['tasks.view']));
    expect(await screen.findByText('Резолюцій ще немає.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Видати резолюцію' })).toBeNull();
    unmount();
    renderPanel(userOf('zed', ['resolutions.create']), true);
    await screen.findByText('Резолюцій ще немає.');
    expect(screen.queryByRole('button', { name: 'Видати резолюцію' })).toBeNull();
  });

  it('issues a resolution after validating the form', async () => {
    vi.mocked(api.issueResolution).mockResolvedValue(detail());
    renderPanel(userOf('alice', ['resolutions.create']));
    await userEvent.click(await screen.findByRole('button', { name: 'Видати резолюцію' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Видати' }));
    expect(await within(dialog).findByText('Оберіть виконавця.')).toBeInTheDocument();
    expect(within(dialog).getByText('Вкажіть доручення.')).toBeInTheDocument();
    expect(within(dialog).getByText('Вкажіть термін виконання.')).toBeInTheDocument();
    expect(api.issueResolution).not.toHaveBeenCalled();

    await userEvent.click(within(dialog).getByRole('combobox', { name: /Виконавець/ }));
    await userEvent.click((await screen.findAllByRole('option', { name: 'Фейковий Фейк', hidden: true }))[0]);
    // archived members are not offered
    expect(screen.queryByRole('option', { name: 'Архівний Фейк', hidden: true })).toBeNull();
    await userEvent.type(within(dialog).getByRole('textbox', { name: /Доручення/ }), 'Зробити');
    await userEvent.type(within(dialog).getByLabelText(/Термін/), '2099-01-01');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Видати' }));
    await waitFor(() => expect(api.issueResolution).toHaveBeenCalled());
    expect(vi.mocked(api.issueResolution).mock.calls[0]).toEqual([
      't1',
      { executorId: 'bob', coExecutorIds: [], instruction: 'Зробити', dueDate: '2099-01-01' },
    ]);
  });

  it('runs the executor flow: start, then submit requires a completion note', async () => {
    vi.mocked(api.listTaskResolutions).mockResolvedValue(page([resolution({ state: 'NEW' })]));
    vi.mocked(api.simpleResolutionAction).mockResolvedValue(detail({ state: 'IN_PROGRESS' }));
    const { unmount } = renderPanel(userOf('bob'));
    await userEvent.click(await screen.findByRole('button', { name: 'Взяти в роботу' }));
    await waitFor(() => expect(vi.mocked(api.simpleResolutionAction).mock.calls[0]).toEqual(['r1', 'start']));
    unmount();

    vi.mocked(api.listTaskResolutions).mockResolvedValue(page([resolution({ state: 'IN_PROGRESS' })]));
    vi.mocked(api.submitResolution).mockResolvedValue(detail({ state: 'SUBMITTED' }));
    renderPanel(userOf('bob'));
    await userEvent.click(await screen.findByRole('button', { name: 'Здати результат' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Здати' }));
    expect(await within(dialog).findByText("Це поле обов'язкове.")).toBeInTheDocument();
    expect(api.submitResolution).not.toHaveBeenCalled();
    await userEvent.type(within(dialog).getByRole('textbox', { name: /Коментар про виконання/ }), 'Готово');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Здати' }));
    await waitFor(() => expect(vi.mocked(api.submitResolution).mock.calls[0]).toEqual(['r1', 'Готово']));
  });

  it('runs the author flow: accept, and rework requires a comment', async () => {
    vi.mocked(api.listTaskResolutions).mockResolvedValue(page([resolution({ state: 'SUBMITTED' })]));
    vi.mocked(api.simpleResolutionAction).mockResolvedValue(detail({ state: 'ACCEPTED' }));
    vi.mocked(api.reworkResolution).mockResolvedValue(detail({ state: 'REWORK' }));
    renderPanel(userOf('alice'));
    await userEvent.click(await screen.findByRole('button', { name: 'Прийняти результат' }));
    await waitFor(() => expect(vi.mocked(api.simpleResolutionAction).mock.calls[0]).toEqual(['r1', 'accept']));

    await userEvent.click(screen.getByRole('button', { name: 'На доопрацювання' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Відправити' }));
    expect(await within(dialog).findByText("Це поле обов'язкове.")).toBeInTheDocument();
    expect(api.reworkResolution).not.toHaveBeenCalled();
    await userEvent.type(within(dialog).getByRole('textbox', { name: /Що потрібно доопрацювати/ }), 'Додати дані');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Відправити' }));
    await waitFor(() => expect(vi.mocked(api.reworkResolution).mock.calls[0]).toEqual(['r1', 'Додати дані']));
  });

  it('surfaces a server rejection in the dialog', async () => {
    vi.mocked(api.listTaskResolutions).mockResolvedValue(page([resolution({ state: 'SUBMITTED' })]));
    vi.mocked(api.cancelResolution).mockRejectedValue(new ApiProblemError(422, 'RESOLUTION_TRANSITION_NOT_ALLOWED'));
    renderPanel(userOf('alice'));
    await userEvent.click(await screen.findByRole('button', { name: 'Скасувати резолюцію' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Скасувати резолюцію' }));
    expect(await within(dialog).findByText('Ця дія недоступна для резолюції в поточному стані.')).toBeInTheDocument();
  });

  it('redirects and shows the due-date warning', async () => {
    vi.mocked(api.listTaskResolutions).mockResolvedValue(page([resolution({ state: 'IN_PROGRESS' })]));
    vi.mocked(api.redirectResolution).mockResolvedValue({ child: detail({ id: 'r2' }), warnings: ['RESOLUTION_CHILD_DUE_AFTER_PARENT'] });
    renderPanel(userOf('bob', ['resolutions.redirect']));
    await userEvent.click(await screen.findByRole('button', { name: 'Переадресувати' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByRole('combobox', { name: /Виконавець/ }));
    // the current executor is not offered as the redirect target
    expect(screen.queryByRole('option', { name: 'Фейковий Фейк', hidden: true })).toBeNull();
    await userEvent.click(await screen.findByRole('option', { name: 'Тестова Фейк', hidden: true }));
    await userEvent.type(within(dialog).getByRole('textbox', { name: /Доручення/ }), 'Далі');
    await userEvent.type(within(dialog).getByLabelText(/Термін/), '2099-01-01');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Переадресувати' }));
    await waitFor(() =>
      expect(vi.mocked(api.redirectResolution).mock.calls[0]).toEqual(['r1', { executorId: 'carol', instruction: 'Далі', dueDate: '2099-01-01' }]),
    );
    expect(await screen.findByText('Термін нової резолюції пізніший за термін початкової.')).toBeInTheDocument();
  });

  it('shows the history timeline with the completion note on demand', async () => {
    vi.mocked(api.listTaskResolutions).mockResolvedValue(page([resolution({ state: 'SUBMITTED' })]));
    vi.mocked(api.getResolution).mockResolvedValue(
      detail({
        state: 'SUBMITTED',
        completionNote: 'Усе зроблено',
        history: [
          { actorId: 'alice', from: null, to: 'NEW', comment: '', at: '2031-01-01T10:00:00Z' },
          { actorId: 'bob', from: 'IN_PROGRESS', to: 'SUBMITTED', comment: 'Усе зроблено', at: '2031-01-02T10:00:00Z' },
        ],
      }),
    );
    renderPanel(userOf('zed'));
    await userEvent.click(await screen.findByRole('button', { name: 'Історія' }));
    expect(await screen.findByText('Історія резолюції')).toBeInTheDocument();
    expect(screen.getAllByText('Усе зроблено').length).toBeGreaterThan(0);
    expect(screen.getByText('Нова')).toBeInTheDocument();
  });
});
