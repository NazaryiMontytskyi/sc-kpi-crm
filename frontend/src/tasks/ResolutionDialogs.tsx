import { Alert, Button, Group, Modal, MultiSelect, Select, Stack, Text, Textarea, TextInput } from '@mantine/core';
import { useMediaQuery } from '@mantine/hooks';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { fieldErrorMap } from '../api/problem';
import { fullName } from '../members/memberFormat';
import { useMembers } from '../members/useMembers';
import { isNoteValid, resolutionErrorKey, validateResolutionForm } from './resolutionLogic';
import type { IssueInput, RedirectInput } from './resolutionsApi';
import { todayKyiv } from './taskLogic';

const TOUCH = 44;
const MAX_TEXT = 4000;

interface ShellProps {
  title: string;
  onClose: () => void;
  onSubmit: (e: FormEvent) => void;
  pending: boolean;
  failure: unknown;
  submitLabel: string;
  submitColor?: string;
  children: React.ReactNode;
}

/** Full-screen sheet on phones, centered modal on larger screens. */
function DialogShell({ title, onClose, onSubmit, pending, failure, submitLabel, submitColor, children }: ShellProps) {
  const { t } = useTranslation();
  const mobile = useMediaQuery('(max-width: 48em)');
  return (
    <Modal opened onClose={onClose} fullScreen={mobile} title={title} closeButtonProps={{ 'aria-label': t('tasks.close'), size: 'lg' }}>
      <form onSubmit={onSubmit} noValidate>
        <Stack gap="sm">
          {failure !== null && <Alert color="red">{t(resolutionErrorKey(failure))}</Alert>}
          {children}
          <Group justify="flex-end" mt="xs">
            <Button variant="default" mih={TOUCH} onClick={onClose} disabled={pending}>
              {t('tasks.cancel')}
            </Button>
            <Button type="submit" mih={TOUCH} loading={pending} color={submitColor}>
              {submitLabel}
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}

interface NoteDialogProps {
  kind: 'submit' | 'rework' | 'cancel';
  pending: boolean;
  failure: unknown;
  onClose: () => void;
  onConfirm: (text: string) => void;
}

/** Submit (completion note required), rework (comment required) and cancel (comment optional). */
export function NoteDialog({ kind, pending, failure, onClose, onConfirm }: NoteDialogProps) {
  const { t } = useTranslation();
  const [text, setText] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const required = kind !== 'cancel';
  const invalid = required && !isNoteValid(text);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setSubmitted(true);
    if (invalid) return;
    onConfirm(text.trim());
  };

  return (
    <DialogShell
      title={t(`tasks.resolutions.dialog.${kind}.title`)}
      onClose={onClose}
      onSubmit={submit}
      pending={pending}
      failure={failure}
      submitLabel={t(`tasks.resolutions.dialog.${kind}.confirm`)}
      submitColor={kind === 'cancel' ? 'red' : undefined}
    >
      {kind === 'cancel' && <Text size="sm">{t('tasks.resolutions.dialog.cancel.hint')}</Text>}
      <Textarea
        label={t(`tasks.resolutions.dialog.${kind}.label`)}
        required={required}
        value={text}
        onChange={(e) => setText(e.currentTarget.value)}
        maxLength={MAX_TEXT}
        rows={4}
        size="md"
        data-autofocus
        error={submitted && invalid ? t('tasks.resolutions.validation.noteRequired') : undefined}
      />
    </DialogShell>
  );
}

interface IssueDialogProps {
  mode: 'issue' | 'redirect';
  /** Executor excluded from the picker on redirect. */
  excludeUserId?: string;
  pending: boolean;
  failure: unknown;
  onClose: () => void;
  onIssue?: (input: IssueInput) => void;
  onRedirect?: (input: RedirectInput) => void;
}

/** Issue-resolution and redirect form: executor, (co-executors), instruction and due date. */
export function ResolutionFormDialog({ mode, excludeUserId, pending, failure, onClose, onIssue, onRedirect }: IssueDialogProps) {
  const { t } = useTranslation();
  const members = useMembers(false);
  const [executorId, setExecutorId] = useState<string | null>(null);
  const [coExecutorIds, setCoExecutorIds] = useState<string[]>([]);
  const [instruction, setInstruction] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [submitted, setSubmitted] = useState(false);

  const today = todayKyiv();
  const options = (members.data ?? [])
    .filter((m) => !m.archived && m.id !== excludeUserId)
    .map((m) => ({ value: m.id, label: fullName(m) }));
  const errors = validateResolutionForm({ executorId, instruction, dueDate }, today);
  const serverErrors = fieldErrorMap(failure);
  const show = (k: keyof typeof errors) => {
    const key = submitted ? errors[k] : undefined;
    return key ? t(key) : serverErrors[k];
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setSubmitted(true);
    if (Object.keys(errors).length > 0 || !executorId) return;
    const base = { executorId, instruction: instruction.trim(), dueDate };
    if (mode === 'issue') onIssue?.({ ...base, coExecutorIds: coExecutorIds.filter((id) => id !== executorId) });
    else onRedirect?.(base);
  };

  return (
    <DialogShell
      title={t(`tasks.resolutions.dialog.${mode}.title`)}
      onClose={onClose}
      onSubmit={submit}
      pending={pending}
      failure={failure}
      submitLabel={t(`tasks.resolutions.dialog.${mode}.confirm`)}
    >
      <Select
        label={t('tasks.resolutions.fields.executor')}
        required
        searchable
        nothingFoundMessage={t('tasks.resolutions.noMembers')}
        data={options}
        value={executorId}
        onChange={setExecutorId}
        size="md"
        error={show('executorId')}
      />
      {mode === 'issue' && (
        <MultiSelect
          label={t('tasks.resolutions.fields.coExecutors')}
          searchable
          nothingFoundMessage={t('tasks.resolutions.noMembers')}
          data={options.filter((o) => o.value !== executorId)}
          value={coExecutorIds}
          onChange={setCoExecutorIds}
          size="md"
        />
      )}
      <Textarea
        label={t('tasks.resolutions.fields.instruction')}
        required
        value={instruction}
        onChange={(e) => setInstruction(e.currentTarget.value)}
        maxLength={MAX_TEXT}
        rows={4}
        size="md"
        error={show('instruction')}
      />
      <TextInput
        label={t('tasks.resolutions.fields.dueDate')}
        required
        type="date"
        min={today}
        value={dueDate}
        onChange={(e) => setDueDate(e.currentTarget.value)}
        size="md"
        error={show('dueDate')}
      />
    </DialogShell>
  );
}
