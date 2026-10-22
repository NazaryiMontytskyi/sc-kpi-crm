import { api, type components } from '../api/client';
import { toProblemError } from '../api/problem';

type Schemas = components['schemas'];

/** Known notification types; the backend may add more (e.g. due-date reminders), so the field is a plain string. */
export const NOTIFICATION_TYPES = [
  'RESOLUTION_ISSUED',
  'RESOLUTION_REDIRECTED',
  'RESOLUTION_SUBMITTED',
  'RESOLUTION_ACCEPTED',
  'RESOLUTION_REWORK',
  'RESOLUTION_CHILD_ACCEPTED',
  'RESOLUTION_DUE_SOON',
  'RESOLUTION_OVERDUE',
  'MENTION',
  'POLL_OPENED',
  'POLL_CLOSED',
  'HANDOVER_STARTED',
] as const;

/** The generated types mark every field optional; this is the shape the UI relies on. */
export interface AppNotification {
  id: string;
  type: string;
  targetType: string;
  targetId: string;
  payload: Record<string, unknown>;
  text: string;
  read: boolean;
  readAt: string | null;
  createdAt: string;
}

export interface NotificationPage {
  items: AppNotification[];
  page: number;
  totalPages: number;
  totalElements: number;
}

export const NOTIFICATIONS_PAGE_SIZE = 20;
/** Number of items shown in the bell dropdown. */
export const BELL_PAGE_SIZE = 6;

function toNotification(n: Schemas['NotificationResponse']): AppNotification {
  return {
    id: n.id ?? '',
    type: n.type ?? '',
    targetType: n.targetType ?? '',
    targetId: n.targetId ?? '',
    payload: n.payload ?? {},
    text: n.text ?? '',
    read: n.read === true || Boolean(n.readAt),
    readAt: n.readAt ?? null,
    createdAt: n.createdAt ?? '',
  };
}

export async function listNotifications(page: number, size: number, unread?: boolean): Promise<NotificationPage> {
  const { data, error, response } = await api.GET('/api/v1/notifications', {
    params: { query: { page, size, ...(unread ? { unread: true } : {}) } },
  });
  if (!data) throw toProblemError(response, error);
  return {
    items: (data.content ?? []).map(toNotification),
    page: data.page ?? 0,
    totalPages: data.totalPages ?? 0,
    totalElements: data.totalElements ?? 0,
  };
}

export async function getUnreadCount(): Promise<number> {
  const { data, error, response } = await api.GET('/api/v1/notifications/unread-count');
  if (!data) throw toProblemError(response, error);
  return data.count ?? 0;
}

export async function markNotificationRead(id: string): Promise<AppNotification> {
  const { data, error, response } = await api.POST('/api/v1/notifications/{id}/read', { params: { path: { id } } });
  if (!data) throw toProblemError(response, error);
  return toNotification(data);
}

export async function markAllNotificationsRead(): Promise<number> {
  const { data, error, response } = await api.POST('/api/v1/notifications/read-all');
  if (!data) throw toProblemError(response, error);
  return data.updated ?? 0;
}
