import { ActionIcon, Alert, Avatar, Box, Button, Group, Select, Stack, Text } from '@mantine/core';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/CurrentUser';
import type { MemberSummary } from '../members/memberApi';
import { fileUrl } from '../members/memberApi';
import { fullName, initials } from '../members/memberFormat';
import { canAssignOthers, canManageAssignee } from './panelLogic';
import { taskErrorKey } from './taskLogic';
import type { Task } from './tasksApi';
import { useAddAssignee, useRemoveAssignee } from './useTasks';

const TOUCH = 44;

interface Props {
  task: Task;
  members: readonly MemberSummary[];
}

/**
 * Active assignees as avatars with their names (names stay visible: touch screens have no hover tooltips), with
 * inline add and remove (spec UI-5). Controls follow AS-3: `tasks.assign` manages anybody; without it a user may
 * only assign or remove themselves as the creator (with `tasks.create`) or with `tasks.edit.any`.
 */
export function PanelAssignees({ task, members }: Props) {
  const { t } = useTranslation();
  const { user } = useAuth();
  const add = useAddAssignee();
  const remove = useRemoveAssignee();
  const [failure, setFailure] = useState<unknown>(null);
  // Remounting the picker after each pick clears its search text.
  const [pickerKey, setPickerKey] = useState(0);
  const busy = add.isPending || remove.isPending;
  const open = !task.archived;
  const nameOf = (id: string) => {
    const m = members.find((x) => x.id === id);
    return m ? fullName(m) : t('tasks.panel.unknownMember');
  };

  const assign = (userId: string) => {
    setFailure(null);
    add.mutate({ taskId: task.id, userId }, { onError: setFailure });
  };
  const unassign = (userId: string) => {
    setFailure(null);
    remove.mutate({ taskId: task.id, userId }, { onError: setFailure });
  };

  const candidates = members.filter((m) => !m.archived && !task.assigneeIds.includes(m.id));
  const mayAssignOthers = open && canAssignOthers(user);
  const maySelfAssign = open && !!user && !task.assigneeIds.includes(user.id) && canManageAssignee(user, task, user.id);

  return (
    <Box>
      <Text size="xs" c="dimmed">
        {t('tasks.panel.assignees')}
      </Text>
      <Stack gap="xs">
        {task.assigneeIds.length === 0 ? (
          <Text c="dimmed">{t('tasks.panel.noAssignees')}</Text>
        ) : (
          <Group gap="xs" role="list" aria-label={t('tasks.panel.assignees')} data-testid="panel-assignees">
            {task.assigneeIds.map((id) => {
              const m = members.find((x) => x.id === id);
              const name = nameOf(id);
              return (
                <Group key={id} gap={6} wrap="nowrap" role="listitem" style={{ minWidth: 0 }}>
                  <Avatar size="md" radius="xl" src={m?.avatarFileId ? fileUrl(m.avatarFileId) : null} alt="" aria-hidden>
                    {m ? initials(m) : '?'}
                  </Avatar>
                  <Text size="sm" style={{ overflowWrap: 'anywhere' }}>
                    {name}
                  </Text>
                  {open && canManageAssignee(user, task, id) && (
                    <ActionIcon
                      variant="subtle"
                      color="gray"
                      size={TOUCH}
                      disabled={busy}
                      aria-label={t('tasks.assigneeActions.remove', { name })}
                      onClick={() => unassign(id)}
                    >
                      <span aria-hidden>×</span>
                    </ActionIcon>
                  )}
                </Group>
              );
            })}
          </Group>
        )}
        {(mayAssignOthers || maySelfAssign) && (
          <Group gap="xs" align="flex-end" wrap="wrap">
            {mayAssignOthers && (
              <Select
                key={pickerKey}
                label={t('tasks.assigneeActions.add')}
                placeholder={t('tasks.assigneeActions.placeholder')}
                data={candidates.map((m) => ({ value: m.id, label: fullName(m) }))}
                value={null}
                onChange={(v) => {
                  if (!v) return;
                  setPickerKey((k) => k + 1);
                  assign(v);
                }}
                searchable
                nothingFoundMessage={t('tasks.assigneeActions.nothingFound')}
                disabled={busy}
                size="md"
                style={{ flex: '1 1 200px', minWidth: 0 }}
              />
            )}
            {maySelfAssign && (
              <Button variant="default" mih={TOUCH} disabled={busy} onClick={() => user && assign(user.id)}>
                {t('tasks.assigneeActions.assignMe')}
              </Button>
            )}
          </Group>
        )}
        {failure !== null && (
          <Alert color="red" role="alert" withCloseButton closeButtonLabel={t('tasks.close')} onClose={() => setFailure(null)}>
            {t(taskErrorKey(failure))}
          </Alert>
        )}
      </Stack>
    </Box>
  );
}
