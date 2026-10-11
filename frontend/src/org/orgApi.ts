import { api, type components } from '../api/client';
import { toProblemError } from '../api/problem';

type Schemas = components['schemas'];

export type OrgUnitType = NonNullable<Schemas['OrgUnitTreeNode']['type']>;

export interface OrgNode {
  id: string;
  name: string;
  type: OrgUnitType;
  description: string;
  archived: boolean;
  children: OrgNode[];
}

export interface OrgUnit {
  id: string;
  name: string;
  type: OrgUnitType;
  parentId: string | null;
  description: string;
  archived: boolean;
  ancestorIds: string[];
  childIds: string[];
}

export interface Membership {
  id: string;
  userId: string;
  orgUnitId: string;
  since: string;
  until: string | null;
  active: boolean;
}

export interface Position {
  id: string;
  title: string;
  orgUnitId: string;
  isHead: boolean;
  capacity: number;
  defaultRoleIds: string[];
  activeHolderIds: string[];
  vacant: boolean;
  archived: boolean;
}

export interface Appointment {
  id: string;
  positionId: string;
  userId: string;
  startDate: string;
  endDate: string | null;
  active: boolean;
}

export interface UnitInput {
  name: string;
  description?: string;
}
export interface CreateUnitInput extends UnitInput {
  type: OrgUnitType;
  parentId?: string;
}
export interface CreatePositionInput {
  title: string;
  isHead: boolean;
  capacity: number;
  defaultRoleIds: string[];
}
export interface UpdatePositionInput {
  title: string;
  capacity: number;
  defaultRoleIds: string[];
}

/** Backend maximum page size. */
const PAGE_SIZE = 100;
/** Safety cap so a misbehaving server cannot make the client loop forever. */
const MAX_PAGES = 50;

interface PageLike<T> {
  content?: T[];
  last?: boolean;
}

/** Reads every page of a paginated endpoint. */
async function fetchAll<T, R>(
  load: (page: number) => Promise<{ data?: PageLike<T>; error?: unknown; response: Response }>,
  map: (item: T) => R,
): Promise<R[]> {
  const all: R[] = [];
  for (let page = 0; page < MAX_PAGES; page++) {
    const { data, error, response } = await load(page);
    if (!data) throw toProblemError(response, error);
    all.push(...(data.content ?? []).map(map));
    if (data.last !== false) break;
  }
  return all;
}

function toNode(n: Schemas['OrgUnitTreeNode']): OrgNode {
  return {
    id: n.id ?? '',
    name: n.name ?? '',
    type: n.type ?? 'DEPARTMENT',
    description: n.description ?? '',
    archived: n.archived === true,
    children: (n.children ?? []).map(toNode),
  };
}

function toUnit(u: Schemas['OrgUnitResponse']): OrgUnit {
  return {
    id: u.id ?? '',
    name: u.name ?? '',
    type: u.type ?? 'DEPARTMENT',
    parentId: u.parentId ?? null,
    description: u.description ?? '',
    archived: u.archived === true,
    ancestorIds: u.ancestorIds ?? [],
    childIds: u.childIds ?? [],
  };
}

function toMembership(m: Schemas['MembershipResponse']): Membership {
  return {
    id: m.id ?? '',
    userId: m.userId ?? '',
    orgUnitId: m.orgUnitId ?? '',
    since: m.since ?? '',
    until: m.until ?? null,
    active: m.active === true,
  };
}

function toPosition(p: Schemas['PositionResponse']): Position {
  return {
    id: p.id ?? '',
    title: p.title ?? '',
    orgUnitId: p.orgUnitId ?? '',
    isHead: p.isHead === true,
    capacity: p.capacity ?? 1,
    defaultRoleIds: p.defaultRoleIds ?? [],
    activeHolderIds: p.activeHolderIds ?? [],
    vacant: p.vacant === true,
    archived: p.archived === true,
  };
}

function toAppointment(a: Schemas['AppointmentResponse']): Appointment {
  return {
    id: a.id ?? '',
    positionId: a.positionId ?? '',
    userId: a.userId ?? '',
    startDate: a.startDate ?? '',
    endDate: a.endDate ?? null,
    active: a.active === true,
  };
}

// ---- units -----------------------------------------------------------------------------------

export async function getOrgTree(includeArchived: boolean): Promise<OrgNode[]> {
  const { data, error, response } = await api.GET('/api/v1/org-units/tree', {
    params: { query: { includeArchived } },
  });
  if (!data) throw toProblemError(response, error);
  return data.map(toNode);
}

export async function getOrgUnit(id: string): Promise<OrgUnit> {
  const { data, error, response } = await api.GET('/api/v1/org-units/{id}', { params: { path: { id } } });
  if (!data) throw toProblemError(response, error);
  return toUnit(data);
}

export async function createOrgUnit(input: CreateUnitInput): Promise<OrgUnit> {
  const { data, error, response } = await api.POST('/api/v1/org-units', { body: input });
  if (!data) throw toProblemError(response, error);
  return toUnit(data);
}

export async function updateOrgUnit(id: string, input: UnitInput): Promise<OrgUnit> {
  const { data, error, response } = await api.PUT('/api/v1/org-units/{id}', {
    params: { path: { id } },
    body: input,
  });
  if (!data) throw toProblemError(response, error);
  return toUnit(data);
}

/** `parentId` null moves the unit to the top level. */
export async function moveOrgUnit(id: string, parentId: string | null): Promise<OrgUnit> {
  const { data, error, response } = await api.POST('/api/v1/org-units/{id}/move', {
    params: { path: { id } },
    body: parentId ? { parentId } : {},
  });
  if (!data) throw toProblemError(response, error);
  return toUnit(data);
}

export async function archiveOrgUnit(id: string): Promise<OrgUnit> {
  const { data, error, response } = await api.POST('/api/v1/org-units/{id}/archive', { params: { path: { id } } });
  if (!data) throw toProblemError(response, error);
  return toUnit(data);
}

export async function restoreOrgUnit(id: string): Promise<OrgUnit> {
  const { data, error, response } = await api.POST('/api/v1/org-units/{id}/restore', { params: { path: { id } } });
  if (!data) throw toProblemError(response, error);
  return toUnit(data);
}

// ---- memberships -----------------------------------------------------------------------------

export function listUnitMemberships(unitId: string, includeEnded: boolean): Promise<Membership[]> {
  return fetchAll(
    (page) =>
      api.GET('/api/v1/org-units/{id}/memberships', {
        params: { path: { id: unitId }, query: { page: { page, size: PAGE_SIZE }, includeEnded } },
      }),
    toMembership,
  );
}

export async function startMembership(unitId: string, userId: string, since?: string): Promise<Membership> {
  const { data, error, response } = await api.POST('/api/v1/org-units/{id}/memberships', {
    params: { path: { id: unitId } },
    body: { userId, since },
  });
  if (!data) throw toProblemError(response, error);
  return toMembership(data);
}

export async function endMembership(id: string, until?: string): Promise<Membership> {
  const { data, error, response } = await api.POST('/api/v1/org-memberships/{id}/end', {
    params: { path: { id } },
    body: { until },
  });
  if (!data) throw toProblemError(response, error);
  return toMembership(data);
}

export async function moveMembership(id: string, targetOrgUnitId: string, date?: string): Promise<Membership> {
  const { data, error, response } = await api.POST('/api/v1/org-memberships/{id}/move', {
    params: { path: { id } },
    body: { targetOrgUnitId, date },
  });
  if (!data) throw toProblemError(response, error);
  return toMembership(data);
}

// ---- positions and appointments --------------------------------------------------------------

export function listPositions(unitId: string, includeArchived: boolean): Promise<Position[]> {
  return fetchAll(
    (page) =>
      api.GET('/api/v1/org-units/{id}/positions', {
        params: { path: { id: unitId }, query: { page: { page, size: PAGE_SIZE }, includeArchived } },
      }),
    toPosition,
  );
}

export async function getPosition(id: string): Promise<Position> {
  const { data, error, response } = await api.GET('/api/v1/positions/{id}', { params: { path: { id } } });
  if (!data) throw toProblemError(response, error);
  return toPosition(data);
}

export async function createPosition(unitId: string, input: CreatePositionInput): Promise<Position> {
  const { data, error, response } = await api.POST('/api/v1/org-units/{id}/positions', {
    params: { path: { id: unitId } },
    body: input,
  });
  if (!data) throw toProblemError(response, error);
  return toPosition(data);
}

export async function updatePosition(id: string, input: UpdatePositionInput): Promise<Position> {
  const { data, error, response } = await api.PUT('/api/v1/positions/{id}', {
    params: { path: { id } },
    body: input,
  });
  if (!data) throw toProblemError(response, error);
  return toPosition(data);
}

export async function archivePosition(id: string): Promise<Position> {
  const { data, error, response } = await api.POST('/api/v1/positions/{id}/archive', { params: { path: { id } } });
  if (!data) throw toProblemError(response, error);
  return toPosition(data);
}

export async function restorePosition(id: string): Promise<Position> {
  const { data, error, response } = await api.POST('/api/v1/positions/{id}/restore', { params: { path: { id } } });
  if (!data) throw toProblemError(response, error);
  return toPosition(data);
}

/** Who held the position and when (newest first), ended appointments included. */
export function listPositionHistory(positionId: string): Promise<Appointment[]> {
  return fetchAll(
    (page) =>
      api.GET('/api/v1/positions/{id}/appointments', {
        params: { path: { id: positionId }, query: { page: { page, size: PAGE_SIZE }, includeEnded: true } },
      }),
    toAppointment,
  );
}

export async function appoint(positionId: string, userId: string, startDate?: string): Promise<Appointment> {
  const { data, error, response } = await api.POST('/api/v1/positions/{id}/appointments', {
    params: { path: { id: positionId } },
    body: { userId, startDate },
  });
  if (!data) throw toProblemError(response, error);
  return toAppointment(data);
}

export async function endAppointment(id: string, endDate?: string): Promise<Appointment> {
  const { data, error, response } = await api.POST('/api/v1/appointments/{id}/end', {
    params: { path: { id } },
    body: { endDate },
  });
  if (!data) throw toProblemError(response, error);
  return toAppointment(data);
}
