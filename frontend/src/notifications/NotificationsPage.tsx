import { Alert, Button, Card, Group, Loader, Stack, Switch, Text, Title } from '@mantine/core';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { usePageTitle } from '../layout/pageTitle';
import { NotificationItem } from './NotificationItem';
import { useNotificationMutations, useNotificationPages, useUnreadCount } from './useNotifications';
import { useOpenNotification } from './useOpenNotification';

export function NotificationsPage() {
  const { t } = useTranslation();
  usePageTitle('nav.notifications');
  const [unreadOnly, setUnreadOnly] = useState(false);
  const query = useNotificationPages(unreadOnly);
  const unread = useUnreadCount();
  const { markAllRead } = useNotificationMutations();
  const open = useOpenNotification();
  const items = query.data?.pages.flatMap((p) => p.items) ?? [];

  return (
    <Stack gap="md">
      <Group justify="space-between">
        <Title order={1} size="h2">
          {t('notifications.title')}
        </Title>
        <Button variant="light" mih={44} disabled={(unread.data ?? 0) === 0 || markAllRead.isPending} onClick={() => markAllRead.mutate()}>
          {t('notifications.markAllRead')}
        </Button>
      </Group>
      <Switch label={t('notifications.unreadOnly')} checked={unreadOnly} onChange={(e) => setUnreadOnly(e.currentTarget.checked)} />
      {markAllRead.isError && <Alert color="red">{t('notifications.actionFailed')}</Alert>}
      {query.isLoading && <Loader size="sm" />}
      {query.isError && <Alert color="red">{t('notifications.loadFailed')}</Alert>}
      {query.data && items.length === 0 && <Text c="dimmed">{t(unreadOnly ? 'notifications.emptyUnread' : 'notifications.empty')}</Text>}
      {items.length > 0 && (
        <Card withBorder padding="xs">
          <Stack gap={2}>
            {items.map((n) => (
              <NotificationItem key={n.id} item={n} onOpen={open} />
            ))}
          </Stack>
        </Card>
      )}
      {query.hasNextPage && (
        <Button variant="default" mih={44} loading={query.isFetchingNextPage} onClick={() => void query.fetchNextPage()}>
          {t('notifications.loadMore')}
        </Button>
      )}
    </Stack>
  );
}
