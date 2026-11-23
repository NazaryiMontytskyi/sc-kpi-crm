import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { configure, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CurrentUserProvider, STUB_AUTH_STATE, type AuthState } from '../auth/CurrentUser';
import { changeLanguage } from '../i18n';
import * as commentsApi from './commentsApi';
import * as memberApi from '../members/memberApi';
import { renderWithProviders } from '../test/render';
import { adjacentTaskLink, isEditableTarget, shouldCloseOnEscape } from './keyboardNav';
import { TaskPanelRoute } from './TaskPanel';
import { TasksPage } from './TasksPage';
import * as api from './tasksApi';
import { keepsListFocus, listFocusNavigation } from './trackerNav';

/** INC-083: keyboard support of the tracker (spec UI-7): Esc closes the side panel, arrows move in the list. */

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

const TODO: api.SpaceStatus = { id: 's1', name: 'Фейкове нове', category: 'TODO', position: 0, archived: false };
const SPACE: api.Space = {
  id: 'sp1',
  name: 'Фейковий простір',
  orgUnitId: null,
  general: true,
  archived: false,
  statuses: [TODO],
  customFields: [],
};

const summary = (id: string, title: string): api.TaskSummary => ({
  id,
  spaceId: 'sp1',
  title,
  statusId: 's1',
  priority: 'NORMAL',
  deadline: null,
  labels: [],
  attachmentCount: 0,
  authorId: 'creator',
  archived: false,
  createdAt: '2031-01-01T10:00:00Z',
  parentId: null,
  version: 1,
  assigneeIds: [],
});

const TASKS = [summary('t1', 'Перша фейкова'), summary('t2', 'Друга фейкова'), summary('t3', 'Третя фейкова')];

const detail = (task: api.TaskSummary): api.Task => ({
  ...task,
  description: null,
  customFieldValues: {},
  relatedTaskIds: [],
  attachmentIds: [],
  attachments: [],
  issuedAt: '2031-01-01T10:00:00Z',
  creatorId: 'creator',
  assignees: [],
  subtasks: [],
  artifacts: [],
  openSubtaskCount: 0,
  suggestClose: false,
});

const AUTH: AuthState = {
  ...STUB_AUTH_STATE,
  user: { id: 'u1', displayName: 'Тестовий користувач', isAdmin: false, permissions: ['tasks.view'] },
};

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{`${location.pathname}${location.search}`}</output>;
}

function renderTracker(route: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderWithProviders(
    <QueryClientProvider client={queryClient}>
      <CurrentUserProvider value={AUTH}>
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
const cardLink = (title: string) => within(screen.getByTestId('task-list')).getByRole('link', { name: title });

beforeEach(async () => {
  vi.clearAllMocks();
  Element.prototype.scrollIntoView = vi.fn();
  await changeLanguage('uk');
  setViewport(1280);
  vi.mocked(api.listSpaces).mockResolvedValue([SPACE]);
  vi.mocked(api.getSpace).mockResolvedValue(SPACE);
  vi.mocked(api.listTasks).mockResolvedValue({ items: TASKS, page: 0, totalPages: 1, totalElements: 3 });
  vi.mocked(api.getTask).mockImplementation(async (id) => detail(TASKS.find((t) => t.id === id) ?? TASKS[0]));
  vi.mocked(api.listSavedFilters).mockResolvedValue([]);
  vi.mocked(commentsApi.listComments).mockResolvedValue({ items: [], page: 0, totalPages: 0, totalElements: 0 });
  vi.mocked(memberApi.listMembers).mockResolvedValue([]);
});

// ---- pure rules ---------------------------------------------------------------------------------------------

describe('keyboard rules', () => {
  it('treats fields, editors and pickers as owners of their keys', () => {
    const input = document.createElement('input');
    const area = document.createElement('textarea');
    const editor = document.createElement('div');
    editor.setAttribute('contenteditable', 'true');
    const inner = document.createElement('p');
    editor.appendChild(inner);
    document.body.appendChild(editor);
    const combo = document.createElement('div');
    combo.setAttribute('role', 'combobox');
    const link = document.createElement('a');
    expect(isEditableTarget(input)).toBe(true);
    expect(isEditableTarget(area)).toBe(true);
    expect(isEditableTarget(inner)).toBe(true);
    expect(isEditableTarget(combo)).toBe(true);
    expect(isEditableTarget(link)).toBe(false);
    expect(isEditableTarget(null)).toBe(false);
    editor.remove();
  });

  it('closes on a plain Escape only, never over a field or an open modal dialog', () => {
    const body = document.body;
    const esc = (over: Partial<KeyboardEvent> = {}) =>
      ({ key: 'Escape', defaultPrevented: false, isComposing: false, target: body, ...over }) as KeyboardEvent;
    expect(shouldCloseOnEscape(esc())).toBe(true);
    expect(shouldCloseOnEscape(esc({ key: 'Enter' }))).toBe(false);
    expect(shouldCloseOnEscape(esc({ defaultPrevented: true }))).toBe(false);
    expect(shouldCloseOnEscape(esc({ isComposing: true }))).toBe(false);
    expect(shouldCloseOnEscape(esc({ target: document.createElement('input') }))).toBe(false);
    const modal = document.createElement('div');
    modal.setAttribute('role', 'dialog');
    modal.setAttribute('aria-modal', 'true');
    body.appendChild(modal);
    expect(shouldCloseOnEscape(esc())).toBe(false);
    modal.remove();
  });

  it('moves to the neighbour without wrapping around', () => {
    const links = [document.createElement('a'), document.createElement('a'), document.createElement('a')];
    expect(adjacentTaskLink(links, links[0], 1)).toBe(links[1]);
    expect(adjacentTaskLink(links, links[1], -1)).toBe(links[0]);
    expect(adjacentTaskLink(links, links[2], 1)).toBeNull();
    expect(adjacentTaskLink(links, links[0], -1)).toBeNull();
    expect(adjacentTaskLink(links, document.createElement('a'), 1)).toBeNull();
    expect(adjacentTaskLink(links, null, 1)).toBeNull();
  });

  it('marks arrow navigation so the panel leaves the focus in the list, keeping the tracker marker', () => {
    expect(listFocusNavigation({ fromTracker: true })).toEqual({ state: { fromTracker: true, listFocus: true }, replace: true });
    expect(listFocusNavigation(undefined)).toEqual({ state: { listFocus: true }, replace: true });
    expect(keepsListFocus({ listFocus: true })).toBe(true);
    expect(keepsListFocus({ fromTracker: true })).toBe(false);
    expect(keepsListFocus(null)).toBe(false);
  });
});

// ---- the tracker ------------------------------------------------------------------------------------------------

describe('Esc closes the side panel', () => {
  it('closes the panel and returns the focus to the card of the task', async () => {
    const user = userEvent.setup();
    renderTracker('/tasks?section=all');
    await user.click(await screen.findByRole('link', { name: 'Перша фейкова' }));
    const panel = await screen.findByRole('region', { name: 'Перша фейкова' });
    await within(panel).findByTestId('task-panel-body');

    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByTestId('task-panel')).toBeNull());
    expect(location()).toBe('/tasks?section=all');
    await waitFor(() => expect(cardLink('Перша фейкова')).toHaveFocus());
  });

  it('closes a deep-linked panel to the tracker', async () => {
    const user = userEvent.setup();
    renderTracker('/tasks/t2?section=all');
    await screen.findByRole('region', { name: 'Друга фейкова' });
    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByTestId('task-panel')).toBeNull());
    expect(location()).toBe('/tasks?section=all');
  });

  it('leaves the panel open when Esc is pressed in a text field', async () => {
    const user = userEvent.setup();
    renderTracker('/tasks/t1?section=all');
    await screen.findByRole('region', { name: 'Перша фейкова' });
    const sort = screen.getAllByLabelText('Сортування').find((el) => el.tagName === 'INPUT') as HTMLInputElement;
    sort.focus();
    await user.keyboard('{Escape}');
    expect(screen.getByTestId('task-panel')).toBeInTheDocument();
    expect(location()).toBe('/tasks/t1?section=all');
  });
});

describe('arrow keys move in the list', () => {
  it('moves the focus between task cards without opening them when no panel is open', async () => {
    const user = userEvent.setup();
    renderTracker('/tasks?section=all');
    await screen.findByRole('link', { name: 'Перша фейкова' });
    cardLink('Перша фейкова').focus();

    await user.keyboard('{ArrowDown}');
    expect(cardLink('Друга фейкова')).toHaveFocus();
    await user.keyboard('{ArrowDown}');
    expect(cardLink('Третя фейкова')).toHaveFocus();
    await user.keyboard('{ArrowDown}');
    expect(cardLink('Третя фейкова')).toHaveFocus();
    await user.keyboard('{ArrowUp}');
    expect(cardLink('Друга фейкова')).toHaveFocus();
    expect(location()).toBe('/tasks?section=all');
    expect(screen.queryByTestId('task-panel')).toBeNull();

    await user.keyboard('{Enter}');
    expect(await screen.findByRole('region', { name: 'Друга фейкова' })).toBeInTheDocument();
  });

  it('with the side panel open, the selection follows the focus and the focus stays in the list', async () => {
    const user = userEvent.setup();
    renderTracker('/tasks?section=all');
    await user.click(await screen.findByRole('link', { name: 'Перша фейкова' }));
    await screen.findByRole('region', { name: 'Перша фейкова' });
    cardLink('Перша фейкова').focus();

    await user.keyboard('{ArrowDown}');
    expect(await screen.findByRole('region', { name: 'Друга фейкова' })).toBeInTheDocument();
    expect(location()).toBe('/tasks/t2?section=all');
    expect(cardLink('Друга фейкова')).toHaveFocus();
    expect(cardLink('Друга фейкова')).toHaveAttribute('aria-current', 'true');

    await user.keyboard('{ArrowDown}');
    expect(await screen.findByRole('region', { name: 'Третя фейкова' })).toBeInTheDocument();
    expect(cardLink('Третя фейкова')).toHaveFocus();

    // Replaced entries: Esc still closes back to the plain tracker.
    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByTestId('task-panel')).toBeNull());
    expect(location()).toBe('/tasks?section=all');
    await waitFor(() => expect(cardLink('Третя фейкова')).toHaveFocus());
  });
});
