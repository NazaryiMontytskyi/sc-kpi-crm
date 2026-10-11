import type { Role } from './roleApi';

/** Roles that can still be granted to a user: active, not the built-in Admin, not already held. */
export function assignableRoles(all: readonly Role[], held: readonly Role[]): Role[] {
  const heldIds = new Set(held.map((r) => r.id));
  return all.filter((r) => !r.system && !r.archived && !heldIds.has(r.id));
}
