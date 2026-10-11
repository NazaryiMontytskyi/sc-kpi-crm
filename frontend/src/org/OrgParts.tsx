import { Alert, Anchor, Button, Group, Modal, Select, Stack, Text, TextInput } from '@mantine/core';
import { useMediaQuery } from '@mantine/hooks';
import { useQuery } from '@tanstack/react-query';
import type { FormEvent, ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { getMember } from '../members/memberApi';
import { fullName } from '../members/memberFormat';
import { MEMBERS_KEY, useMembers } from '../members/useMembers';
import type { OrgUnitType } from './orgApi';

export const TOUCH = 44;
/** Permissions that gate the manage actions (the backend stays authoritative). */
export const ORG_MANAGE = 'org.manage';
export const POSITIONS_MANAGE = 'positions.manage';
export const POSITIONS_APPOINT = 'positions.appoint';

export function unitTypeKey(type: OrgUnitType): string {
  return `org.type.${type}`;
}

/**
 * Display name of a member, resolved from the shared directory cache; a member missing from it (archived, for
 * users without `members.archive`) is fetched by id, so names never degrade to raw ids.
 */
export function useMemberLabel(id: string): string {
  const { t } = useTranslation();
  const directory = useMembers(false);
  const known = directory.data?.find((m) => m.id === id);
  const fallback = useQuery({
    queryKey: [...MEMBERS_KEY, 'one', id],
    queryFn: () => getMember(id),
    enabled: !known && directory.isSuccess,
  });
  const person = known ?? fallback.data;
  if (person) return fullName(person);
  return fallback.isError ? t('org.unknownMember') : '…';
}

/** Member name linking to the profile. */
export function MemberName({ id }: { id: string }) {
  const label = useMemberLabel(id);
  return (
    <Anchor component={Link} to={`/members/${id}`} style={{ overflowWrap: 'anywhere' }}>
      {label}
    </Anchor>
  );
}

interface MemberPickerProps {
  label: string;
  value: string | null;
  onChange: (id: string | null) => void;
  /** Members that must not be offered (already in the unit / already holding the position). */
  excludeIds?: readonly string[];
  error?: string;
}

/** Searchable picker of active members; archived members are never offered. */
export function MemberPicker({ label, value, onChange, excludeIds = [], error }: MemberPickerProps) {
  const { t } = useTranslation();
  const members = useMembers(false);
  const data = (members.data ?? [])
    .filter((m) => !m.archived && !excludeIds.includes(m.id))
    .map((m) => ({ value: m.id, label: fullName(m) }));
  return (
    <Select
      label={label}
      placeholder={t('org.memberPlaceholder')}
      nothingFoundMessage={t('org.memberNothing')}
      searchable
      data={data}
      value={value}
      onChange={onChange}
      error={error ? t(error) : undefined}
      disabled={members.isLoading}
      comboboxProps={{ withinPortal: true }}
      styles={{ input: { minHeight: TOUCH } }}
    />
  );
}

interface DateFieldProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  error?: string | null;
  max?: string;
  min?: string;
}

/** Native date input (calendar date, no time zone), the same pattern as the member form. */
export function DateField({ label, value, onChange, error, max, min }: DateFieldProps) {
  const { t } = useTranslation();
  return (
    <TextInput
      type="date"
      label={label}
      value={value}
      max={max}
      min={min}
      onChange={(e) => onChange(e.currentTarget.value)}
      error={error ? t(error) : undefined}
      styles={{ input: { minHeight: TOUCH } }}
    />
  );
}

interface OrgModalProps {
  title: string;
  onClose: () => void;
  onSubmit: () => void;
  submitLabel: string;
  pending?: boolean;
  /** i18n key of a general (non-field) error. */
  errorKey?: string | null;
  /** Extra explanation shown under the error (e.g. why archiving is blocked). */
  errorHint?: string | null;
  danger?: boolean;
  children: ReactNode;
}

/** Dialog shell: full-screen below 48em (375 px phones), centered above; 44 px buttons, stacked on phones. */
export function OrgModal({
  title,
  onClose,
  onSubmit,
  submitLabel,
  pending,
  errorKey,
  errorHint,
  danger,
  children,
}: OrgModalProps) {
  const { t } = useTranslation();
  const isMobile = useMediaQuery('(max-width: 47.99em)');
  const submit = (e: FormEvent) => {
    e.preventDefault();
    onSubmit();
  };
  return (
    <Modal opened onClose={onClose} title={title} fullScreen={!!isMobile} centered={!isMobile}>
      <form onSubmit={submit} noValidate>
        <Stack gap="md">
          {errorKey && (
            <Alert color="red" role="alert">
              <Text size="sm">{t(errorKey)}</Text>
              {errorHint && (
                <Text size="sm" mt={4}>
                  {errorHint}
                </Text>
              )}
            </Alert>
          )}
          {children}
          <Group justify="flex-end" gap="xs" wrap="wrap">
            <Button variant="default" mih={TOUCH} onClick={onClose}>
              {t('org.cancel')}
            </Button>
            <Button type="submit" color={danger ? 'red' : undefined} mih={TOUCH} loading={pending}>
              {submitLabel}
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}
