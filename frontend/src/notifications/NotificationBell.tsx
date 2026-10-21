import { ActionIcon, Alert, Anchor, Button, Group, Indicator, Loader, Popover, Stack, Text } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { NotificationItem } from './NotificationItem';
import { formatBadgeCount } from './notificationLogic';
import { useLatestNotifications, useNotificationMutations, useUnreadCount } from './useNotifications';
import { useOpenNotification } from './useOpenNotification';

function BellIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
      <path d="M18 8a6 6 0 1 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" />
      <path d="M13.7 21a2 2 0 0 1-3.4 0" />
    </svg>
  );
}

/** Header bell: polled unread counter plus a dropdown with the latest notifications. */
export function NotificationBell() {
  const { t } = useTranslation();
  const [opened, { toggle, close }] = useDisclosure(false);
  const unread = useUnreadCount();
  const latest = useLatestNotifications(opened);
  const { markAllRead } = useNotificationMutations();
  const open = useOpenNotification(close);
  const count = unread.data ?? 0;

  return (
    <Popover opened={opened} onChange={(o) => !o && close()} position="bottom-end" width="min(360px, 94vw)" withinPortal shadow="md">
      <Popover.Target>
        <Indicator label={formatBadgeCount(count)} disabled={count === 0} size={18} offset={6} color="red" data-testid="bell-badge">
          <ActionIcon
            variant="subtle"
            color="gray"
            size={44}
            onClick={toggle}
            aria-label={t('notifications.bellLabel', { count })}
            data-testid="bell-slot"
          >
            <BellIcon />
          </ActionIcon>
        </Indicator>
      </Popover.Target>
      <Popover.Dropdown p="xs">
        <Stack gap="xs">
          <Group justify="space-between" wrap="nowrap">
            <Text fw={600}>{t('notifications.title')}</Text>
            <Button
              variant="subtle"
              size="compact-md"
              mih={44}
              disabled={count === 0 || markAllRead.isPending}
              onClick={() => markAllRead.mutate()}
            >
              {t('notifications.markAllRead')}
            </Button>
          </Group>
          {latest.isLoading && <Loader size="sm" />}
          {latest.isError && <Alert color="red">{t('notifications.loadFailed')}</Alert>}
          {latest.data && latest.data.items.length === 0 && <Text c="dimmed">{t('notifications.empty')}</Text>}
          {latest.data?.items.map((n) => (
            <NotificationItem key={n.id} item={n} onOpen={open} />
          ))}
          <Anchor
            component={Link}
            to="/notifications"
            onClick={close}
            mih={44}
            style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}
          >
            {t('notifications.viewAll')}
          </Anchor>
        </Stack>
      </Popover.Dropdown>
    </Popover>
  );
}
