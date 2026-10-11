import { MultiSelect, NumberInput, Switch, Text, TextInput } from '@mantine/core';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useRoles } from '../roles/useRoles';
import type { Appointment, Position } from './orgApi';
import { todayKyiv } from './orgDates';
import { orgErrorKey } from './orgProblems';
import { validateAppointmentDate, validatePosition, type PositionFormValues } from './orgValidation';
import { DateField, MemberPicker, OrgModal, TOUCH, useMemberLabel } from './OrgParts';
import { useOrgMutations } from './useOrg';

interface PositionFormProps {
  /** Position being edited; null creates one in `unitId`. */
  position: Position | null;
  unitId: string;
  /** The unit already has an active head position, so another one cannot be created. */
  unitHasHead: boolean;
  onClose: () => void;
}

/** Create / edit a position: title, head flag (create only), capacity and the roles bound to it. */
export function PositionFormModal({ position, unitId, unitHasHead, onClose }: PositionFormProps) {
  const { t } = useTranslation();
  const mutations = useOrgMutations();
  const roles = useRoles(true);
  const creating = position === null;
  const [values, setValues] = useState<PositionFormValues>({
    title: position?.title ?? '',
    capacity: position?.capacity ?? 1,
  });
  const [isHead, setIsHead] = useState(position?.isHead ?? false);
  const [roleIds, setRoleIds] = useState<string[]>(position?.defaultRoleIds ?? []);
  const [errors, setErrors] = useState<ReturnType<typeof validatePosition>>({});
  const [errorKey, setErrorKey] = useState<string | null>(null);

  // Admin is never offered; an archived role stays listed only while it is still bound to this position.
  const options = (roles.data ?? [])
    .filter((r) => !r.system && (!r.archived || (position?.defaultRoleIds ?? []).includes(r.id)))
    .map((r) => ({ value: r.id, label: r.archived ? `${r.name} (${t('org.position.roleArchived')})` : r.name }));
  const capacityLocked = isHead;

  const submit = () => {
    setErrorKey(null);
    const found = validatePosition({ ...values, capacity: capacityLocked ? 1 : values.capacity });
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    const capacity = capacityLocked ? 1 : Number(values.capacity);
    const title = values.title.trim();
    const options = { onSuccess: onClose, onError: (e: unknown) => setErrorKey(orgErrorKey(e)) };
    if (creating) {
      mutations.createPosition.mutate({ unitId, input: { title, isHead, capacity, defaultRoleIds: roleIds } }, options);
    } else {
      mutations.updatePosition.mutate({ id: position.id, input: { title, capacity, defaultRoleIds: roleIds } }, options);
    }
  };

  return (
    <OrgModal
      title={t(creating ? 'org.position.createTitle' : 'org.position.editTitle')}
      onClose={onClose}
      onSubmit={submit}
      submitLabel={t('org.save')}
      pending={mutations.createPosition.isPending || mutations.updatePosition.isPending}
      errorKey={errorKey}
    >
      <TextInput
        label={t('org.position.title')}
        value={values.title}
        onChange={(e) => setValues((v) => ({ ...v, title: e.currentTarget.value }))}
        error={errors.title ? t(errors.title) : undefined}
        data-autofocus
        styles={{ input: { minHeight: TOUCH } }}
      />
      {creating && (
        <Switch
          label={t('org.position.isHead')}
          description={unitHasHead ? t('org.position.headExists') : t('org.position.isHeadHint')}
          checked={isHead}
          disabled={unitHasHead}
          onChange={(e) => setIsHead(e.currentTarget.checked)}
          styles={{ root: { minHeight: TOUCH } }}
        />
      )}
      <NumberInput
        label={t('org.position.capacity')}
        description={capacityLocked ? t('org.position.headCapacity') : undefined}
        value={capacityLocked ? 1 : values.capacity}
        onChange={(v) => setValues((s) => ({ ...s, capacity: v }))}
        disabled={capacityLocked}
        min={1}
        max={100}
        allowDecimal={false}
        error={errors.capacity ? t(errors.capacity) : undefined}
        styles={{ input: { minHeight: TOUCH } }}
      />
      <MultiSelect
        label={t('org.position.roles')}
        description={t('org.position.rolesHint')}
        placeholder={t('org.position.rolesPlaceholder')}
        nothingFoundMessage={t('org.position.rolesNothing')}
        data={options}
        value={roleIds}
        onChange={setRoleIds}
        searchable
        clearable
        styles={{ input: { minHeight: TOUCH } }}
      />
    </OrgModal>
  );
}

export type PositionAction = 'archive' | 'restore';

export function PositionActionModal({
  action,
  position,
  onClose,
}: {
  action: PositionAction;
  position: Position;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const mutations = useOrgMutations();
  const [errorKey, setErrorKey] = useState<string | null>(null);
  const mutation = action === 'archive' ? mutations.archivePosition : mutations.restorePosition;
  return (
    <OrgModal
      title={t(`org.position.${action}.title`)}
      onClose={onClose}
      onSubmit={() => {
        setErrorKey(null);
        mutation.mutate(position.id, { onSuccess: onClose, onError: (e) => setErrorKey(orgErrorKey(e)) });
      }}
      submitLabel={t(`org.position.${action}.submit`)}
      pending={mutation.isPending}
      errorKey={errorKey}
      danger={action === 'archive'}
    >
      <Text>{t(`org.position.${action}.confirm`, { title: position.title })}</Text>
    </OrgModal>
  );
}

interface AppointProps {
  position: Position;
  onClose: () => void;
}

/** Appoint a person to a position; the start date cannot be in the future. */
export function AppointModal({ position, onClose }: AppointProps) {
  const { t } = useTranslation();
  const mutations = useOrgMutations();
  const today = todayKyiv();
  const [userId, setUserId] = useState<string | null>(null);
  const [startDate, setStartDate] = useState(today);
  const [errors, setErrors] = useState<{ user?: string; date?: string }>({});
  const [errorKey, setErrorKey] = useState<string | null>(null);

  const submit = () => {
    setErrorKey(null);
    const found = {
      user: userId ? undefined : 'org.validation.required',
      date: validateAppointmentDate(startDate, today) ?? undefined,
    };
    setErrors(found);
    if (found.user || found.date || !userId) return;
    mutations.appoint.mutate(
      { positionId: position.id, userId, startDate },
      { onSuccess: onClose, onError: (e) => setErrorKey(orgErrorKey(e)) },
    );
  };

  return (
    <OrgModal
      title={t('org.appointment.appointTitle', { title: position.title })}
      onClose={onClose}
      onSubmit={submit}
      submitLabel={t('org.appointment.appointSubmit')}
      pending={mutations.appoint.isPending}
      errorKey={errorKey}
    >
      <Text size="sm" c="dimmed">
        {t('org.appointment.appointHint')}
      </Text>
      <MemberPicker
        label={t('org.appointment.person')}
        value={userId}
        onChange={setUserId}
        excludeIds={position.activeHolderIds}
        error={errors.user}
      />
      <DateField
        label={t('org.appointment.startDate')}
        value={startDate}
        onChange={setStartDate}
        max={today}
        error={errors.date}
      />
    </OrgModal>
  );
}

/** End an appointment; the end date cannot be in the future or before the start. */
export function EndAppointmentModal({ appointment, onClose }: { appointment: Appointment; onClose: () => void }) {
  const { t } = useTranslation();
  const mutations = useOrgMutations();
  const today = todayKyiv();
  const name = useMemberLabel(appointment.userId);
  const [endDate, setEndDate] = useState(today);
  const [error, setError] = useState<string | null>(null);
  const [errorKey, setErrorKey] = useState<string | null>(null);

  const submit = () => {
    setErrorKey(null);
    const found = validateAppointmentDate(endDate, today, appointment.startDate);
    setError(found);
    if (found) return;
    mutations.endAppointment.mutate(
      { id: appointment.id, endDate },
      { onSuccess: onClose, onError: (e) => setErrorKey(orgErrorKey(e)) },
    );
  };

  return (
    <OrgModal
      title={t('org.appointment.endTitle')}
      onClose={onClose}
      onSubmit={submit}
      submitLabel={t('org.appointment.endSubmit')}
      pending={mutations.endAppointment.isPending}
      errorKey={errorKey}
      danger
    >
      <Text>{t('org.appointment.endConfirm', { name })}</Text>
      <Text size="sm" c="dimmed">
        {t('org.appointment.endHint')}
      </Text>
      <DateField
        label={t('org.appointment.endDate')}
        value={endDate}
        onChange={setEndDate}
        min={appointment.startDate}
        max={today}
        error={error}
      />
    </OrgModal>
  );
}
