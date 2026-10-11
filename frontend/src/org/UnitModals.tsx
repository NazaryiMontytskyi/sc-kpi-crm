import { Select, Text, Textarea, TextInput } from '@mantine/core';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { Membership, OrgNode, OrgUnit, OrgUnitType } from './orgApi';
import { todayKyiv } from './orgDates';
import { orgErrorKey } from './orgProblems';
import { activeUnits, parentOptions, parentRequired, parentTypes, subtreeIds, findNode } from './orgTree';
import { validateMembershipDate, validateUnit, type UnitFormValues } from './orgValidation';
import { DateField, MemberPicker, OrgModal, TOUCH, unitTypeKey, useMemberLabel } from './OrgParts';
import { useOrgMutations } from './useOrg';

const UNIT_TYPES: OrgUnitType[] = ['LEADERSHIP', 'DEPARTMENT', 'DIVISION', 'WORKING_GROUP'];

interface UnitFormProps {
  /** Unit being renamed; null creates a new one. */
  unit: OrgUnit | null;
  tree: readonly OrgNode[];
  onClose: () => void;
  onSaved?: (unit: OrgUnit) => void;
}

/** Create (name, type, parent, description) or rename/describe (name, description) a unit. */
export function UnitFormModal({ unit, tree, onClose, onSaved }: UnitFormProps) {
  const { t } = useTranslation();
  const mutations = useOrgMutations();
  const [values, setValues] = useState<UnitFormValues>({
    name: unit?.name ?? '',
    description: unit?.description ?? '',
    type: unit?.type ?? null,
    parentId: null,
  });
  const [errors, setErrors] = useState<ReturnType<typeof validateUnit>>({});
  const [errorKey, setErrorKey] = useState<string | null>(null);
  const creating = unit === null;
  const type = values.type as OrgUnitType | null;
  const hasLeadership = activeUnits(tree).some((u) => u.type === 'LEADERSHIP');
  const typeOptions = UNIT_TYPES.filter((x) => !(x === 'LEADERSHIP' && hasLeadership)).map((x) => ({
    value: x,
    label: t(unitTypeKey(x)),
  }));
  const parents = type ? parentOptions(tree, type) : [];
  const showParent = creating && !!type && parentTypes(type) !== 'none';
  const set = <K extends keyof UnitFormValues>(key: K, value: UnitFormValues[K]) =>
    setValues((v) => ({ ...v, [key]: value }));

  const submit = () => {
    setErrorKey(null);
    const found = validateUnit(values, { withType: creating, parentRequired: !!type && parentRequired(type) });
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    const description = values.description.trim() || undefined;
    const onError = (e: unknown) => setErrorKey(orgErrorKey(e));
    if (creating) {
      mutations.createUnit.mutate(
        { name: values.name.trim(), type: values.type as OrgUnitType, parentId: values.parentId ?? undefined, description },
        {
          onSuccess: (created) => {
            onSaved?.(created);
            onClose();
          },
          onError,
        },
      );
    } else {
      mutations.updateUnit.mutate(
        { id: unit.id, input: { name: values.name.trim(), description: description ?? '' } },
        { onSuccess: onClose, onError },
      );
    }
  };

  return (
    <OrgModal
      title={t(creating ? 'org.unit.createTitle' : 'org.unit.editTitle')}
      onClose={onClose}
      onSubmit={submit}
      submitLabel={t('org.save')}
      pending={mutations.createUnit.isPending || mutations.updateUnit.isPending}
      errorKey={errorKey}
    >
      <TextInput
        label={t('org.unit.name')}
        value={values.name}
        onChange={(e) => set('name', e.currentTarget.value)}
        error={errors.name ? t(errors.name) : undefined}
        data-autofocus
        styles={{ input: { minHeight: TOUCH } }}
      />
      {creating && (
        <Select
          label={t('org.unit.type')}
          data={typeOptions}
          value={values.type}
          onChange={(v) => setValues((s) => ({ ...s, type: v, parentId: null }))}
          error={errors.type ? t(errors.type) : undefined}
          allowDeselect={false}
          styles={{ input: { minHeight: TOUCH } }}
        />
      )}
      {showParent && (
        <Select
          label={t('org.unit.parent')}
          description={parentRequired(type) ? undefined : t('org.unit.parentOptional')}
          placeholder={t('org.unit.parentPlaceholder')}
          nothingFoundMessage={t('org.unit.parentNothing')}
          data={parents.map((p) => ({ value: p.id, label: p.name }))}
          value={values.parentId}
          onChange={(v) => set('parentId', v)}
          clearable={!parentRequired(type)}
          searchable
          error={errors.parentId ? t(errors.parentId) : undefined}
          styles={{ input: { minHeight: TOUCH } }}
        />
      )}
      <Textarea
        label={t('org.unit.description')}
        value={values.description}
        onChange={(e) => set('description', e.currentTarget.value)}
        error={errors.description ? t(errors.description) : undefined}
        autosize
        minRows={3}
        maxRows={8}
      />
    </OrgModal>
  );
}

interface UnitMoveProps {
  unit: OrgUnit;
  tree: readonly OrgNode[];
  onClose: () => void;
}

/** Move a unit under another valid parent (or to the top level when its type allows it). */
export function UnitMoveModal({ unit, tree, onClose }: UnitMoveProps) {
  const { t } = useTranslation();
  const mutations = useOrgMutations();
  const node = findNode(tree, unit.id);
  const options = parentOptions(tree, unit.type, node ? subtreeIds(node) : new Set([unit.id]));
  const [parentId, setParentId] = useState<string | null>(unit.parentId);
  const [error, setError] = useState<string | null>(null);
  const [errorKey, setErrorKey] = useState<string | null>(null);
  const mandatory = parentRequired(unit.type);

  const submit = () => {
    setErrorKey(null);
    if (mandatory && !parentId) {
      setError('org.validation.required');
      return;
    }
    setError(null);
    mutations.moveUnit.mutate({ id: unit.id, parentId }, { onSuccess: onClose, onError: (e) => setErrorKey(orgErrorKey(e)) });
  };

  return (
    <OrgModal
      title={t('org.unit.moveTitle')}
      onClose={onClose}
      onSubmit={submit}
      submitLabel={t('org.unit.moveSubmit')}
      pending={mutations.moveUnit.isPending}
      errorKey={errorKey}
    >
      <Text size="sm">{t('org.unit.moveHint', { name: unit.name })}</Text>
      <Select
        label={t('org.unit.newParent')}
        placeholder={t('org.unit.topLevel')}
        nothingFoundMessage={t('org.unit.parentNothing')}
        data={options.map((p) => ({ value: p.id, label: p.name }))}
        value={parentId}
        onChange={setParentId}
        clearable={!mandatory}
        searchable
        error={error ? t(error) : undefined}
        styles={{ input: { minHeight: TOUCH } }}
      />
    </OrgModal>
  );
}

export type UnitAction = 'archive' | 'restore';

interface UnitActionProps {
  action: UnitAction;
  unit: OrgUnit;
  onClose: () => void;
}

/** Archive / restore confirmation. A vetoed archive explains what to end or move first. */
export function UnitActionModal({ action, unit, onClose }: UnitActionProps) {
  const { t } = useTranslation();
  const mutations = useOrgMutations();
  const [errorKey, setErrorKey] = useState<string | null>(null);
  const [blocked, setBlocked] = useState(false);
  const mutation = action === 'archive' ? mutations.archiveUnit : mutations.restoreUnit;

  const submit = () => {
    setErrorKey(null);
    setBlocked(false);
    mutation.mutate(unit.id, {
      onSuccess: onClose,
      onError: (e) => {
        const key = orgErrorKey(e);
        setErrorKey(key);
        setBlocked(key === 'problems.ORG_UNIT_ARCHIVE_BLOCKED' || key === 'problems.ORG_UNIT_HAS_ACTIVE_CHILDREN');
      },
    });
  };

  return (
    <OrgModal
      title={t(`org.unit.${action}.title`)}
      onClose={onClose}
      onSubmit={submit}
      submitLabel={t(`org.unit.${action}.submit`)}
      pending={mutation.isPending}
      errorKey={errorKey}
      errorHint={blocked ? t('org.unit.archive.blockedHint') : null}
      danger={action === 'archive'}
    >
      <Text>{t(`org.unit.${action}.confirm`, { name: unit.name })}</Text>
    </OrgModal>
  );
}

interface AddMemberProps {
  unit: OrgUnit;
  /** Ids of people who are already active members of the unit. */
  memberIds: readonly string[];
  onClose: () => void;
}

export function AddMemberModal({ unit, memberIds, onClose }: AddMemberProps) {
  const { t } = useTranslation();
  const mutations = useOrgMutations();
  const [userId, setUserId] = useState<string | null>(null);
  const [since, setSince] = useState(todayKyiv());
  const [errors, setErrors] = useState<{ user?: string; since?: string }>({});
  const [errorKey, setErrorKey] = useState<string | null>(null);

  const submit = () => {
    setErrorKey(null);
    const found = {
      user: userId ? undefined : 'org.validation.required',
      since: validateMembershipDate(since) ?? undefined,
    };
    setErrors(found);
    if (found.user || found.since || !userId) return;
    mutations.startMembership.mutate(
      { unitId: unit.id, userId, since },
      { onSuccess: onClose, onError: (e) => setErrorKey(orgErrorKey(e)) },
    );
  };

  return (
    <OrgModal
      title={t('org.membership.addTitle')}
      onClose={onClose}
      onSubmit={submit}
      submitLabel={t('org.membership.addSubmit')}
      pending={mutations.startMembership.isPending}
      errorKey={errorKey}
    >
      <MemberPicker
        label={t('org.membership.member')}
        value={userId}
        onChange={setUserId}
        excludeIds={memberIds}
        error={errors.user}
      />
      <DateField label={t('org.membership.since')} value={since} onChange={setSince} error={errors.since} />
    </OrgModal>
  );
}

interface MembershipActionProps {
  action: 'end' | 'move';
  membership: Membership;
  tree: readonly OrgNode[];
  onClose: () => void;
}

/** End a membership, or move the person to another unit, on a chosen date. */
export function MembershipActionModal({ action, membership, tree, onClose }: MembershipActionProps) {
  const { t } = useTranslation();
  const mutations = useOrgMutations();
  const personName = useMemberLabel(membership.userId);
  const [date, setDate] = useState(todayKyiv());
  const [target, setTarget] = useState<string | null>(null);
  const [errors, setErrors] = useState<{ date?: string; target?: string }>({});
  const [errorKey, setErrorKey] = useState<string | null>(null);
  const targets = activeUnits(tree, new Set([membership.orgUnitId]));
  const pending = mutations.endMembership.isPending || mutations.moveMembership.isPending;

  const submit = () => {
    setErrorKey(null);
    const found = {
      date: validateMembershipDate(date, membership.since) ?? undefined,
      target: action === 'move' && !target ? 'org.validation.required' : undefined,
    };
    setErrors(found);
    if (found.date || found.target) return;
    const options = { onSuccess: onClose, onError: (e: unknown) => setErrorKey(orgErrorKey(e)) };
    if (action === 'end') mutations.endMembership.mutate({ id: membership.id, until: date }, options);
    else if (target) mutations.moveMembership.mutate({ id: membership.id, targetUnitId: target, date }, options);
  };

  return (
    <OrgModal
      title={t(`org.membership.${action}.title`)}
      onClose={onClose}
      onSubmit={submit}
      submitLabel={t(`org.membership.${action}.submit`)}
      pending={pending}
      errorKey={errorKey}
      danger={action === 'end'}
    >
      <Text>{t(`org.membership.${action}.confirm`, { name: personName })}</Text>
      {action === 'move' && (
        <Select
          label={t('org.membership.targetUnit')}
          placeholder={t('org.membership.targetPlaceholder')}
          nothingFoundMessage={t('org.unit.parentNothing')}
          data={targets.map((u) => ({ value: u.id, label: `${u.name} (${t(unitTypeKey(u.type))})` }))}
          value={target}
          onChange={setTarget}
          searchable
          error={errors.target ? t(errors.target) : undefined}
          styles={{ input: { minHeight: TOUCH } }}
        />
      )}
      <DateField
        label={t(action === 'end' ? 'org.membership.until' : 'org.membership.moveDate')}
        value={date}
        onChange={setDate}
        min={membership.since}
        error={errors.date}
      />
    </OrgModal>
  );
}
