import { NOTIFICATION_TYPES, type AppNotification } from './notificationsApi';

const str = (v: unknown): string | null => (typeof v === 'string' && v !== '' ? v : null);

/**
 * In-app route a notification leads to, or null when the target is unknown. Task notifications open the task panel
 * (a mention on its comments tab); any other target that carries a `taskId` in its payload (stored notifications of
 * retired types) opens that task.
 */
export function notificationLink(n: Pick<AppNotification, 'type' | 'targetType' | 'targetId' | 'payload'>): string | null {
  if (!n.targetId) return null;
  if (n.targetType === 'TASK') {
    return n.type === 'MENTION' ? `/tasks/${n.targetId}?tab=comments` : `/tasks/${n.targetId}`;
  }
  const taskId = str(n.payload.taskId);
  return taskId ? `/tasks/${taskId}` : null;
}

/** i18n key of the type label; unknown types fall back to a generic label. */
export function notificationTypeKey(type: string): string {
  return (NOTIFICATION_TYPES as readonly string[]).includes(type) ? `notifications.type.${type}` : 'notifications.type.OTHER';
}

/** Caps the badge text so it fits a small circle. */
export function formatBadgeCount(count: number): string {
  return count > 99 ? '99+' : String(count);
}
