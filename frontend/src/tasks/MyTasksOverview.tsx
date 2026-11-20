import { Alert, Badge, Group, Loader, Stack, Text, Title, type TitleOrder } from '@mantine/core';
import { useId } from 'react';
import { useTranslation } from 'react-i18next';
import { fullName } from '../members/memberFormat';
import { useMembers } from '../members/useMembers';
import { TaskCard } from './TaskCard';
import { DEADLINE_GROUPS, groupTasksByDeadline, taskErrorKey } from './taskLogic';
import type { Space, TaskSummary } from './tasksApi';
import { useSpaceDetailsState, useTaskOverview, type OverviewView } from './useTasks';

export interface MyTasksOverviewProps {
  /** `MY_TASKS`: open tasks I am an assignee of; `ISSUED_BY_ME`: tasks I created and assigned to others (spec §3). */
  view?: OverviewView;
  /** Task open in the panel (highlighted). */
  selectedId?: string;
  /** Show at most this many tasks per group (e.g. on the main-page widget); counts always cover every task. */
  limitPerGroup?: number;
  /** Heading level of the group titles, so the overview fits under any page heading. */
  headingOrder?: TitleOrder;
}

type GroupKey = (typeof DEADLINE_GROUPS)[number] | 'CLOSED';

const isClosedCategory = (space: Space | undefined, task: TaskSummary) => {
  const category = space?.statuses.find((s) => s.id === task.statusId)?.category;
  return category === 'DONE' || category === 'CANCELED';
};

/**
 * "My tasks" overview (spec UI-4): the caller's tasks grouped into Overdue, This week, Later and No deadline (Europe/Kyiv
 * days) with a count per group; with `ISSUED_BY_ME` the tasks issued to others, closed ones in their own group.
 * Selecting a task opens the task panel. Self-contained, so the main-page "My tasks" widget can reuse it.
 */
export function MyTasksOverview({ view = 'MY_TASKS', selectedId, limitPerGroup, headingOrder = 2 }: MyTasksOverviewProps) {
  const { t } = useTranslation();
  const overview = useTaskOverview(view);
  const items = overview.data?.items ?? [];
  // Closed tasks are told apart by the status category of their space, so grouping waits for the spaces.
  const { details, pending } = useSpaceDetailsState([...new Set(items.map((i) => i.spaceId))]);
  const { groups, closed, openCount } = groupTasksByDeadline(items, new Date(), (task) =>
    isClosedCategory(details.get(task.spaceId), task),
  );
  const issued = view === 'ISSUED_BY_ME';

  if (overview.isLoading || pending) return <Loader size="sm" />;
  if (overview.isError) return <Alert color="red">{t(taskErrorKey(overview.error))}</Alert>;
  if (openCount + closed.length === 0) {
    return (
      <Text c="dimmed" data-testid="overview-empty">
        {t(issued ? 'tasks.overview.emptyIssued' : 'tasks.overview.emptyMine')}
      </Text>
    );
  }

  const sections: { key: GroupKey; tasks: TaskSummary[] }[] = DEADLINE_GROUPS.map((key) => ({ key, tasks: groups[key] }));
  if (closed.length > 0) sections.push({ key: 'CLOSED', tasks: closed });

  return (
    <Stack gap="lg" data-testid="task-overview" data-view={view}>
      {sections.map(({ key, tasks }) => (
        <OverviewGroup
          key={key}
          group={key}
          tasks={tasks}
          details={details}
          selectedId={selectedId}
          limit={limitPerGroup}
          headingOrder={headingOrder}
          showAssignees={issued}
        />
      ))}
      {overview.data?.truncated && (
        <Text size="sm" c="dimmed">
          {t('tasks.overview.truncated', { count: items.length })}
        </Text>
      )}
    </Stack>
  );
}

interface GroupProps {
  group: GroupKey;
  tasks: TaskSummary[];
  details: Map<string, Space>;
  selectedId: string | undefined;
  limit: number | undefined;
  headingOrder: TitleOrder;
  showAssignees: boolean;
}

function OverviewGroup({ group, tasks, details, selectedId, limit, headingOrder, showAssignees }: GroupProps) {
  const { t } = useTranslation();
  const headingId = useId();
  const shown = limit === undefined ? tasks : tasks.slice(0, limit);
  const hidden = tasks.length - shown.length;
  const alarming = group === 'OVERDUE' && tasks.length > 0;

  return (
    <section aria-labelledby={headingId} data-testid="overview-group" data-group={group} data-count={tasks.length}>
      <Group gap="xs" mb="xs" wrap="nowrap">
        <Title order={headingOrder} size="h4" id={headingId}>
          {t(`tasks.overview.groups.${group}`)}
        </Title>
        <Badge
          variant={alarming ? 'filled' : 'light'}
          color={alarming ? 'red' : 'gray'}
          size="lg"
          data-testid="overview-count"
        >
          {tasks.length}
        </Badge>
      </Group>
      {tasks.length === 0 ? (
        <Text size="sm" c="dimmed">
          {t('tasks.overview.emptyGroup')}
        </Text>
      ) : (
        <Stack gap="xs" role="list" aria-labelledby={headingId}>
          {shown.map((task) => (
            <div role="listitem" key={task.id}>
              <TaskCard task={task} space={details.get(task.spaceId)} selected={task.id === selectedId}>
                {showAssignees && <AssigneeNames ids={task.assigneeIds} />}
              </TaskCard>
            </div>
          ))}
        </Stack>
      )}
      {hidden > 0 && (
        <Text size="sm" c="dimmed" mt="xs">
          {t('tasks.overview.more', { count: hidden })}
        </Text>
      )}
    </section>
  );
}

/** Who the issued task is assigned to (names from the member directory). */
function AssigneeNames({ ids }: { ids: string[] }) {
  const { t } = useTranslation();
  const members = useMembers(true).data ?? [];
  if (ids.length === 0) return null;
  const names = ids.map((id) => {
    const m = members.find((x) => x.id === id);
    return m ? fullName(m) : t('tasks.panel.unknownMember');
  });
  return (
    <Text size="sm" c="dimmed" data-testid="overview-assignees" style={{ overflowWrap: 'anywhere' }}>
      {t('tasks.overview.assignees', { names: names.join(', ') })}
    </Text>
  );
}
