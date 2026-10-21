import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  BELL_PAGE_SIZE,
  getUnreadCount,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  NOTIFICATIONS_PAGE_SIZE,
} from './notificationsApi';

export const NOTIFICATIONS_KEY = ['notifications'] as const;
/** The unread counter is refreshed this often; push delivery is a later stage. */
export const UNREAD_POLL_MS = 30_000;

export function useUnreadCount() {
  return useQuery({
    queryKey: [...NOTIFICATIONS_KEY, 'unread-count'],
    queryFn: getUnreadCount,
    refetchInterval: UNREAD_POLL_MS,
  });
}

/** Latest notifications for the bell dropdown; only fetched while the dropdown is open. */
export function useLatestNotifications(enabled: boolean) {
  return useQuery({
    queryKey: [...NOTIFICATIONS_KEY, 'latest'],
    queryFn: () => listNotifications(0, BELL_PAGE_SIZE),
    enabled,
  });
}

export function useNotificationPages(unreadOnly: boolean) {
  return useInfiniteQuery({
    queryKey: [...NOTIFICATIONS_KEY, 'list', unreadOnly],
    queryFn: ({ pageParam }) => listNotifications(pageParam, NOTIFICATIONS_PAGE_SIZE, unreadOnly),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
  });
}

/** Mutations refresh the counter and every list so the bell updates without a reload. */
export function useNotificationMutations() {
  const qc = useQueryClient();
  const onSuccess = () => qc.invalidateQueries({ queryKey: NOTIFICATIONS_KEY });
  return {
    markRead: useMutation({ mutationFn: (id: string) => markNotificationRead(id), onSuccess }),
    markAllRead: useMutation({ mutationFn: markAllNotificationsRead, onSuccess }),
  };
}
