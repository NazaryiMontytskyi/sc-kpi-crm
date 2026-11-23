import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { configure, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import i18n, { changeLanguage } from '../i18n';
import { renderWithProviders } from '../test/render';
import { NotificationBell } from './NotificationBell';
import { formatBadgeCount, notificationLink, notificationTypeKey } from './notificationLogic';
import * as api from './notificationsApi';
import { NotificationsPage } from './NotificationsPage';

vi.mock('./notificationsApi', async (orig) => ({
  ...(await orig<typeof api>()),
  listNotifications: vi.fn(),
  getUnreadCount: vi.fn(),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
}));

configure({ asyncUtilTimeout: 5000 });

const note = (id: string, over: Partial<api.AppNotification> = {}): api.AppNotification => ({
  id,
  type: 'TASK_ASSIGNED',
  targetType: 'TASK',
  targetId: 't1',
  payload: { taskId: 't1' },
  text: `Текст ${id}`,
  read: false,
  readAt: null,
  createdAt: '2031-03-05T10:00:00Z',
  ...over,
});
const page = (items: api.AppNotification[]): api.NotificationPage => ({ items, page: 0, totalPages: 1, totalElements: items.length });

function Where() {
  const loc = useLocation();
  return <div data-testid="where">{loc.pathname + loc.search}</div>;
}

function renderUi(ui: React.ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderWithProviders(
    <QueryClientProvider client={queryClient}>
      <Routes>
        <Route path="*" element={<>{ui}<Where /></>} />
      </Routes>
    </QueryClientProvider>,
  );
}

beforeEach(async () => {
  vi.resetAllMocks();
  await changeLanguage('uk');
});

/** A stored notification of a retired type (deprecated backend enum value), as it may still be in the database. */
const LEGACY = note('old', { type: 'RESOLUTION_ISSUED', targetType: 'RESOLUTION', targetId: 'r1', payload: { taskId: 't7' }, text: 'Старий текст' });

const NEW_TYPES = ['TASK_ASSIGNED', 'TASK_UNASSIGNED', 'TASK_DEADLINE_CHANGED', 'TASK_DUE_SOON', 'TASK_OVERDUE', 'SUBTASK_ADDED', 'TASK_STATUS_CHANGED'];

describe('notification logic', () => {
  it('opens tasks in the panel and mentions on the comments tab', () => {
    expect(notificationLink(note('a'))).toBe('/tasks/t1');
    expect(notificationLink(note('a', { type: 'SUBTASK_ADDED', targetId: 's1', payload: { taskId: 't1', subtaskId: 's1' } }))).toBe('/tasks/s1');
    expect(notificationLink(note('a', { type: 'MENTION', targetId: 't9', payload: { commentId: 'c' } }))).toBe('/tasks/t9?tab=comments');
    expect(notificationLink(note('a', { targetType: 'POLL', payload: {} }))).toBeNull();
    expect(notificationLink(note('a', { targetId: '' }))).toBeNull();
  });

  it('opens the task of a stored notification of a retired type', () => {
    expect(notificationLink(LEGACY)).toBe('/tasks/t7');
    expect(notificationLink({ ...LEGACY, payload: {} })).toBeNull();
  });

  it('labels every new task type and falls back to a generic label for retired or unknown ones', () => {
    for (const type of NEW_TYPES) expect(notificationTypeKey(type)).toBe(`notifications.type.${type}`);
    expect(notificationTypeKey(LEGACY.type)).toBe('notifications.type.OTHER');
    expect(notificationTypeKey('SOMETHING_NEW')).toBe('notifications.type.OTHER');
    expect(formatBadgeCount(5)).toBe('5');
    expect(formatBadgeCount(150)).toBe('99+');
  });

  it('has a distinct uk and en label for every new type', async () => {
    for (const lang of ['uk', 'en'] as const) {
      await changeLanguage(lang);
      const labels = NEW_TYPES.map((type) => i18n.t(notificationTypeKey(type)));
      labels.forEach((label, i) => expect(label).not.toBe(`notifications.type.${NEW_TYPES[i]}`));
      expect(new Set(labels).size).toBe(NEW_TYPES.length);
    }
    await changeLanguage('uk');
  });
});

describe('NotificationBell', () => {
  it('shows the unread counter and updates it after marking all read, without reload', async () => {
    let unread = 2;
    vi.mocked(api.getUnreadCount).mockImplementation(() => Promise.resolve(unread));
    vi.mocked(api.listNotifications).mockImplementation(() => Promise.resolve(page([note('n1'), note('n2')])));
    vi.mocked(api.markAllNotificationsRead).mockImplementation(() => {
      unread = 0;
      return Promise.resolve(2);
    });
    renderUi(<NotificationBell />);
    expect(await screen.findByText('2')).toBeInTheDocument();

    await userEvent.click(screen.getByTestId('bell-slot'));
    expect(await screen.findByText('Текст n1')).toBeInTheDocument();
    await userEvent.click(screen.getByText('Позначити всі прочитаними'));
    await waitFor(() => expect(screen.queryByText('2')).not.toBeInTheDocument());
    expect(api.markAllNotificationsRead).toHaveBeenCalled();
  });

  it('opening an item marks it read, decrements the counter and navigates to the target', async () => {
    let unread = 1;
    vi.mocked(api.getUnreadCount).mockImplementation(() => Promise.resolve(unread));
    vi.mocked(api.listNotifications).mockImplementation(() => Promise.resolve(page([note('n1')])));
    vi.mocked(api.markNotificationRead).mockImplementation(() => {
      unread = 0;
      return Promise.resolve(note('n1', { read: true }));
    });
    renderUi(<NotificationBell />);
    await userEvent.click(screen.getByTestId('bell-slot'));
    await userEvent.click(await screen.findByText('Текст n1'));
    expect(api.markNotificationRead).toHaveBeenCalledWith('n1');
    expect(screen.getByTestId('where')).toHaveTextContent('/tasks/t1');
    await waitFor(() => expect(api.getUnreadCount).toHaveBeenCalledTimes(2));
  });

  it('opens a stored notification of a retired type at its task', async () => {
    vi.mocked(api.getUnreadCount).mockResolvedValue(1);
    vi.mocked(api.listNotifications).mockResolvedValue(page([LEGACY]));
    vi.mocked(api.markNotificationRead).mockResolvedValue({ ...LEGACY, read: true });
    renderUi(<NotificationBell />);
    await userEvent.click(screen.getByTestId('bell-slot'));
    await userEvent.click(await screen.findByText('Старий текст'));
    expect(screen.getByTestId('where')).toHaveTextContent('/tasks/t7');
  });
});

describe('NotificationsPage', () => {
  it('lists notifications, filters unread in en and marks all read', async () => {
    await changeLanguage('en');
    vi.mocked(api.getUnreadCount).mockResolvedValue(1);
    vi.mocked(api.listNotifications).mockImplementation((_p, _s, unread) =>
      Promise.resolve(page(unread ? [note('n1')] : [note('n1'), note('n2', { read: true, type: 'MENTION' })])),
    );
    vi.mocked(api.markAllNotificationsRead).mockResolvedValue(1);
    renderUi(<NotificationsPage />);
    expect(await screen.findByText('Текст n2')).toBeInTheDocument();
    expect(screen.getByText('Mentioned in a comment')).toBeInTheDocument();

    await userEvent.click(screen.getByLabelText('Unread only'));
    await waitFor(() => expect(screen.queryByText('Текст n2')).not.toBeInTheDocument());
    expect(api.listNotifications).toHaveBeenLastCalledWith(0, expect.any(Number), true);

    await userEvent.click(screen.getByRole('button', { name: 'Mark all as read' }));
    await waitFor(() => expect(api.markAllNotificationsRead).toHaveBeenCalled());
    await changeLanguage('uk');
  });
});

describe('notification rendering', () => {
  const item = (text: string) => screen.getAllByTestId('notification-item').find((el) => el.textContent?.includes(text))!;

  it('renders the new task types and a retired type in uk and en', async () => {
    vi.mocked(api.getUnreadCount).mockResolvedValue(0);
    vi.mocked(api.listNotifications).mockResolvedValue(
      page([note('n1', { read: true }), note('n2', { type: 'SUBTASK_ADDED', read: true }), { ...LEGACY, read: true }]),
    );
    renderUi(<NotificationsPage />);
    expect(await screen.findByText('Старий текст')).toBeInTheDocument();
    expect(item('Текст n1')).toHaveTextContent('Вас призначено до задачі');
    expect(item('Текст n2')).toHaveTextContent('Додано підзадачу');
    expect(item('Старий текст')).toHaveTextContent('Сповіщення');

    await changeLanguage('en');
    await waitFor(() => expect(item('Текст n1')).toHaveTextContent('Assigned to a task'));
    expect(item('Текст n2')).toHaveTextContent('Subtask added');
    expect(item('Старий текст')).toHaveTextContent('Notification');
    await changeLanguage('uk');
  });
});
