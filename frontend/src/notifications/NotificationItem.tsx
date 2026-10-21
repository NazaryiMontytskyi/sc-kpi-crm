import { Badge, Group, Stack, Text, UnstyledButton } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { formatKyivDateTime } from '../audit/kyivTime';
import { notificationTypeKey } from './notificationLogic';
import type { AppNotification } from './notificationsApi';

/** One notification row; the whole row is a 44px+ touch target that opens the target. */
export function NotificationItem({ item, onOpen }: { item: AppNotification; onOpen: (item: AppNotification) => void }) {
  const { t, i18n } = useTranslation();
  return (
    <UnstyledButton
      onClick={() => onOpen(item)}
      w="100%"
      mih={44}
      p="xs"
      data-testid="notification-item"
      data-unread={!item.read}
      style={{ borderRadius: 'var(--mantine-radius-sm)' }}
      bg={item.read ? undefined : 'var(--mantine-primary-color-light)'}
    >
      <Stack gap={2}>
        <Group gap="xs" wrap="nowrap" justify="space-between">
          <Text size="xs" c="dimmed" truncate>
            {t(notificationTypeKey(item.type))}
          </Text>
          {!item.read && (
            <Badge size="xs" variant="filled">
              {t('notifications.unread')}
            </Badge>
          )}
        </Group>
        <Text size="sm" fw={item.read ? 400 : 600} style={{ overflowWrap: 'anywhere' }}>
          {item.text}
        </Text>
        <Text size="xs" c="dimmed">
          {formatKyivDateTime(item.createdAt, i18n.language)}
        </Text>
      </Stack>
    </UnstyledButton>
  );
}
