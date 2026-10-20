/** Directory filters and their (shareable) URL query-string representation. */

export type MemberStatus = 'ACTIVE' | 'ARCHIVED' | 'ALL';

export const SORT_OPTIONS = ['lastName,asc', 'lastName,desc', 'joinedAt,desc', 'joinedAt,asc'] as const;
export type MemberSort = (typeof SORT_OPTIONS)[number];

export const DEFAULT_SORT: MemberSort = 'lastName,asc';
export const PAGE_SIZE = 20;

export interface MemberFilters {
  q: string;
  facultyId: string | null;
  departmentId: string | null;
  divisionId: string | null;
  positionId: string | null;
  /** Id of an activity level definition (INC-022). */
  activityLevelId: string | null;
  status: MemberStatus;
  sort: MemberSort;
  /** Zero-based page index (the URL carries it one-based). */
  page: number;
}

export const EMPTY_FILTERS: MemberFilters = {
  q: '',
  facultyId: null,
  departmentId: null,
  divisionId: null,
  positionId: null,
  activityLevelId: null,
  status: 'ACTIVE',
  sort: DEFAULT_SORT,
  page: 0,
};

const PARAMS = {
  q: 'q',
  facultyId: 'faculty',
  departmentId: 'department',
  divisionId: 'division',
  positionId: 'position',
  activityLevelId: 'level',
  status: 'status',
  sort: 'sort',
  page: 'page',
} as const;

function isStatus(v: string | null): v is MemberStatus {
  return v === 'ACTIVE' || v === 'ARCHIVED' || v === 'ALL';
}

function isSort(v: string | null): v is MemberSort {
  return SORT_OPTIONS.some((s) => s === v);
}

/** Reads filters from a query string; unknown or malformed values fall back to the defaults. */
export function parseFilters(params: URLSearchParams): MemberFilters {
  const page = Number.parseInt(params.get(PARAMS.page) ?? '', 10);
  const status = params.get(PARAMS.status);
  const sort = params.get(PARAMS.sort);
  const id = (key: string) => params.get(key)?.trim() || null;
  return {
    q: params.get(PARAMS.q) ?? '',
    facultyId: id(PARAMS.facultyId),
    departmentId: id(PARAMS.departmentId),
    divisionId: id(PARAMS.divisionId),
    positionId: id(PARAMS.positionId),
    activityLevelId: id(PARAMS.activityLevelId),
    status: isStatus(status) ? status : 'ACTIVE',
    sort: isSort(sort) ? sort : DEFAULT_SORT,
    page: Number.isFinite(page) && page > 1 ? page - 1 : 0,
  };
}

/** Writes only non-default values, so the default view has a clean URL. */
export function toSearchParams(f: MemberFilters): URLSearchParams {
  const p = new URLSearchParams();
  if (f.q.trim()) p.set(PARAMS.q, f.q.trim());
  if (f.facultyId) p.set(PARAMS.facultyId, f.facultyId);
  if (f.departmentId) p.set(PARAMS.departmentId, f.departmentId);
  if (f.divisionId) p.set(PARAMS.divisionId, f.divisionId);
  if (f.positionId) p.set(PARAMS.positionId, f.positionId);
  if (f.activityLevelId) p.set(PARAMS.activityLevelId, f.activityLevelId);
  if (f.status !== 'ACTIVE') p.set(PARAMS.status, f.status);
  if (f.sort !== DEFAULT_SORT) p.set(PARAMS.sort, f.sort);
  if (f.page > 0) p.set(PARAMS.page, String(f.page + 1));
  return p;
}

/** Number of narrowing filters (search text, sort and page do not count), for the mobile filter button badge. */
export function activeFilterCount(f: MemberFilters): number {
  return [f.facultyId, f.departmentId, f.divisionId, f.positionId, f.activityLevelId].filter(Boolean).length +
    (f.status !== 'ACTIVE' ? 1 : 0);
}

/** A change of any criterion goes back to the first page. */
export function withFilter(f: MemberFilters, change: Partial<Omit<MemberFilters, 'page'>>): MemberFilters {
  return { ...f, ...change, page: 0 };
}
