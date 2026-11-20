import { Alert, Badge, Box, Button, Group, Select, Stack, Text } from '@mantine/core';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { needsOpenSubtaskWarning } from './panelLogic';
import type { SpaceStatus, Task } from './tasksApi';
import { useChangeTaskStatus } from './useTasks';

const TOUCH = 44;

interface Props {
  task: Task;
  /** Every status of the space (archived ones included, to resolve the current one). */
  statuses: readonly SpaceStatus[];
  editable: boolean;
  /** Called with a failed change; the panel shows it (409 as a reload hint). */
  onFailure: (error: unknown) => void;
  /** Called before a change is sent, so the panel can clear an old failure. */
  onStart: () => void;
}

/**
 * Status of a task without a dialog (spec UI-5). Moving a task with open subtasks into a DONE status is allowed but
 * asks for a confirmation first (ST-5); every other change is sent at once with the task version (spec §6.5).
 */
export function PanelStatus({ task, statuses, editable, onFailure, onStart }: Props) {
  const { t } = useTranslation();
  const changeStatus = useChangeTaskStatus();
  const [pending, setPending] = useState<SpaceStatus | null>(null);
  const current = statuses.find((s) => s.id === task.statusId);
  const options = statuses.filter((s) => !s.archived || s.id === task.statusId);

  if (!editable) {
    return (
      <Box>
        <Text size="xs" c="dimmed">
          {t('tasks.fields.status')}
        </Text>
        <Badge variant="light" color="gray" data-testid="panel-status">
          {current?.name ?? '…'}
        </Badge>
      </Box>
    );
  }

  const send = (statusId: string) => {
    setPending(null);
    onStart();
    changeStatus.mutate({ id: task.id, statusId, expectedVersion: task.version ?? undefined }, { onError: onFailure });
  };

  const choose = (id: string | null) => {
    if (!id || id === task.statusId) return;
    const target = statuses.find((s) => s.id === id);
    if (target && needsOpenSubtaskWarning(task, current?.category, target.category)) setPending(target);
    else send(id);
  };

  return (
    <Stack gap="xs">
      <Select
        label={t('tasks.fields.status')}
        data={options.map((s) => ({ value: s.id, label: s.name }))}
        value={task.statusId}
        onChange={choose}
        allowDeselect={false}
        disabled={changeStatus.isPending || options.length === 0}
        size="md"
      />
      {pending && (
        <Alert color="yellow" role="alert" data-testid="open-subtasks-warning" title={t('tasks.panel.openSubtasksTitle')}>
          <Stack gap="xs" align="flex-start">
            <Text size="sm">
              {t('tasks.panel.openSubtasksWarning', { count: task.openSubtaskCount, status: pending.name })}
            </Text>
            <Group gap="xs">
              <Button mih={TOUCH} onClick={() => send(pending.id)} loading={changeStatus.isPending}>
                {t('tasks.panel.moveAnyway')}
              </Button>
              <Button mih={TOUCH} variant="default" onClick={() => setPending(null)}>
                {t('tasks.cancel')}
              </Button>
            </Group>
          </Stack>
        </Alert>
      )}
    </Stack>
  );
}
