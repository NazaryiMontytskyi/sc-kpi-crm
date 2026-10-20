import { Alert, Anchor, Badge, Button, Card, Group, Loader, Pagination, Select, Stack, Text, Title } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { usePermission } from '../auth/CurrentUser';
import { usePageTitle } from '../layout/pageTitle';
import { formatDate } from '../members/memberFormat';
import { TaskFiltersDrawer } from './TaskFiltersDrawer';
import { TaskFormModal } from './TaskFormModal';
import {
  activeFilterCount,
  DEFAULT_QUERY,
  isOverdue,
  priorityColor,
  TASKS_ARCHIVE,
  TASKS_CREATE,
  taskErrorKey,
  todayKyiv,
} from './taskLogic';
import type { Space, SortDirection, TaskQuery, TaskSortField, TaskSummary } from './tasksApi';
import { useSpaceDetails, useSpaces, useTaskList } from './useTasks';

const SORTS: { value: `${TaskSortField}:${SortDirection}`; key: string }[] = [
  { value: 'createdAt:desc', key: 'newest' },
  { value: 'createdAt:asc', key: 'oldest' },
  { value: 'dueDate:asc', key: 'dueSoon' },
  { value: 'dueDate:desc', key: 'dueLate' },
  { value: 'title:asc', key: 'titleAsc' },
  { value: 'title:desc', key: 'titleDesc' },
  { value: 'updatedAt:desc', key: 'updated' },
];

export function TaskCard({ task, space }: { task: TaskSummary; space: Space | undefined }) {
  const { t } = useTranslation();
  const status = space?.statuses.find((s) => s.id === task.statusId);
  const overdue = isOverdue(task, todayKyiv());
  return (
    <Card withBorder padding="sm" component="article" data-testid="task-card">
      <Stack gap={6}>
        <Anchor component={Link} to={`/tasks/${task.id}`} fw={600} style={{ overflowWrap: 'anywhere' }}>
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
        {task.dueDate && (
          <Text size="sm" c={overdue ? 'red' : 'dimmed'}>
            {t('tasks.due', { date: formatDate(task.dueDate) })}
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
      </Stack>
    </Card>
  );
}

/** Task list: cards (readable at 375 px), sorting in the toolbar, every filter inside a drawer. */
export function TasksPage() {
  const { t } = useTranslation();
  usePageTitle('nav.tasks');
  const navigate = useNavigate();
  const canCreate = usePermission(TASKS_CREATE);
  const canSeeArchived = usePermission(TASKS_ARCHIVE);
  const [params] = useSearchParams();
  const [query, setQuery] = useState<TaskQuery>({ ...DEFAULT_QUERY, spaceId: params.get('space') ?? undefined });
  const [drawer, drawerHandlers] = useDisclosure(false);
  const [creating, setCreating] = useState(false);

  const spacesQuery = useSpaces(true);
  const spaces = spacesQuery.data ?? [];
  const list = useTaskList(query);
  const items = list.data?.items ?? [];
  const details = useSpaceDetails([...new Set(items.map((i) => i.spaceId))]);
  const filterCount = activeFilterCount(query);

  return (
    <Stack gap="md">
      <Group justify="space-between" align="center" gap="sm">
        <Title order={1} size="h2">
          {t('tasks.title')}
        </Title>
        {canCreate && (
          <Button mih={44} onClick={() => setCreating(true)}>
            {t('tasks.create')}
          </Button>
        )}
      </Group>

      <Group gap="sm" align="flex-end">
        <Button variant="default" mih={44} onClick={drawerHandlers.open}>
          {t('tasks.filters.open')}
          {filterCount > 0 ? ` (${filterCount})` : ''}
        </Button>
        <Select
          aria-label={t('tasks.sort.label')}
          data={SORTS.map((s) => ({ value: s.value, label: t(`tasks.sort.${s.key}`) }))}
          value={`${query.sortBy}:${query.direction}`}
          onChange={(v) => {
            if (!v) return;
            const [sortBy, direction] = v.split(':') as [TaskSortField, SortDirection];
            setQuery({ ...query, sortBy, direction, page: 0 });
          }}
          allowDeselect={false}
          size="md"
          style={{ flex: 1, minWidth: 160 }}
        />
      </Group>

      {list.isLoading && <Loader size="sm" />}
      {list.isError && <Alert color="red">{t(taskErrorKey(list.error))}</Alert>}
      {list.data && items.length === 0 && <Text c="dimmed">{t(filterCount > 0 ? 'tasks.emptyFiltered' : 'tasks.empty')}</Text>}

      <Stack gap="xs" data-testid="task-list">
        {items.map((task) => (
          <TaskCard key={task.id} task={task} space={details.get(task.spaceId)} />
        ))}
      </Stack>

      {list.data && list.data.totalPages > 1 && (
        <Pagination
          total={list.data.totalPages}
          value={query.page + 1}
          onChange={(p) => setQuery({ ...query, page: p - 1 })}
          size="md"
        />
      )}

      <TaskFiltersDrawer
        opened={drawer}
        onClose={drawerHandlers.close}
        query={query}
        onChange={setQuery}
        spaces={spaces}
        canSeeArchived={canSeeArchived}
      />

      {creating && (
        <TaskFormModal
          task={null}
          spaces={spaces}
          defaultSpaceId={query.spaceId}
          onClose={() => setCreating(false)}
          onSaved={(created) => navigate(`/tasks/${created.id}`)}
        />
      )}
    </Stack>
  );
}
