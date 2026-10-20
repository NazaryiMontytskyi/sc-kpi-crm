import { Alert, Button, Checkbox, Group, Modal, Select, Stack, TagsInput, Text, TextInput } from '@mantine/core';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { fieldErrorMap } from '../api/problem';
import { activeUnits } from '../org/orgTree';
import { useOrgTree } from '../org/useOrg';
import { taskErrorKey } from './taskLogic';
import {
  FIELD_TYPES,
  STATUS_CATEGORIES,
  type CustomField,
  type CustomFieldType,
  type Space,
  type SpaceStatus,
  type StatusCategory,
} from './tasksApi';
import {
  useAddCustomField,
  useAddStatus,
  useArchiveStatus,
  useCreateSpace,
  useUpdateCustomField,
  useUpdateSpace,
  useUpdateStatus,
} from './useTasks';

const closeProps = (label: string) => ({ 'aria-label': label, size: 'lg' as const });

function Footer({ onClose, pending }: { onClose: () => void; pending: boolean }) {
  const { t } = useTranslation();
  return (
    <Group justify="flex-end" mt="xs">
      <Button variant="default" mih={44} onClick={onClose}>
        {t('tasks.cancel')}
      </Button>
      <Button type="submit" mih={44} loading={pending}>
        {t('tasks.save')}
      </Button>
    </Group>
  );
}

// ---- space ---------------------------------------------------------------------------------

/** Create (`space` null) or edit name / unit binding / general flag of a space. */
export function SpaceFormModal({ space, onClose, onSaved }: { space: Space | null; onClose: () => void; onSaved?: (s: Space) => void }) {
  const { t } = useTranslation();
  const create = useCreateSpace();
  const update = useUpdateSpace();
  const tree = useOrgTree(false);
  const [name, setName] = useState(space?.name ?? '');
  const [general, setGeneral] = useState(space?.general ?? false);
  const [orgUnitId, setOrgUnitId] = useState<string | null>(space?.orgUnitId ?? null);
  const [submitted, setSubmitted] = useState(false);
  const [failure, setFailure] = useState<unknown>(null);
  const nameError = submitted && name.trim() === '' ? t('tasks.validation.nameRequired') : fieldErrorMap(failure).name;

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitted(true);
    setFailure(null);
    if (name.trim() === '') return;
    const input = { name: name.trim(), general, orgUnitId: general ? undefined : (orgUnitId ?? undefined) };
    const done = {
      onSuccess: (saved: Space) => {
        onSaved?.(saved);
        onClose();
      },
      onError: setFailure,
    };
    if (space) update.mutate({ id: space.id, input }, done);
    else create.mutate(input, done);
  };

  return (
    <Modal opened onClose={onClose} title={t(space ? 'tasks.space.editTitle' : 'tasks.space.createTitle')} closeButtonProps={closeProps(t('tasks.close'))}>
      <form onSubmit={submit} noValidate>
        <Stack gap="sm">
          {failure !== null && <Alert color="red">{t(taskErrorKey(failure))}</Alert>}
          <TextInput label={t('tasks.space.name')} required value={name} onChange={(e) => setName(e.currentTarget.value)} error={nameError} size="md" />
          <Checkbox label={t('tasks.space.general')} checked={general} onChange={(e) => setGeneral(e.currentTarget.checked)} />
          {!general && (
            <Select
              label={t('tasks.space.orgUnit')}
              description={t('tasks.space.orgUnitHint')}
              data={activeUnits(tree.data ?? []).map((u) => ({ value: u.id, label: u.name }))}
              value={orgUnitId}
              onChange={setOrgUnitId}
              clearable
              searchable
              size="md"
            />
          )}
          <Footer onClose={onClose} pending={create.isPending || update.isPending} />
        </Stack>
      </form>
    </Modal>
  );
}

// ---- statuses ------------------------------------------------------------------------------

/** Add (`status` null) or edit a status: name + category. */
export function StatusFormModal({ spaceId, status, onClose }: { spaceId: string; status: SpaceStatus | null; onClose: () => void }) {
  const { t } = useTranslation();
  const add = useAddStatus();
  const update = useUpdateStatus();
  const [name, setName] = useState(status?.name ?? '');
  const [category, setCategory] = useState<StatusCategory>(status?.category ?? 'TODO');
  const [submitted, setSubmitted] = useState(false);
  const [failure, setFailure] = useState<unknown>(null);

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitted(true);
    setFailure(null);
    if (name.trim() === '') return;
    const input = { name: name.trim(), category };
    const done = { onSuccess: onClose, onError: setFailure };
    if (status) update.mutate({ spaceId, statusId: status.id, input }, done);
    else add.mutate({ spaceId, input }, done);
  };

  return (
    <Modal opened onClose={onClose} title={t(status ? 'tasks.status.editTitle' : 'tasks.status.addTitle')} closeButtonProps={closeProps(t('tasks.close'))}>
      <form onSubmit={submit} noValidate>
        <Stack gap="sm">
          {failure !== null && <Alert color="red">{t(taskErrorKey(failure))}</Alert>}
          <TextInput
            label={t('tasks.status.name')}
            required
            value={name}
            onChange={(e) => setName(e.currentTarget.value)}
            error={submitted && name.trim() === '' ? t('tasks.validation.nameRequired') : undefined}
            size="md"
          />
          <Select
            label={t('tasks.status.category')}
            data={STATUS_CATEGORIES.map((c) => ({ value: c, label: t(`tasks.status.categories.${c}`) }))}
            value={category}
            onChange={(v) => v && setCategory(v as StatusCategory)}
            allowDeselect={false}
            size="md"
          />
          <Footer onClose={onClose} pending={add.isPending || update.isPending} />
        </Stack>
      </form>
    </Modal>
  );
}

/** Archive a status; tasks that use it can be moved to a replacement status. */
export function ArchiveStatusModal({ spaceId, status, others, onClose }: { spaceId: string; status: SpaceStatus; others: SpaceStatus[]; onClose: () => void }) {
  const { t } = useTranslation();
  const archive = useArchiveStatus();
  const [replacement, setReplacement] = useState<string | null>(null);
  const [failure, setFailure] = useState<unknown>(null);
  return (
    <Modal opened onClose={onClose} title={t('tasks.status.archiveTitle', { name: status.name })} closeButtonProps={closeProps(t('tasks.close'))}>
      <Stack gap="sm">
        {failure !== null && <Alert color="red">{t(taskErrorKey(failure))}</Alert>}
        <Text size="sm">{t('tasks.status.archiveHint')}</Text>
        <Select
          label={t('tasks.status.replacement')}
          data={others.map((s) => ({ value: s.id, label: s.name }))}
          value={replacement}
          onChange={setReplacement}
          clearable
          size="md"
        />
        <Group justify="flex-end">
          <Button variant="default" mih={44} onClick={onClose}>
            {t('tasks.cancel')}
          </Button>
          <Button
            color="red"
            mih={44}
            loading={archive.isPending}
            onClick={() => {
              setFailure(null);
              archive.mutate({ spaceId, statusId: status.id, replacementId: replacement ?? undefined }, { onSuccess: onClose, onError: setFailure });
            }}
          >
            {t('tasks.archive')}
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

// ---- custom fields -------------------------------------------------------------------------

/** Add (`field` null) or edit a custom field; the type is fixed after creation. */
export function CustomFieldFormModal({ spaceId, field, onClose }: { spaceId: string; field: CustomField | null; onClose: () => void }) {
  const { t } = useTranslation();
  const add = useAddCustomField();
  const update = useUpdateCustomField();
  const [name, setName] = useState(field?.name ?? '');
  const [type, setType] = useState<CustomFieldType>(field?.type ?? 'TEXT');
  const [required, setRequired] = useState(field?.required ?? false);
  const [options, setOptions] = useState<string[]>(field?.options ?? []);
  const [submitted, setSubmitted] = useState(false);
  const [failure, setFailure] = useState<unknown>(null);
  const isSelect = type === 'SELECT';
  const nameError = submitted && name.trim() === '' ? t('tasks.validation.nameRequired') : undefined;
  const optionsError = submitted && isSelect && options.length === 0 ? t('tasks.validation.optionsRequired') : undefined;

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitted(true);
    setFailure(null);
    if (name.trim() === '' || (isSelect && options.length === 0)) return;
    const base = { name: name.trim(), required, options: isSelect ? options : undefined };
    const done = { onSuccess: onClose, onError: setFailure };
    if (field) update.mutate({ spaceId, fieldId: field.id, input: base }, done);
    else add.mutate({ spaceId, input: { ...base, type } }, done);
  };

  return (
    <Modal opened onClose={onClose} title={t(field ? 'tasks.customField.editTitle' : 'tasks.customField.addTitle')} closeButtonProps={closeProps(t('tasks.close'))}>
      <form onSubmit={submit} noValidate>
        <Stack gap="sm">
          {failure !== null && <Alert color="red">{t(taskErrorKey(failure))}</Alert>}
          <TextInput label={t('tasks.customField.name')} required value={name} onChange={(e) => setName(e.currentTarget.value)} error={nameError} size="md" />
          <Select
            label={t('tasks.customField.type')}
            data={FIELD_TYPES.map((x) => ({ value: x, label: t(`tasks.customField.types.${x}`) }))}
            value={type}
            onChange={(v) => v && setType(v as CustomFieldType)}
            allowDeselect={false}
            disabled={!!field}
            size="md"
          />
          {isSelect && (
            <TagsInput
              label={t('tasks.customField.options')}
              description={t('tasks.customField.optionsHint')}
              value={options}
              onChange={setOptions}
              error={optionsError}
              splitChars={[',']}
              size="md"
            />
          )}
          <Checkbox label={t('tasks.customField.required')} checked={required} onChange={(e) => setRequired(e.currentTarget.checked)} />
          <Footer onClose={onClose} pending={add.isPending || update.isPending} />
        </Stack>
      </form>
    </Modal>
  );
}
