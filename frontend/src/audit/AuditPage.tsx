import { Alert, Button, Group, Loader, Pagination, Select, SimpleGrid, Stack, Text, TextInput, Title } from '@mantine/core';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { problemMessage } from '../api/problem';
import { usePageTitle } from '../layout/pageTitle';
import { AuditEntryCard } from './AuditEntryCard';
import { AUDIT_ACTIONS, type AuditFilters } from './auditApi';
import { kyivDayStartIso } from './kyivTime';
import { useAuditLog } from './useAudit';

const TOUCH = 44;
/** Permission that guards the global log (the backend is authoritative). */
export const AUDIT_VIEW = 'audit.view';

interface FormState {
  actorId: string;
  entityType: string;
  entityId: string;
  action: string;
  /** `YYYY-MM-DD`, Europe/Kyiv day. */
  from: string;
  to: string;
}

const EMPTY: FormState = { actorId: '', entityType: '', entityId: '', action: '', from: '', to: '' };

/** Turns the form into API filters: whole Kyiv days, `to` is inclusive for the user (exclusive next-day start). */
export function toFilters(form: FormState): AuditFilters {
  return {
    actorId: form.actorId,
    entityType: form.entityType,
    entityId: form.entityId,
    action: form.action,
    from: form.from ? kyivDayStartIso(form.from) : undefined,
    to: form.to ? kyivDayStartIso(form.to, 1) : undefined,
  };
}

/** Global audit log with filters by person, object, action and period. Needs `audit.view`. */
export function AuditPage() {
  const { t } = useTranslation();
  usePageTitle('nav.audit');
  const [form, setForm] = useState<FormState>(EMPTY);
  const [applied, setApplied] = useState<AuditFilters>({});
  const [page, setPage] = useState(0);
  const query = useAuditLog(applied, page);
  const data = query.data;
  const errorKey = query.isError ? problemMessage(query.error).key : null;

  const set = (key: keyof FormState) => (value: string) => setForm((f) => ({ ...f, [key]: value }));

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setPage(0);
    setApplied(toFilters(form));
  };
  const reset = () => {
    setForm(EMPTY);
    setPage(0);
    setApplied({});
  };

  return (
    <Stack gap="md">
      <Title order={1} size="h2">
        {t('audit.title')}
      </Title>
      <Text c="dimmed">{t('audit.description')}</Text>

      <form onSubmit={submit} aria-label={t('audit.filters')}>
        <Stack gap="sm">
          <SimpleGrid cols={{ base: 1, sm: 2, lg: 3 }} spacing="sm">
            <TextInput
              label={t('audit.filterActor')}
              description={t('audit.filterIdHint')}
              value={form.actorId}
              onChange={(e) => set('actorId')(e.currentTarget.value)}
              styles={{ input: { minHeight: TOUCH } }}
            />
            <TextInput
              label={t('audit.filterEntityType')}
              value={form.entityType}
              onChange={(e) => set('entityType')(e.currentTarget.value)}
              styles={{ input: { minHeight: TOUCH } }}
            />
            <TextInput
              label={t('audit.filterEntityId')}
              description={t('audit.filterIdHint')}
              value={form.entityId}
              onChange={(e) => set('entityId')(e.currentTarget.value)}
              styles={{ input: { minHeight: TOUCH } }}
            />
            <Select
              label={t('audit.filterAction')}
              clearable
              data={AUDIT_ACTIONS.map((a) => ({ value: a, label: t(`audit.actions.${a}`) }))}
              value={form.action || null}
              onChange={(v) => set('action')(v ?? '')}
              comboboxProps={{ withinPortal: true }}
              styles={{ input: { minHeight: TOUCH } }}
            />
            <TextInput
              type="date"
              label={t('audit.filterFrom')}
              value={form.from}
              onChange={(e) => set('from')(e.currentTarget.value)}
              styles={{ input: { minHeight: TOUCH } }}
            />
            <TextInput
              type="date"
              label={t('audit.filterTo')}
              value={form.to}
              onChange={(e) => set('to')(e.currentTarget.value)}
              styles={{ input: { minHeight: TOUCH } }}
            />
          </SimpleGrid>
          <Group>
            <Button type="submit" mih={TOUCH}>
              {t('audit.apply')}
            </Button>
            <Button type="button" variant="default" mih={TOUCH} onClick={reset}>
              {t('audit.reset')}
            </Button>
          </Group>
        </Stack>
      </form>

      {query.isLoading && <Loader size="sm" />}
      {errorKey && <Alert color="red">{t(errorKey === 'problems.UNKNOWN' ? 'audit.loadFailed' : errorKey)}</Alert>}
      {data && data.entries.length === 0 && <Text c="dimmed">{t('audit.empty_list')}</Text>}
      <Stack gap="sm">
        {data?.entries.map((e) => <AuditEntryCard key={e.id} entry={e} showEntity />)}
      </Stack>
      {data && data.totalPages > 1 && (
        <Pagination total={data.totalPages} value={page + 1} onChange={(p) => setPage(p - 1)} size="md" />
      )}
    </Stack>
  );
}
