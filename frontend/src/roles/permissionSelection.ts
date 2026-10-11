import type { PermissionGroup } from './roleApi';

/** Adds or removes one permission key; the result is a new sorted list without duplicates. */
export function togglePermission(selected: readonly string[], key: string, on: boolean): string[] {
  const set = new Set(selected);
  if (on) set.add(key);
  else set.delete(key);
  return [...set].sort();
}

/** Selects or clears every permission of a group, leaving other groups untouched. */
export function toggleGroup(selected: readonly string[], group: PermissionGroup, on: boolean): string[] {
  const set = new Set(selected);
  for (const p of group.permissions) {
    if (on) set.add(p.key);
    else set.delete(p.key);
  }
  return [...set].sort();
}

export type GroupState = 'none' | 'some' | 'all';

export function groupState(selected: readonly string[], group: PermissionGroup): GroupState {
  const count = group.permissions.filter((p) => selected.includes(p.key)).length;
  if (count === 0) return 'none';
  return count === group.permissions.length ? 'all' : 'some';
}
