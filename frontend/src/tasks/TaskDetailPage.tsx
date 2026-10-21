import { Alert, Anchor, Badge, Button, Card, Group, Loader, Select, Stack, Tabs, Text, Title } from '@mantine/core';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { useAuth, usePermission } from '../auth/CurrentUser';
import { usePageTitle } from '../layout/pageTitle';
import { formatDate } from '../members/memberFormat';
import { fullName } from '../members/memberFormat';
import { useMembers } from '../members/useMembers';
import { RichTextView } from '../shared/RichTextField';
import { CommentsPanel } from './CommentsPanel';
import { ResolutionsPanel } from './ResolutionsPanel';
import { SuggestDoneBanner } from './SuggestDoneBanner';
import { TaskAttachments } from './TaskAttachments';
import { TaskFormModal } from './TaskFormModal';
import { canEditTask, TASKS_ARCHIVE, taskErrorKey } from './taskLogic';
import type { CustomField, Task } from './tasksApi';
import { useArchiveTask, useChangeTaskStatus, useRestoreTask, useSpace, useSpaces, useTask } from './useTasks';

function FieldValue({ field, value }: { field: CustomField; value: unknown }) {
  const { t } = useTranslation();
  const members = useMembers(false);
  if (value === undefined || value === null || value === '') return <Text c="dimmed">{t('tasks.notSet')}</Text>;
  if (field.type === 'DATE') return <Text>{formatDate(String(value))}</Text>;
  if (field.type === 'PERSON') {
    const m = members.data?.find((x) => x.id === value);
    return <Text>{m ? fullName(m) : '…'}</Text>;
  }
  return <Text style={{ overflowWrap: 'anywhere' }}>{String(value)}</Text>;
}

function TaskBody({ task }: { task: Task }) {
  const [searchParams] = useSearchParams();
  const tabParam = searchParams.get('tab');
  // Deep links from notifications open the matching tab.
  const initialTab = tabParam === 'resolutions' || tabParam === 'history' ? tabParam : 'comments';
  const { t } = useTranslation();
  const { user } = useAuth();
  const canArchive = usePermission(TASKS_ARCHIVE);
  const space = useSpace(task.spaceId);
  const spaces = useSpaces(true);
  const changeStatus = useChangeTaskStatus();
  const archive = useArchiveTask();
  const restore = useRestoreTask();
  const [editing, setEditing] = useState(false);
  const [failure, setFailure] = useState<unknown>(null);

  const editable = canEditTask(user, task) && !task.archived;
  const statuses = (space.data?.statuses ?? []).filter((s) => !s.archived || s.id === task.statusId);
  const fields = (space.data?.customFields ?? []).filter((f) => !f.archived || task.customFieldValues[f.id] !== undefined);
  const run = (fn: () => void) => {
    setFailure(null);
    fn();
  };

  return (
    <Stack gap="md">
      <Anchor component={Link} to="/tasks" size="sm">
        {t('tasks.backToList')}
      </Anchor>
      <Group justify="space-between" align="flex-start" gap="sm">
        <Title order={1} size="h2" style={{ overflowWrap: 'anywhere', flex: 1, minWidth: 0 }}>
          {task.title}
        </Title>
        <Group gap="xs">
          {editable && (
            <Button mih={44} onClick={() => setEditing(true)}>
              {t('tasks.edit')}
            </Button>
          )}
          {canArchive && !task.archived && (
            <Button
              mih={44}
              variant="default"
              loading={archive.isPending}
              onClick={() => run(() => archive.mutate(task.id, { onError: setFailure }))}
            >
              {t('tasks.archive')}
            </Button>
          )}
          {canArchive && task.archived && (
            <Button
              mih={44}
              variant="default"
              loading={restore.isPending}
              onClick={() => run(() => restore.mutate(task.id, { onError: setFailure }))}
            >
              {t('tasks.restore')}
            </Button>
          )}
        </Group>
      </Group>
      {failure !== null && <Alert color="red">{t(taskErrorKey(failure))}</Alert>}
      {task.archived && <Alert color="gray">{t('tasks.archivedNotice')}</Alert>}
      <SuggestDoneBanner task={task} statuses={space.data?.statuses ?? []} />

      <Card withBorder padding="sm">
        <Stack gap="sm">
          <Select
            label={t('tasks.fields.status')}
            data={statuses.map((s) => ({ value: s.id, label: s.name }))}
            value={task.statusId}
            onChange={(v) => v && v !== task.statusId && run(() => changeStatus.mutate({ id: task.id, statusId: v }, { onError: setFailure }))}
            allowDeselect={false}
            disabled={task.archived || changeStatus.isPending}
            size="md"
          />
          <Group gap="xs">
            <Badge variant="light">{t(`tasks.priority.${task.priority}`)}</Badge>
            {space.data && (
              <Text size="sm" c="dimmed">
                {spaces.data?.find((s) => s.id === task.spaceId)?.name ?? space.data.name}
              </Text>
            )}
          </Group>
          <Text size="sm">
            {t('tasks.fields.dueDate')}: {task.dueDate ? formatDate(task.dueDate) : t('tasks.notSet')}
          </Text>
          {task.labels.length > 0 && (
            <Group gap={4}>
              {task.labels.map((l) => (
                <Badge key={l} variant="outline" color="gray" size="sm" tt="none">
                  {l}
                </Badge>
              ))}
            </Group>
          )}
        </Stack>
      </Card>

      <Card withBorder padding="sm">
        <Title order={2} size="h5" mb="xs">
          {t('tasks.fields.description')}
        </Title>
        {task.description ? <RichTextView value={task.description} /> : <Text c="dimmed">{t('tasks.notSet')}</Text>}
      </Card>

      {fields.length > 0 && (
        <Card withBorder padding="sm">
          <Stack gap="xs">
            {fields.map((f) => (
              <div key={f.id}>
                <Text size="xs" c="dimmed">
                  {f.name}
                </Text>
                <FieldValue field={f} value={task.customFieldValues[f.id]} />
              </div>
            ))}
          </Stack>
        </Card>
      )}

      <TaskAttachments task={task} editable={editable} />

      <Tabs defaultValue={initialTab} keepMounted={false}>
        <Tabs.List>
          <Tabs.Tab value="comments" mih={44}>
            {t('tasks.tabs.comments')}
          </Tabs.Tab>
          <Tabs.Tab value="resolutions" mih={44}>
            {t('tasks.tabs.resolutions')}
          </Tabs.Tab>
          <Tabs.Tab value="history" mih={44}>
            {t('tasks.tabs.history')}
          </Tabs.Tab>
        </Tabs.List>
        <Tabs.Panel value="comments" pt="sm">
          <CommentsPanel targetType="TASK" targetId={task.id} readOnly={task.archived} />
        </Tabs.Panel>
        <Tabs.Panel value="resolutions" pt="sm">
          <ResolutionsPanel taskId={task.id} taskArchived={task.archived} />
        </Tabs.Panel>
        <Tabs.Panel value="history" pt="sm">
          <Text c="dimmed">{t('tasks.tabs.soon')}</Text>
        </Tabs.Panel>
      </Tabs>

      {editing && (
        <TaskFormModal task={task} spaces={spaces.data ?? []} onClose={() => setEditing(false)} onSaved={() => setEditing(false)} />
      )}
    </Stack>
  );
}

export function TaskDetailPage() {
  const { t } = useTranslation();
  usePageTitle('nav.tasks');
  const { id = '' } = useParams();
  const query = useTask(id);
  if (query.isLoading) return <Loader size="sm" />;
  if (query.isError || !query.data) return <Alert color="red">{t(query.error ? taskErrorKey(query.error) : 'tasks.loadFailed')}</Alert>;
  return <TaskBody task={query.data} />;
}
