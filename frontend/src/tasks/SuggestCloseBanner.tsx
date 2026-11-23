import { Alert, Button, Group, Select, Stack, Text } from '@mantine/core';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { doneTargets, showSuggestClose } from './panelLogic';
import type { SpaceStatus, Task } from './tasksApi';
import { useChangeTaskStatus } from './useTasks';

const TOUCH = 44;

interface Props {
  task: Task;
  statuses: readonly SpaceStatus[];
  /** Whether the user may change the status (AS-5); the banner is an action, so others do not see it. */
  editable: boolean;
  onFailure: (error: unknown) => void;
  onStart: () => void;
}

/**
 * "All subtasks are done - close the task?" (spec ST-4). Driven only by the backend flag `suggestClose`. The parent
 * moves to a DONE status only when the user clicks; showing, dismissing or ignoring the banner changes nothing.
 */
export function SuggestCloseBanner({ task, statuses, editable, onFailure, onStart }: Props) {
  const { t } = useTranslation();
  const changeStatus = useChangeTaskStatus();
  const [dismissed, setDismissed] = useState(false);
  const [chosen, setChosen] = useState<string | null>(null);
  const current = statuses.find((s) => s.id === task.statusId);

  if (!editable || dismissed || !showSuggestClose(task, current?.category, statuses)) return null;
  const targets = doneTargets(statuses);
  const selected = chosen ?? targets[0].id;

  return (
    // The title uses the body text colour: the green alert title is below 4.5:1 on its tinted background (axe).
    <Alert color="green" data-testid="suggest-close" title={t('tasks.suggestClose.title')} styles={{ title: { color: 'var(--mantine-color-text)' } }}>
      <Stack gap="xs">
        <Text size="sm">{t('tasks.suggestClose.text')}</Text>
        {targets.length > 1 && (
          <Select
            label={t('tasks.suggestClose.statusLabel')}
            data={targets.map((s) => ({ value: s.id, label: s.name }))}
            value={selected}
            onChange={setChosen}
            allowDeselect={false}
            size="md"
          />
        )}
        <Group gap="xs">
          <Button
            mih={TOUCH}
            loading={changeStatus.isPending}
            onClick={() => {
              onStart();
              changeStatus.mutate(
                { id: task.id, statusId: selected, expectedVersion: task.version ?? undefined },
                { onError: onFailure },
              );
            }}
          >
            {targets.length === 1 ? t('tasks.suggestClose.confirmTo', { status: targets[0].name }) : t('tasks.suggestClose.confirm')}
          </Button>
          <Button mih={TOUCH} variant="default" disabled={changeStatus.isPending} onClick={() => setDismissed(true)}>
            {t('tasks.suggestClose.dismiss')}
          </Button>
        </Group>
      </Stack>
    </Alert>
  );
}
