import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { listAudit, listEntityHistory, type AuditFilters } from './auditApi';

export function useAuditLog(filters: AuditFilters, page: number) {
  return useQuery({
    queryKey: ['audit', 'log', filters, page],
    queryFn: () => listAudit(filters, page),
    placeholderData: keepPreviousData,
  });
}

export function useEntityHistory(entityType: string, entityId: string, page: number) {
  return useQuery({
    queryKey: ['audit', 'entity', entityType, entityId, page],
    queryFn: () => listEntityHistory(entityType, entityId, page),
    placeholderData: keepPreviousData,
  });
}
