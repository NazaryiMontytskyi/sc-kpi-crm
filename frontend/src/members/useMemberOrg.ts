import { useQueries, useQuery } from '@tanstack/react-query';
import { getPosition, listPositions, type OrgNode, type Position } from '../org/orgApi';
import { useOrgTree } from '../org/useOrg';
import { listAppointmentsOfUser, listMembershipsOfUser } from './memberOrgApi';
import { MEMBERS_KEY } from './useMembers';

/** Positions and unit memberships of one person for the profile page. */
export function useMemberOrg(userId: string) {
  const memberships = useQuery({
    queryKey: [...MEMBERS_KEY, 'org-memberships', userId],
    queryFn: () => listMembershipsOfUser(userId),
  });
  const appointments = useQuery({
    queryKey: [...MEMBERS_KEY, 'org-appointments', userId],
    queryFn: () => listAppointmentsOfUser(userId),
  });
  // Archived units stay visible so past memberships keep their names.
  const tree = useOrgTree(true);
  const positionIds = [...new Set((appointments.data ?? []).map((a) => a.positionId))];
  const positions = useQueries({
    queries: positionIds.map((id) => ({
      queryKey: ['org', 'position', id],
      queryFn: () => getPosition(id),
    })),
  });
  const positionsById = new Map<string, Position>();
  positions.forEach((q) => {
    if (q.data) positionsById.set(q.data.id, q.data);
  });
  return {
    memberships,
    appointments,
    tree,
    positionsById,
    isLoading: memberships.isLoading || appointments.isLoading || tree.isLoading,
    isError: memberships.isError || appointments.isError || tree.isError,
  };
}

export interface PositionOption {
  value: string;
  title: string;
  unitName: string;
}

function activeUnitNodes(tree: readonly OrgNode[]): OrgNode[] {
  const out: OrgNode[] = [];
  const walk = (list: readonly OrgNode[]) => {
    for (const n of list) {
      if (n.archived) continue;
      out.push(n);
      walk(n.children);
    }
  };
  walk(tree);
  return out;
}

/**
 * Active positions of all active units, for the directory's position filter. There is no cross-unit position
 * list endpoint, so positions are loaded per unit (the structure of a council is small).
 */
export function usePositionOptions(enabled: boolean) {
  const tree = useOrgTree(false);
  const units = enabled ? activeUnitNodes(tree.data ?? []) : [];
  const results = useQueries({
    queries: units.map((u) => ({
      queryKey: ['org', 'positions', u.id, { includeArchived: false }],
      queryFn: () => listPositions(u.id, false),
    })),
  });
  const options: PositionOption[] = [];
  results.forEach((r, i) => {
    for (const p of r.data ?? []) {
      if (!p.archived) options.push({ value: p.id, title: p.title, unitName: units[i].name });
    }
  });
  options.sort((a, b) => a.unitName.localeCompare(b.unitName) || a.title.localeCompare(b.title));
  return { options, tree, isLoading: tree.isLoading || results.some((r) => r.isLoading) };
}
