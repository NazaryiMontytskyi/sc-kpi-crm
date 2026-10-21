import { Alert, Card, Stack, Text, Title } from '@mantine/core';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { AttachmentList, AttachmentUploader } from './Attachments';
import { addAttachmentIds, attachmentErrorKey, MAX_TASK_ATTACHMENTS, remainingSlots, type AttachmentInfo } from './attachmentLogic';
import { uploadAttachment } from './attachmentsApi';
import type { Task, TaskInput } from './tasksApi';
import { useUpdateTask } from './useTasks';

function inputOf(task: Task): TaskInput {
  return {
    title: task.title,
    description: task.description ?? undefined,
    priority: task.priority,
    dueDate: task.dueDate ?? undefined,
    labels: task.labels,
    customFieldValues: task.customFieldValues,
  };
}

/**
 * Files attached to a task. Attaching and removing replace the task's attachment list through the task PUT
 * (the only way the API attaches files); everything else is sent back unchanged.
 */
export function TaskAttachments({ task, editable }: { task: Task; editable: boolean }) {
  const { t } = useTranslation();
  const update = useUpdateTask();
  const [failure, setFailure] = useState<unknown>(null);

  const save = async (attachmentIds: string[]) => {
    setFailure(null);
    try {
      await update.mutateAsync({ id: task.id, input: inputOf(task), related: task.relatedTaskIds, attachments: attachmentIds });
    } catch (e) {
      setFailure(e);
    }
  };

  return (
    <Card withBorder padding="sm">
      <Stack gap="xs">
        <Title order={2} size="h5">
          {t('tasks.attachments.title')}
        </Title>
        {task.attachments.length === 0 && <Text c="dimmed">{t('tasks.attachments.empty')}</Text>}
        <AttachmentList
          attachments={task.attachments}
          label={t('tasks.attachments.title')}
          disabled={update.isPending}
          onRemove={
            editable
              ? (a: AttachmentInfo) => {
                  void save(task.attachmentIds.filter((id) => id !== a.id));
                }
              : undefined
          }
          removeLabel={(name) => t('tasks.attachments.remove', { name })}
        />
        {failure !== null && <Alert color="red">{t(attachmentErrorKey(failure))}</Alert>}
        {editable && (
          <AttachmentUploader
            upload={uploadAttachment}
            slots={remainingSlots(task.attachmentIds.length, MAX_TASK_ATTACHMENTS)}
            disabled={update.isPending}
            onUploaded={(uploaded) => save(addAttachmentIds(task.attachmentIds, uploaded.map((u) => u.id)))}
          />
        )}
      </Stack>
    </Card>
  );
}
