import { useNavigate } from 'react-router-dom';
import { notificationLink } from './notificationLogic';
import type { AppNotification } from './notificationsApi';
import { useNotificationMutations } from './useNotifications';

/** Marks the notification read (when unread) and navigates to its target. */
export function useOpenNotification(onNavigate?: () => void) {
  const navigate = useNavigate();
  const { markRead } = useNotificationMutations();
  return (item: AppNotification) => {
    if (!item.read) markRead.mutate(item.id);
    const link = notificationLink(item);
    if (link) {
      onNavigate?.();
      navigate(link);
    }
  };
}
