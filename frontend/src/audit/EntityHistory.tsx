import { Alert, Loader, Pagination, Stack, Text } from '@mantine/core';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { problemMessage } from '../api/problem';
import { AuditEntryCard } from './AuditEntryCard';
import { useEntityHistory } from './useAudit';

export interface EntityHistoryProps {
  /** Entity type name exactly as audited, e.g. "Role". */
  entityType: string;
  entityId: string;
}

/**
 * Change history of one object (who, what, when, field-level before/after). Reusable on any object page:
 * `<EntityHistory entityType="Role" entityId={id} />`. Visible to every authenticated user; the backend decides.
 */
export function EntityHistory({ entityType, entityId }: EntityHistoryProps) {
  const { t } = useTranslation();
  const [page, setPage] = useState(0);
  const query = useEntityHistory(entityType, entityId, page);
  const data = query.data;
  const errorKey = query.isError ? problemMessage(query.error).key : null;

  return (
    <Stack gap="sm" data-testid="entity-history">
      {query.isLoading && <Loader size="sm" />}
      {errorKey && <Alert color="red">{t(errorKey === 'problems.UNKNOWN' ? 'audit.loadFailed' : errorKey)}</Alert>}
      {data && data.entries.length === 0 && <Text c="dimmed">{t('audit.historyEmpty')}</Text>}
      {data?.entries.map((e) => <AuditEntryCard key={e.id} entry={e} />)}
      {data && data.totalPages > 1 && (
        <Pagination total={data.totalPages} value={page + 1} onChange={(p) => setPage(p - 1)} size="md" />
      )}
    </Stack>
  );
}
