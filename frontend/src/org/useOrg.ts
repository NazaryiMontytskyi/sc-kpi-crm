import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  appoint,
  archiveOrgUnit,
  archivePosition,
  createOrgUnit,
  createPosition,
  endAppointment,
  endMembership,
  getOrgTree,
  getOrgUnit,
  getPosition,
  listPositionHistory,
  listPositions,
  listUnitMemberships,
  moveMembership,
  moveOrgUnit,
  restoreOrgUnit,
  restorePosition,
  startMembership,
  updateOrgUnit,
  updatePosition,
  type CreatePositionInput,
  type CreateUnitInput,
  type UnitInput,
  type UpdatePositionInput,
} from './orgApi';

export const ORG_KEY = ['org'] as const;

export function useOrgTree(includeArchived: boolean) {
  return useQuery({ queryKey: [...ORG_KEY, 'tree', { includeArchived }], queryFn: () => getOrgTree(includeArchived) });
}

export function useOrgUnit(id: string) {
  return useQuery({ queryKey: [...ORG_KEY, 'unit', id], queryFn: () => getOrgUnit(id) });
}

export function useUnitMemberships(unitId: string, includeEnded: boolean) {
  return useQuery({
    queryKey: [...ORG_KEY, 'memberships', unitId, { includeEnded }],
    queryFn: () => listUnitMemberships(unitId, includeEnded),
  });
}

export function useUnitPositions(unitId: string, includeArchived: boolean) {
  return useQuery({
    queryKey: [...ORG_KEY, 'positions', unitId, { includeArchived }],
    queryFn: () => listPositions(unitId, includeArchived),
  });
}

export function usePosition(id: string) {
  return useQuery({ queryKey: [...ORG_KEY, 'position', id], queryFn: () => getPosition(id) });
}

export function usePositionHistory(id: string) {
  return useQuery({ queryKey: [...ORG_KEY, 'history', id], queryFn: () => listPositionHistory(id) });
}

/**
 * All org mutations. Everything under `org` is refetched (tree, units, memberships, positions, holders); the audit
 * history is refreshed too, and roles because appointments grant and revoke the bound roles.
 */
export function useOrgMutations() {
  const queryClient = useQueryClient();
  const onSuccess = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ORG_KEY }),
      queryClient.invalidateQueries({ queryKey: ['audit'] }),
      queryClient.invalidateQueries({ queryKey: ['roles'] }),
      queryClient.invalidateQueries({ queryKey: ['user-roles'] }),
    ]);
  return {
    createUnit: useMutation({ mutationFn: (input: CreateUnitInput) => createOrgUnit(input), onSuccess }),
    updateUnit: useMutation({
      mutationFn: ({ id, input }: { id: string; input: UnitInput }) => updateOrgUnit(id, input),
      onSuccess,
    }),
    moveUnit: useMutation({
      mutationFn: ({ id, parentId }: { id: string; parentId: string | null }) => moveOrgUnit(id, parentId),
      onSuccess,
    }),
    archiveUnit: useMutation({ mutationFn: (id: string) => archiveOrgUnit(id), onSuccess }),
    restoreUnit: useMutation({ mutationFn: (id: string) => restoreOrgUnit(id), onSuccess }),
    startMembership: useMutation({
      mutationFn: ({ unitId, userId, since }: { unitId: string; userId: string; since?: string }) =>
        startMembership(unitId, userId, since),
      onSuccess,
    }),
    endMembership: useMutation({
      mutationFn: ({ id, until }: { id: string; until?: string }) => endMembership(id, until),
      onSuccess,
    }),
    moveMembership: useMutation({
      mutationFn: ({ id, targetUnitId, date }: { id: string; targetUnitId: string; date?: string }) =>
        moveMembership(id, targetUnitId, date),
      onSuccess,
    }),
    createPosition: useMutation({
      mutationFn: ({ unitId, input }: { unitId: string; input: CreatePositionInput }) =>
        createPosition(unitId, input),
      onSuccess,
    }),
    updatePosition: useMutation({
      mutationFn: ({ id, input }: { id: string; input: UpdatePositionInput }) => updatePosition(id, input),
      onSuccess,
    }),
    archivePosition: useMutation({ mutationFn: (id: string) => archivePosition(id), onSuccess }),
    restorePosition: useMutation({ mutationFn: (id: string) => restorePosition(id), onSuccess }),
    appoint: useMutation({
      mutationFn: ({ positionId, userId, startDate }: { positionId: string; userId: string; startDate?: string }) =>
        appoint(positionId, userId, startDate),
      onSuccess,
    }),
    endAppointment: useMutation({
      mutationFn: ({ id, endDate }: { id: string; endDate?: string }) => endAppointment(id, endDate),
      onSuccess,
    }),
  };
}
