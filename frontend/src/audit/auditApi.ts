import { api, type components } from '../api/client';
import { toProblemError } from '../api/problem';

type AuditEntryResponse = components['schemas']['AuditEntryResponse'];

export const AUDIT_ACTIONS = [
  'CREATE',
  'UPDATE',
  'ARCHIVE',
  'RESTORE',
  'HARD_DELETE',
  'LOGIN',
  'LOGIN_FAILED',
  'LOGOUT',
  'PASSWORD_CHANGE',
  'ROLE_CHANGE',
  'PERMISSION_CHANGE',
  'APPOINT',
  'DISMISS',
  'VOTE',
  'VOTE_CHANGE',
  'HANDOVER',
] as const;

/** Audit record as used by the UI (the generated type marks every field optional). */
export interface AuditEntry {
  id: string;
  actorId: string | null;
  action: string;
  entityType: string;
  entityId: string | null;
  before: Record<string, unknown> | null;
  after: Record<string, unknown> | null;
  /** ISO instant (UTC). */
  at: string;
  ip: string | null;
}

export interface AuditPage {
  entries: AuditEntry[];
  page: number;
  totalPages: number;
  totalElements: number;
}

export interface AuditFilters {
  actorId?: string;
  entityType?: string;
  entityId?: string;
  action?: string;
  /** ISO-8601 instant, inclusive. */
  from?: string;
  /** ISO-8601 instant, exclusive. */
  to?: string;
}

export const AUDIT_PAGE_SIZE = 20;

function toEntry(e: AuditEntryResponse): AuditEntry {
  return {
    id: e.id ?? '',
    actorId: e.actorId ?? null,
    action: e.action ?? '',
    entityType: e.entityType ?? '',
    entityId: e.entityId ?? null,
    before: e.before ?? null,
    after: e.after ?? null,
    at: e.at ?? '',
    ip: e.ip ?? null,
  };
}

function toPage(data: components['schemas']['PageResponseAuditEntryResponse']): AuditPage {
  return {
    entries: (data.content ?? []).map(toEntry),
    page: data.page ?? 0,
    totalPages: data.totalPages ?? 0,
    totalElements: data.totalElements ?? 0,
  };
}

/** Drops empty filter values so they are not sent as blank query parameters. */
export function cleanFilters(filters: AuditFilters): AuditFilters {
  const out: AuditFilters = {};
  for (const [k, v] of Object.entries(filters) as [keyof AuditFilters, string | undefined][]) {
    if (v !== undefined && v.trim() !== '') out[k] = v.trim();
  }
  return out;
}

/** Global audit log (needs `audit.view`). `page` is zero-based. */
export async function listAudit(filters: AuditFilters, page: number): Promise<AuditPage> {
  const { data, error, response } = await api.GET('/api/v1/audit', {
    params: { query: { page, size: AUDIT_PAGE_SIZE, ...cleanFilters(filters) } },
  });
  if (!data) throw toProblemError(response, error);
  return toPage(data);
}

/** History of one object; visible to every authenticated user. */
export async function listEntityHistory(entityType: string, entityId: string, page: number): Promise<AuditPage> {
  const { data, error, response } = await api.GET('/api/v1/audit/entities/{type}/{id}', {
    params: { path: { type: entityType, id: entityId }, query: { page, size: AUDIT_PAGE_SIZE } },
  });
  if (!data) throw toProblemError(response, error);
  return toPage(data);
}
