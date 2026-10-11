import type { OrgNode, OrgUnitType } from './orgApi';

export function findNode(nodes: readonly OrgNode[], id: string): OrgNode | null {
  for (const n of nodes) {
    if (n.id === id) return n;
    const inner = findNode(n.children, id);
    if (inner) return inner;
  }
  return null;
}

/** Ids of the unit and everything below it. */
export function subtreeIds(node: OrgNode): Set<string> {
  const ids = new Set<string>([node.id]);
  for (const c of node.children) for (const id of subtreeIds(c)) ids.add(id);
  return ids;
}

/** Working groups live outside the hierarchy: they are listed separately wherever they hang in the tree. */
export function workingGroups(nodes: readonly OrgNode[]): OrgNode[] {
  const result: OrgNode[] = [];
  const walk = (list: readonly OrgNode[]) => {
    for (const n of list) {
      if (n.type === 'WORKING_GROUP') result.push({ ...n, children: [] });
      // Descend into every unit, working groups included: a group may be nested under another group.
      walk(n.children);
    }
  };
  walk(nodes);
  return result;
}

/** The leadership/department/division hierarchy without working groups. */
export function hierarchy(nodes: readonly OrgNode[]): OrgNode[] {
  return nodes
    .filter((n) => n.type !== 'WORKING_GROUP')
    .map((n) => ({ ...n, children: hierarchy(n.children) }));
}

/** Parent types the backend accepts for a unit type; a working group may sit under any unit. */
export function parentTypes(type: OrgUnitType): OrgUnitType[] | 'any' | 'none' {
  switch (type) {
    case 'LEADERSHIP':
      return 'none';
    case 'DEPARTMENT':
      return ['LEADERSHIP'];
    case 'DIVISION':
      return ['DEPARTMENT'];
    default:
      return 'any';
  }
}

/** Only a division cannot exist at the top level. */
export function parentRequired(type: OrgUnitType): boolean {
  return type === 'DIVISION';
}

export interface UnitOption {
  id: string;
  name: string;
  type: OrgUnitType;
}

/** Active units that may be the parent of a unit of `type`, excluding `excludeIds` (the unit and its subtree). */
export function parentOptions(
  tree: readonly OrgNode[],
  type: OrgUnitType,
  excludeIds: ReadonlySet<string> = new Set(),
): UnitOption[] {
  const rule = parentTypes(type);
  if (rule === 'none') return [];
  const result: UnitOption[] = [];
  const walk = (list: readonly OrgNode[]) => {
    for (const n of list) {
      if (n.archived || excludeIds.has(n.id)) continue;
      if (rule === 'any' || rule.includes(n.type)) result.push({ id: n.id, name: n.name, type: n.type });
      walk(n.children);
    }
  };
  walk(tree);
  return result;
}

/** All active units (for the move-membership target picker), excluding `excludeIds`. */
export function activeUnits(tree: readonly OrgNode[], excludeIds: ReadonlySet<string> = new Set()): UnitOption[] {
  const result: UnitOption[] = [];
  const walk = (list: readonly OrgNode[]) => {
    for (const n of list) {
      if (n.archived) continue;
      if (!excludeIds.has(n.id)) result.push({ id: n.id, name: n.name, type: n.type });
      walk(n.children);
    }
  };
  walk(tree);
  return result;
}

/** Chain of nodes from the root down to `id` (inclusive); empty when not found. */
export function pathTo(tree: readonly OrgNode[], id: string): OrgNode[] {
  for (const n of tree) {
    if (n.id === id) return [n];
    const inner = pathTo(n.children, id);
    if (inner.length) return [n, ...inner];
  }
  return [];
}
