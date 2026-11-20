import { Anchor, Badge, Card, Group, Stack, Text } from '@mantine/core';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { formatDate } from '../members/memberFormat';
import { isOpenAndOverdue, priorityColor, SELECTED_CARD_STYLE, todayKyiv } from './taskLogic';
import type { Space, TaskSummary } from './tasksApi';
import { useTaskLink } from './trackerNav';

const TOUCH = 44;

interface TaskCardProps {
  task: TaskSummary;
  space: Space | undefined;
  selected?: boolean;
  /** Extra lines at the bottom of the card (e.g. the assignees in "Issued by me"). */
  children?: ReactNode;
}

/** A task in the tracker list or the "My tasks" overview; its title opens the task panel in place (spec UI-1). */
export function TaskCard({ task, space, selected = false, children }: TaskCardProps) {
  const { t } = useTranslation();
  const linkTo = useTaskLink();
  const status = space?.statuses.find((s) => s.id === task.statusId);
  const overdue = isOpenAndOverdue(task, todayKyiv(), status?.category);
  return (
    <Card withBorder padding="sm" component="article" data-testid="task-card" style={selected ? SELECTED_CARD_STYLE : undefined}>
      <Stack gap={6}>
        <Anchor
          component={Link}
          {...linkTo(task.id)}
          data-task-link={task.id}
          aria-current={selected ? 'true' : undefined}
          fw={600}
          style={{ overflowWrap: 'anywhere', display: 'inline-block', minHeight: TOUCH, paddingBlock: 10 }}
        >
          {task.title}
        </Anchor>
        <Group gap={6} wrap="wrap">
          {status && (
            <Badge variant="light" color="gray" size="sm">
              {status.name}
            </Badge>
          )}
          <Badge variant="light" color={priorityColor(task.priority)} size="sm">
            {t(`tasks.priority.${task.priority}`)}
          </Badge>
          {task.parentId && (
            <Badge variant="outline" color="gray" size="sm">
              {t('tasks.panel.subtaskBadge')}
            </Badge>
          )}
          {task.archived && (
            <Badge variant="outline" color="gray" size="sm">
              {t('tasks.archivedBadge')}
            </Badge>
          )}
          {space && (
            <Text size="xs" c="dimmed">
              {space.name}
            </Text>
          )}
        </Group>
        {task.deadline && (
          <Text size="sm" c={overdue ? 'red' : 'dimmed'}>
            {t('tasks.due', { date: formatDate(task.deadline) })}
            {overdue ? ` · ${t('tasks.overdue')}` : ''}
          </Text>
        )}
        {task.labels.length > 0 && (
          <Group gap={4}>
            {task.labels.map((l) => (
              <Badge key={l} variant="outline" color="gray" size="xs" tt="none">
                {l}
              </Badge>
            ))}
          </Group>
        )}
        {children}
      </Stack>
    </Card>
  );
}
