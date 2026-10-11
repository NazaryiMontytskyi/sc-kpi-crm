import { api, type components } from '../api/client';
import { toProblemError } from '../api/problem';

export type RoleRequest = components['schemas']['RoleRequest'];
type RoleResponse = components['schemas']['RoleResponse'];

/** Role as used by the UI (the generated type marks every field optional). */
export interface Role {
  id: string;
  name: string;
  description: string;
  /** The built-in Admin role: read-only, cannot be granted or revoked. */
  system: boolean;
  permissions: string[];
  archived: boolean;
  assignedUserCount: number;
}

export interface PermissionItem {
  key: string;
  label: string;
}

export interface PermissionGroup {
  code: string;
  label: string;
  permissions: PermissionItem[];
}

export interface MemberOption {
  id: string;
  name: string;
}

/** Backend maximum page size; the number of roles and members stays far below it for one council. */
const PAGE_SIZE = 100;

function toRole(r: RoleResponse): Role {
  return {
    id: r.id ?? '',
    name: r.name ?? '',
    description: r.description ?? '',
    system: r.system === true,
    permissions: [...(r.permissions ?? [])],
    archived: r.archived === true,
    assignedUserCount: r.assignedUserCount ?? 0,
  };
}

export async function listRoles(includeArchived: boolean): Promise<Role[]> {
  const { data, error, response } = await api.GET('/api/v1/roles', {
    params: { query: { page: { size: PAGE_SIZE }, includeArchived } },
  });
  if (!data) throw toProblemError(response, error);
  return (data.content ?? []).map(toRole);
}

export async function createRole(body: RoleRequest): Promise<Role> {
  const { data, error, response } = await api.POST('/api/v1/roles', { body });
  if (!data) throw toProblemError(response, error);
  return toRole(data);
}

export async function updateRole(id: string, body: RoleRequest): Promise<Role> {
  const { data, error, response } = await api.PUT('/api/v1/roles/{id}', { params: { path: { id } }, body });
  if (!data) throw toProblemError(response, error);
  return toRole(data);
}

export async function archiveRole(id: string): Promise<Role> {
  const { data, error, response } = await api.POST('/api/v1/roles/{id}/archive', { params: { path: { id } } });
  if (!data) throw toProblemError(response, error);
  return toRole(data);
}

/** The permission catalog grouped as in CONTEXT.md 5.2, labels in the language of the signed-in user. */
export async function listPermissionGroups(): Promise<PermissionGroup[]> {
  const { data, error, response } = await api.GET('/api/v1/permissions');
  if (!data) throw toProblemError(response, error);
  return data.map((g) => ({
    code: g.code ?? '',
    label: g.label ?? '',
    permissions: (g.permissions ?? []).map((p) => ({ key: p.key ?? '', label: p.label ?? p.key ?? '' })),
  }));
}

export async function listUserRoles(userId: string): Promise<Role[]> {
  const { data, error, response } = await api.GET('/api/v1/users/{userId}/roles', {
    params: { path: { userId } },
  });
  if (!data) throw toProblemError(response, error);
  return data.map(toRole);
}

export async function assignRole(userId: string, roleId: string): Promise<void> {
  const { error, response } = await api.PUT('/api/v1/users/{userId}/roles/{roleId}', {
    params: { path: { userId, roleId } },
  });
  if (!response.ok) throw toProblemError(response, error);
}

export async function revokeRole(userId: string, roleId: string): Promise<void> {
  const { error, response } = await api.DELETE('/api/v1/users/{userId}/roles/{roleId}', {
    params: { path: { userId, roleId } },
  });
  if (!response.ok) throw toProblemError(response, error);
}

/** Active members of the directory, for the user picker (the member id is the user id). */
export async function listMemberOptions(): Promise<MemberOption[]> {
  const { data, error, response } = await api.GET('/api/v1/members', {
    params: { query: { page: { size: PAGE_SIZE } } },
  });
  if (!data) throw toProblemError(response, error);
  return (data.content ?? []).map((m) => ({
    id: m.id ?? '',
    name: [m.lastName, m.firstName].filter(Boolean).join(' '),
  }));
}
