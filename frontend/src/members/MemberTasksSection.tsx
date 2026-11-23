import { Alert, Anchor, Badge, Button, Card, Group, Loader, Stack, Text, Title } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { usePermission } from '../auth/CurrentUser';
import type { MemberTask } from '../tasks/memberTasksApi';
import { TASKS_VIEW } from '../tasks/taskLogic';
import { useTaskLink } from '../tasks/trackerNav';
import { useMemberTaskCounts, useMemberTasks } from '../tasks/useMemberTasks';
import { formatDate } from './memberFormat';

const TOUCH = 44;

function Counter({ label, value, color, testId }: { label: string; value: number; color?: string; testId: string }) {
  return (
    <Card withBorder padding="sm" style={{ flex: '1 1 90px' }} data-testid={testId}>
      <Text size="xl" fw={700} c={color}>
        {value}
      </Text>
      <Text size="sm" c="dimmed">
        {label}
      </Text>
    </Card>
  );
}

function Item({ task }: { task: MemberTask }) {
  const { t } = useTranslation();
  const linkTo = useTaskLink();
  return (
    <Card withBorder padding="sm" data-testid="member-task" style={task.overdue ? { borderColor: 'var(--mantine-color-red-6)' } : undefined}>
      <Stack gap={6}>
        <Anchor
          component={Link}
          {...linkTo(task.id)}
          fw={600}
          style={{ overflowWrap: 'anywhere', minHeight: TOUCH, display: 'inline-flex', alignItems: 'center' }}
        >
          {task.title}
        </Anchor>
        <Group gap="xs" wrap="wrap">
          <Badge variant="light" color="gray" size="sm" tt="none">
            {task.statusName}
          </Badge>
          {task.parentId && (
            <Badge variant="outline" color="gray" size="sm">
              {t('tasks.panel.subtaskBadge')}
            </Badge>
          )}
          {task.overdue && (
            <Badge color="red" variant="filled" size="sm">
              {t('tasks.panel.overdue')}
            </Badge>
          )}
          <Text size="sm" c={task.overdue ? 'red' : 'dimmed'} fw={task.overdue ? 600 : undefined}>
            {task.deadline ? t('members.tasks.deadline', { date: formatDate(task.deadline) }) : t('members.tasks.noDeadline')}
          </Text>
        </Group>
      </Stack>
    </Card>
  );
}

/**
 * Open tasks of a member (those they are an active assignee of) with counters: open, overdue and done (spec RR-5).
 * Visible to everyone with `tasks.view`; a task opens in the tracker panel.
 */
export function MemberTasksSection({ userId }: { userId: string }) {
  const { t } = useTranslation();
  const allowed = usePermission(TASKS_VIEW);
  const counts = useMemberTaskCounts(userId, allowed);
  const list = useMemberTasks(userId, allowed);
  if (!allowed) return null;

  const items = (list.data?.pages ?? []).flatMap((p) => p.items);
  const overdue = counts.data?.overdue ?? 0;
  return (
    <Stack gap="sm" data-testid="member-tasks">
      <Title order={2} size="h3">
        {t('members.tasks.title')}
      </Title>
      {counts.isError ? (
        <Alert color="red">{t('members.tasks.loadFailed')}</Alert>
      ) : (
        <Group gap="xs" wrap="wrap">
          <Counter testId="count-active" label={t('members.tasks.counts.active')} value={counts.data?.active ?? 0} />
          <Counter testId="count-overdue" label={t('members.tasks.counts.overdue')} value={overdue} color={overdue > 0 ? 'red' : undefined} />
          <Counter testId="count-done" label={t('members.tasks.counts.done')} value={counts.data?.done ?? 0} />
        </Group>
      )}
      {list.isLoading && <Loader size="sm" />}
      {list.isError && <Alert color="red">{t('members.tasks.loadFailed')}</Alert>}
      {list.isSuccess && items.length === 0 && (
        <Text c="dimmed" size="sm">
          {t('members.tasks.empty')}
        </Text>
      )}
      {items.length > 0 && (
        <Stack gap="xs">
          {items.map((task) => (
            <Item key={task.id} task={task} />
          ))}
        </Stack>
      )}
      {list.hasNextPage && (
        <Button variant="default" mih={TOUCH} loading={list.isFetchingNextPage} onClick={() => void list.fetchNextPage()}>
          {t('members.tasks.loadMore')}
        </Button>
      )}
    </Stack>
  );
}
