import type { Appointment, Membership, OrgNode } from '../org/orgApi';

export interface UnitLabelIndex {
  /** Unit name by id (archived units included). */
  names: Map<string, string>;
}

/** Flat id -> name index of the org forest. */
export function indexUnits(tree: readonly OrgNode[]): UnitLabelIndex {
  const names = new Map<string, string>();
  const walk = (list: readonly OrgNode[]) => {
    for (const n of list) {
      names.set(n.id, n.name);
      walk(n.children);
    }
  };
  walk(tree);
  return { names };
}

export interface PeriodSplit<T> {
  current: T[];
  past: T[];
}

/** Splits into current (still open) and past items, each newest first by start date. */
export function splitByPeriod<T extends { active: boolean }>(
  items: readonly T[],
  start: (item: T) => string,
): PeriodSplit<T> {
  const byStartDesc = (a: T, b: T) => start(b).localeCompare(start(a));
  return {
    current: items.filter((i) => i.active).sort(byStartDesc),
    past: items.filter((i) => !i.active).sort(byStartDesc),
  };
}

export const splitMemberships = (items: readonly Membership[]) => splitByPeriod(items, (m) => m.since);
export const splitAppointments = (items: readonly Appointment[]) => splitByPeriod(items, (a) => a.startDate);
