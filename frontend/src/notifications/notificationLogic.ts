import { NOTIFICATION_TYPES, type AppNotification } from './notificationsApi';

const str = (v: unknown): string | null => (typeof v === 'string' && v !== '' ? v : null);

/**
 * In-app route a notification leads to, or null when the target is unknown.
 * Resolutions live on their task page; if the payload has no task id, the resolver route looks it up.
 */
export function notificationLink(n: Pick<AppNotification, 'type' | 'targetType' | 'targetId' | 'payload'>): string | null {
  if (!n.targetId) return null;
  const taskId = str(n.payload.taskId);
  if (n.targetType === 'RESOLUTION') {
    return taskId ? `/tasks/${taskId}?tab=resolutions` : `/resolutions/${n.targetId}`;
  }
  if (n.targetType === 'TASK') {
    return `/tasks/${n.targetId}?tab=${n.type === 'MENTION' ? 'comments' : 'resolutions'}`;
  }
  return null;
}

/** i18n key of the type label; unknown types fall back to a generic label. */
export function notificationTypeKey(type: string): string {
  return (NOTIFICATION_TYPES as readonly string[]).includes(type) ? `notifications.type.${type}` : 'notifications.type.OTHER';
}

/** Caps the badge text so it fits a small circle. */
export function formatBadgeCount(count: number): string {
  return count > 99 ? '99+' : String(count);
}
