import { Alert, Button, Group, Modal, Select, Stack, TagsInput, TextInput } from '@mantine/core';
import { useMediaQuery } from '@mantine/hooks';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { fieldErrorMap } from '../api/problem';
import { RichTextField, type RichDoc } from '../shared/RichTextField';
import { CustomFieldInputs } from './CustomFieldInputs';
import {
  activeFields,
  draftFromValues,
  normalizeLabels,
  taskErrorKey,
  validateFieldDrafts,
  validateTaskForm,
  valuesFromDraft,
  type FieldDraft,
} from './taskLogic';
import { PRIORITIES, type SpaceSummary, type Task, type TaskInput, type TaskPriority } from './tasksApi';
import { useCreateTask, useSpace, useUpdateTask } from './useTasks';

interface Props {
  /** `null` creates a task. */
  task: Task | null;
  spaces: SpaceSummary[];
  defaultSpaceId?: string;
  onClose: () => void;
  onSaved: (task: Task) => void;
}

/** Create / edit form: title, rich-text description, priority, labels, due date and the space's custom fields. */
export function TaskFormModal({ task, spaces, defaultSpaceId, onClose, onSaved }: Props) {
  const { t } = useTranslation();
  const mobile = useMediaQuery('(max-width: 48em)');
  const create = useCreateTask();
  const update = useUpdateTask();

  const [spaceId, setSpaceId] = useState<string | null>(task?.spaceId ?? defaultSpaceId ?? null);
  const space = useSpace(spaceId ?? undefined);
  const [title, setTitle] = useState(task?.title ?? '');
  const [description, setDescription] = useState<RichDoc | undefined>(task?.description ?? undefined);
  const [priority, setPriority] = useState<TaskPriority>(task?.priority ?? 'NORMAL');
  const [dueDate, setDueDate] = useState(task?.dueDate ?? '');
  const [labels, setLabels] = useState<string[]>(task?.labels ?? []);
  const [draft, setDraft] = useState<FieldDraft | null>(null);
  const [submitted, setSubmitted] = useState(false);
  const [failure, setFailure] = useState<unknown>(null);

  const fields = activeFields(space.data);
  // Custom values are seeded from the task once the space is loaded; changing the space starts from empty.
  const values: FieldDraft = draft ?? draftFromValues(fields, task && task.spaceId === spaceId ? task.customFieldValues : {});
  const formErrors = validateTaskForm({ title, priority, dueDate, labels });
  const fieldErrors = validateFieldDrafts(fields, values);
  const valid = Object.keys(formErrors).length === 0 && Object.keys(fieldErrors).length === 0 && !!spaceId && !!space.data;
  const serverErrors = fieldErrorMap(failure);
  const pending = create.isPending || update.isPending;

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setSubmitted(true);
    setFailure(null);
    if (!valid || !spaceId) return;
    const input: TaskInput = {
      title: title.trim(),
      description,
      priority,
      dueDate: dueDate || undefined,
      labels: normalizeLabels(labels),
      customFieldValues: valuesFromDraft(fields, values),
    };
    const done = { onSuccess: (saved: Task) => onSaved(saved), onError: setFailure };
    if (task) update.mutate({ id: task.id, input, related: task.relatedTaskIds, attachments: task.attachmentIds }, done);
    else create.mutate({ ...input, spaceId }, done);
  };

  const show = <K extends keyof typeof formErrors>(k: K) => {
    const key = submitted ? formErrors[k] : undefined;
    return key ? t(key) : serverErrors[k];
  };

  return (
    <Modal
      opened
      onClose={onClose}
      fullScreen={mobile}
      title={t(task ? 'tasks.form.editTitle' : 'tasks.form.createTitle')}
      closeButtonProps={{ 'aria-label': t('tasks.close'), size: 'lg' }}
    >
      <form onSubmit={submit} noValidate>
        <Stack gap="sm">
          {failure !== null && <Alert color="red">{t(taskErrorKey(failure))}</Alert>}
          <Select
            label={t('tasks.spaceLabel')}
            required
            data={spaces.filter((s) => !s.archived || s.id === task?.spaceId).map((s) => ({ value: s.id, label: s.name }))}
            value={spaceId}
            onChange={(v) => {
              setSpaceId(v);
              setDraft(null);
            }}
            disabled={!!task}
            size="md"
            error={submitted && !spaceId ? t('tasks.validation.spaceRequired') : undefined}
          />
          <TextInput
            label={t('tasks.fields.title')}
            required
            value={title}
            onChange={(e) => setTitle(e.currentTarget.value)}
            error={show('title')}
            size="md"
          />
          <RichTextField label={t('tasks.fields.description')} value={description} onChange={setDescription} />
          <Select
            label={t('tasks.fields.priority')}
            data={PRIORITIES.map((p) => ({ value: p, label: t(`tasks.priority.${p}`) }))}
            value={priority}
            onChange={(v) => v && setPriority(v as TaskPriority)}
            allowDeselect={false}
            size="md"
          />
          <TextInput
            label={t('tasks.fields.dueDate')}
            type="date"
            value={dueDate}
            onChange={(e) => setDueDate(e.currentTarget.value)}
            error={show('dueDate')}
            size="md"
          />
          <TagsInput
            label={t('tasks.fields.labels')}
            value={labels}
            onChange={setLabels}
            error={show('labels')}
            splitChars={[',']}
            size="md"
          />
          <CustomFieldInputs
            fields={fields}
            draft={values}
            errors={submitted ? fieldErrors : {}}
            onChange={(id, v) => setDraft({ ...values, [id]: v })}
          />
          <Group justify="flex-end" mt="xs">
            <Button variant="default" onClick={onClose} mih={44}>
              {t('tasks.cancel')}
            </Button>
            <Button type="submit" loading={pending} mih={44}>
              {t('tasks.save')}
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}
