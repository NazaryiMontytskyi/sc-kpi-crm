import { Alert, Button, Group, Select, Stack, Text } from '@mantine/core';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { doneStatuses, showSuggestDone } from './resolutionLogic';
import { taskErrorKey } from './taskLogic';
import type { SpaceStatus, Task } from './tasksApi';
import { useChangeTaskStatus } from './useTasks';

/**
 * "All resolutions accepted - move the task to Done?" Shown only while the backend flags
 * `suggestDone`. The task is moved only after the user picks a DONE status and confirms.
 */
export function SuggestDoneBanner({ task, statuses }: { task: Task; statuses: readonly SpaceStatus[] }) {
  const { t } = useTranslation();
  const changeStatus = useChangeTaskStatus();
  const [dismissed, setDismissed] = useState(false);
  const [chosen, setChosen] = useState<string | null>(null);
  const [failure, setFailure] = useState<unknown>(null);

  if (dismissed || !showSuggestDone(task, statuses)) return null;
  const targets = doneStatuses(statuses);
  const selected = chosen ?? targets[0].id;

  return (
    <Alert color="green" data-testid="suggest-done" title={t('tasks.resolutions.suggestDone.title')}>
      <Stack gap="xs">
        <Text size="sm">{t('tasks.resolutions.suggestDone.text')}</Text>
        {targets.length > 1 && (
          <Select
            label={t('tasks.resolutions.suggestDone.statusLabel')}
            data={targets.map((s) => ({ value: s.id, label: s.name }))}
            value={selected}
            onChange={(v) => setChosen(v)}
            allowDeselect={false}
            size="md"
          />
        )}
        {targets.length === 1 && (
          <Text size="sm" fw={600}>
            {targets[0].name}
          </Text>
        )}
        {failure !== null && <Alert color="red">{t(taskErrorKey(failure))}</Alert>}
        <Group gap="xs">
          <Button
            mih={44}
            loading={changeStatus.isPending}
            onClick={() => {
              setFailure(null);
              changeStatus.mutate({ id: task.id, statusId: selected }, { onError: setFailure });
            }}
          >
            {t('tasks.resolutions.suggestDone.confirm')}
          </Button>
          <Button mih={44} variant="default" disabled={changeStatus.isPending} onClick={() => setDismissed(true)}>
            {t('tasks.resolutions.suggestDone.dismiss')}
          </Button>
        </Group>
      </Stack>
    </Alert>
  );
}
