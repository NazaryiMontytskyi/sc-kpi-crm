import {
  Alert,
  Anchor,
  Badge,
  Box,
  Button,
  Card,
  Checkbox,
  Group,
  Input,
  Paper,
  SegmentedControl,
  Select,
  Stack,
  Text,
  TextInput,
  Title,
} from '@mantine/core';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/CurrentUser';
import type { MemberSummary } from '../members/memberApi';
import { formatDate, fullName } from '../members/memberFormat';
import {
  canAddSubtask,
  canAssignOthers,
  subtaskRequest,
  subtaskToggleTarget,
  validateSubtaskDraft,
  type SubtaskDraft,
  type SubtaskTarget,
} from './panelLogic';
import { canChangeStatus, isOpenAndOverdue, isVersionConflict, taskErrorKey, todayKyiv } from './taskLogic';
import type { SpaceStatus, Task, TaskSummary } from './tasksApi';
import { useTaskLink } from './trackerNav';
import { useChangeTaskStatus, useCreateSubtask } from './useTasks';

const TOUCH = 44;

const EMPTY_DRAFT: SubtaskDraft = { title: '', deadline: '', target: 'me', assigneeId: null };

interface FormProps {
  task: Task;
  members: readonly MemberSummary[];
  onDone: () => void;
}

/**
 * Inline "add subtask" form (spec ST-2, UI-5). "For me" is always offered (the backend assigns the caller);
 * "for someone" appears only with `tasks.assign`. The deadline may not be later than the parent's (ST-3).
 */
function AddSubtaskForm({ task, members, onDone }: FormProps) {
  const { t } = useTranslation();
  const { user } = useAuth();
  const create = useCreateSubtask();
  const [draft, setDraft] = useState<SubtaskDraft>(EMPTY_DRAFT);
  const [submitted, setSubmitted] = useState(false);
  const [failure, setFailure] = useState<unknown>(null);
  const forOthers = canAssignOthers(user);
  const errors = validateSubtaskDraft(draft, task.deadline);
  const show = (k: keyof typeof errors) => {
    const key = submitted ? errors[k] : undefined;
    return key ? t(key) : undefined;
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setSubmitted(true);
    setFailure(null);
    if (Object.keys(errors).length > 0) return;
    create.mutate(
      { parentId: task.id, input: subtaskRequest(forOthers ? draft : { ...draft, target: 'me' }) },
      {
        onSuccess: () => {
          setDraft(EMPTY_DRAFT);
          setSubmitted(false);
          onDone();
        },
        onError: setFailure,
      },
    );
  };

  return (
    <form onSubmit={submit} noValidate data-testid="subtask-form">
      <Stack gap="xs">
        <TextInput
          label={t('tasks.subtasks.titleLabel')}
          required
          value={draft.title}
          onChange={(e) => setDraft({ ...draft, title: e.currentTarget.value })}
          error={show('title')}
          size="md"
        />
        <TextInput
          type="date"
          label={t('tasks.subtasks.deadlineLabel')}
          value={draft.deadline}
          max={task.deadline ?? undefined}
          description={task.deadline ? t('tasks.subtasks.deadlineHint', { date: formatDate(task.deadline) }) : undefined}
          onChange={(e) => setDraft({ ...draft, deadline: e.currentTarget.value })}
          error={show('deadline')}
          size="md"
        />
        {forOthers ? (
          <Input.Wrapper label={t('tasks.subtasks.forWhom')}>
            <SegmentedControl
              fullWidth
              size="md"
              value={draft.target}
              onChange={(v) => setDraft({ ...draft, target: v as SubtaskTarget })}
              data={[
                { value: 'me', label: t('tasks.subtasks.forMe') },
                { value: 'someone', label: t('tasks.subtasks.forSomeone') },
              ]}
              styles={{ label: { minHeight: TOUCH - 8, display: 'flex', alignItems: 'center', justifyContent: 'center' } }}
            />
          </Input.Wrapper>
        ) : (
          <Text size="sm" c="dimmed">
            {t('tasks.subtasks.forMeNote')}
          </Text>
        )}
        {forOthers && draft.target === 'someone' && (
          <Select
            label={t('tasks.subtasks.assigneeLabel')}
            required
            data={members.filter((m) => !m.archived).map((m) => ({ value: m.id, label: fullName(m) }))}
            value={draft.assigneeId}
            onChange={(v) => setDraft({ ...draft, assigneeId: v })}
            searchable
            nothingFoundMessage={t('tasks.assigneeActions.nothingFound')}
            error={show('assignee')}
            size="md"
          />
        )}
        {failure !== null && (
          <Alert color="red" role="alert">
            {t(taskErrorKey(failure))}
          </Alert>
        )}
        <Group gap="xs">
          <Button type="submit" mih={TOUCH} loading={create.isPending}>
            {t('tasks.subtasks.add')}
          </Button>
          <Button variant="default" mih={TOUCH} disabled={create.isPending} onClick={onDone}>
            {t('tasks.cancel')}
          </Button>
        </Group>
      </Stack>
    </form>
  );
}

interface ItemProps {
  subtask: TaskSummary;
  statuses: readonly SpaceStatus[];
  /** Whether a toggle is offered (permission, nothing archived, a target status exists). */
  toggleable: boolean;
  busy: boolean;
  onToggle: (done: boolean) => void;
}

function SubtaskItem({ subtask, statuses, toggleable, busy, onToggle }: ItemProps) {
  const { t } = useTranslation();
  const linkTo = useTaskLink();
  const status = statuses.find((x) => x.id === subtask.statusId);
  const done = status?.category === 'DONE';
  const overdue = isOpenAndOverdue(subtask, todayKyiv(), status?.category);
  return (
    <Paper withBorder p="xs" role="listitem" data-testid="panel-subtask">
      <Group gap="xs" wrap="nowrap" align="flex-start">
        {toggleable && (
          // The label makes the whole 44 px square a target for the small checkbox.
          <Box
            component="label"
            style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minWidth: TOUCH, minHeight: TOUCH, cursor: 'pointer', flexShrink: 0 }}
          >
            <Checkbox
              size="md"
              checked={done}
              disabled={busy}
              onChange={(e) => onToggle(e.currentTarget.checked)}
              aria-label={t('tasks.subtasks.toggleDone', { title: subtask.title })}
            />
          </Box>
        )}
        <Box style={{ minWidth: 0, flex: 1 }}>
          <Anchor
            component={Link}
            {...linkTo(subtask.id)}
            size="sm"
            fw={500}
            td={done ? 'line-through' : undefined}
            style={{ overflowWrap: 'anywhere', display: 'inline-block', minHeight: TOUCH, paddingBlock: 10 }}
          >
            {subtask.title}
          </Anchor>
          <Group gap={6} wrap="wrap">
            {status && (
              <Badge variant="light" color="gray" size="sm">
                {status.name}
              </Badge>
            )}
            {subtask.archived && (
              <Badge variant="outline" color="gray" size="sm">
                {t('tasks.archivedBadge')}
              </Badge>
            )}
            {subtask.deadline && (
              <Text size="xs" c={overdue ? 'red' : 'dimmed'}>
                {t('tasks.due', { date: formatDate(subtask.deadline) })}
                {overdue ? ` · ${t('tasks.overdue')}` : ''}
              </Text>
            )}
          </Group>
        </Box>
      </Group>
    </Paper>
  );
}

interface Props {
  task: Task;
  statuses: readonly SpaceStatus[];
  members: readonly MemberSummary[];
  /** 409 version conflicts go to the panel, which shows the reload hint. */
  onConflict: (error: unknown) => void;
}

/**
 * Subtasks of a task (spec UI-2, ST-1..ST-5): each with an inline done/not-done toggle for users who may change its
 * status (AS-5), plus the inline add form. Subtasks have no subtasks, so the section is shown on parents only.
 */
export function PanelSubtasks({ task, statuses, members, onConflict }: Props) {
  const { t } = useTranslation();
  const { user } = useAuth();
  const changeStatus = useChangeTaskStatus();
  const [adding, setAdding] = useState(false);
  const [failure, setFailure] = useState<unknown>(null);
  const mayAdd = canAddSubtask(user, task);

  const toggle = (s: TaskSummary, done: boolean) => {
    const target = subtaskToggleTarget(statuses, done);
    if (!target) return;
    setFailure(null);
    changeStatus.mutate(
      { id: s.id, statusId: target, expectedVersion: s.version ?? undefined },
      { onError: (e) => (isVersionConflict(e) ? onConflict(e) : setFailure(e)) },
    );
  };

  return (
    <Card withBorder padding="sm" component="section" aria-labelledby="task-panel-subtasks">
      <Title order={3} size="h5" mb="xs" id="task-panel-subtasks">
        {t('tasks.panel.subtasks')}
      </Title>
      <Stack gap="xs">
        {task.subtasks.length === 0 ? (
          <Text c="dimmed" size="sm">
            {t('tasks.panel.noSubtasks')}
          </Text>
        ) : (
          <Stack gap="xs" role="list" aria-label={t('tasks.panel.subtasks')}>
            {task.subtasks.map((s) => {
              const current = statuses.find((x) => x.id === s.statusId);
              const toggleable =
                !task.archived &&
                !s.archived &&
                canChangeStatus(user, s) &&
                !!subtaskToggleTarget(statuses, current?.category !== 'DONE');
              return (
                <SubtaskItem
                  key={s.id}
                  subtask={s}
                  statuses={statuses}
                  toggleable={toggleable}
                  busy={changeStatus.isPending}
                  onToggle={(done) => toggle(s, done)}
                />
              );
            })}
          </Stack>
        )}
        {failure !== null && (
          <Alert color="red" role="alert" withCloseButton closeButtonLabel={t('tasks.close')} onClose={() => setFailure(null)}>
            {t(taskErrorKey(failure))}
          </Alert>
        )}
        {mayAdd &&
          (adding ? (
            <AddSubtaskForm task={task} members={members} onDone={() => setAdding(false)} />
          ) : (
            <Group>
              <Button variant="default" mih={TOUCH} onClick={() => setAdding(true)}>
                {t('tasks.subtasks.new')}
              </Button>
            </Group>
          ))}
      </Stack>
    </Card>
  );
}
