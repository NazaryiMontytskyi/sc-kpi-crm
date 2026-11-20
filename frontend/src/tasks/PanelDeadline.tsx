import { Alert, Badge, Box, Button, Group, List, Stack, Text, TextInput } from '@mantine/core';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { formatDate } from '../members/memberFormat';
import { deadlineConflictsOf, inputWithDeadline, subtasksBlockingDeadline } from './panelLogic';
import { isRealDate, isVersionConflict, taskErrorKey } from './taskLogic';
import { DEADLINE_BEFORE_SUBTASKS, type ConflictingSubtask, type Task } from './tasksApi';
import { ApiProblemError } from '../api/problem';
import { useUpdateTask } from './useTasks';

const TOUCH = 44;

interface Props {
  task: Task;
  overdue: boolean;
  /** Creator or Admin (decision Q7): the deadline is a task field edited through the task PUT. */
  editable: boolean;
  /** 409 version conflicts go to the panel, which shows the reload hint. */
  onConflict: (error: unknown) => void;
}

/** The ST-3 rejection: the message plus every subtask whose deadline is later than the proposed one. */
function ConflictList({ subtasks }: { subtasks: readonly ConflictingSubtask[] }) {
  const { t } = useTranslation();
  return (
    <Alert color="red" role="alert" data-testid="deadline-conflict">
      <Text size="sm">{t('tasks.errors.TASK_DEADLINE_BEFORE_SUBTASKS')}</Text>
      {subtasks.length > 0 && (
        <List size="sm" mt={4} aria-label={t('tasks.deadline.conflictingSubtasks')}>
          {subtasks.map((s) => (
            <List.Item key={s.id} style={{ overflowWrap: 'anywhere' }}>
              {t('tasks.deadline.conflictItem', { title: s.title, date: formatDate(s.deadline) })}
            </List.Item>
          ))}
        </List>
      )}
    </Alert>
  );
}

/**
 * Deadline of a task with an inline editor (spec UI-5): change or remove it without a dialog. A parent deadline
 * earlier than a subtask deadline is refused before sending when the subtasks are known, and the backend refusal
 * (422 with `conflictingSubtasks`) is shown the same way; nothing is changed silently (ST-3).
 */
export function PanelDeadline({ task, overdue, editable, onConflict }: Props) {
  const { t } = useTranslation();
  const update = useUpdateTask();
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState(task.deadline ?? '');
  const [invalid, setInvalid] = useState(false);
  const [conflicts, setConflicts] = useState<ConflictingSubtask[] | null>(null);
  const [failure, setFailure] = useState<unknown>(null);

  const reset = () => {
    setInvalid(false);
    setConflicts(null);
    setFailure(null);
  };

  const save = (deadline: string | null) => {
    reset();
    if (deadline !== null && !isRealDate(deadline)) {
      setInvalid(true);
      return;
    }
    const blocking = subtasksBlockingDeadline(deadline, task.subtasks);
    if (blocking.length > 0) {
      setConflicts(blocking);
      return;
    }
    if (deadline === task.deadline) {
      setEditing(false);
      return;
    }
    update.mutate(
      {
        id: task.id,
        input: inputWithDeadline(task, deadline),
        related: task.relatedTaskIds,
        attachments: task.attachmentIds,
        expectedVersion: task.version ?? undefined,
      },
      {
        onSuccess: () => setEditing(false),
        onError: (e) => {
          if (isVersionConflict(e)) onConflict(e);
          else if (e instanceof ApiProblemError && e.code === DEADLINE_BEFORE_SUBTASKS) setConflicts(deadlineConflictsOf(e, deadline, task.subtasks));
          else setFailure(e);
        },
      },
    );
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    save(value === '' ? null : value);
  };

  return (
    <Box>
      <Text size="xs" c="dimmed">
        {t('tasks.panel.deadline')}
      </Text>
      <Group gap={6} wrap="wrap" align="center">
        <Text size="sm" c={overdue ? 'red' : undefined} fw={overdue ? 700 : undefined} data-testid="panel-deadline">
          {task.deadline ? formatDate(task.deadline) : t('tasks.notSet')}
        </Text>
        {overdue && (
          <Badge color="red" variant="light" size="sm" data-testid="panel-overdue">
            {t('tasks.panel.overdue')}
          </Badge>
        )}
        {editable && !editing && (
          <Button
            variant="subtle"
            size="compact-sm"
            mih={TOUCH}
            onClick={() => {
              setValue(task.deadline ?? '');
              reset();
              setEditing(true);
            }}
          >
            {t('tasks.deadline.change')}
          </Button>
        )}
      </Group>
      {editable && editing && (
        <form onSubmit={submit} noValidate data-testid="deadline-editor">
          <Stack gap="xs" mt="xs">
            <TextInput
              type="date"
              label={t('tasks.deadline.inputLabel')}
              value={value}
              onChange={(e) => setValue(e.currentTarget.value)}
              error={invalid ? t('tasks.validation.dueDateInvalid') : undefined}
              size="md"
            />
            {conflicts && <ConflictList subtasks={conflicts} />}
            {failure !== null && (
              <Alert color="red" role="alert">
                {t(taskErrorKey(failure))}
              </Alert>
            )}
            <Group gap="xs">
              <Button type="submit" mih={TOUCH} loading={update.isPending}>
                {t('tasks.deadline.save')}
              </Button>
              {task.deadline && (
                <Button variant="default" mih={TOUCH} disabled={update.isPending} onClick={() => save(null)}>
                  {t('tasks.deadline.clear')}
                </Button>
              )}
              <Button variant="default" mih={TOUCH} disabled={update.isPending} onClick={() => setEditing(false)}>
                {t('tasks.cancel')}
              </Button>
            </Group>
          </Stack>
        </form>
      )}
    </Box>
  );
}
