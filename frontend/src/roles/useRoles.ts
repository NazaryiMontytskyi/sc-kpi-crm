import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import {
  archiveRole,
  assignRole,
  createRole,
  listMemberOptions,
  listPermissionGroups,
  listRoles,
  listUserRoles,
  revokeRole,
  updateRole,
  type RoleRequest,
} from './roleApi';

export const ROLES_KEY = ['roles'] as const;
const USER_ROLES_KEY = ['user-roles'] as const;

export function useRoles(includeArchived = false) {
  return useQuery({ queryKey: [...ROLES_KEY, { includeArchived }], queryFn: () => listRoles(includeArchived) });
}

/** Catalog labels come localized from the backend, so the language is part of the key. */
export function usePermissionGroups() {
  const { i18n } = useTranslation();
  return useQuery({
    queryKey: ['permission-groups', i18n.language],
    queryFn: listPermissionGroups,
    staleTime: Infinity,
  });
}

export function useRoleMutations() {
  const queryClient = useQueryClient();
  const onSuccess = () => queryClient.invalidateQueries({ queryKey: ROLES_KEY });
  return {
    create: useMutation({ mutationFn: (body: RoleRequest) => createRole(body), onSuccess }),
    update: useMutation({
      mutationFn: ({ id, body }: { id: string; body: RoleRequest }) => updateRole(id, body),
      onSuccess,
    }),
    archive: useMutation({
      mutationFn: (id: string) => archiveRole(id),
      onSuccess: () => {
        void queryClient.invalidateQueries({ queryKey: USER_ROLES_KEY });
        return onSuccess();
      },
    }),
  };
}

export function useMemberOptions() {
  return useQuery({ queryKey: ['member-options'], queryFn: listMemberOptions });
}

export function useUserRoles(userId: string | null) {
  return useQuery({
    queryKey: [...USER_ROLES_KEY, userId],
    queryFn: () => listUserRoles(userId ?? ''),
    enabled: userId !== null,
  });
}

export function useAssignmentMutations(userId: string | null) {
  const queryClient = useQueryClient();
  const onSuccess = () => {
    void queryClient.invalidateQueries({ queryKey: ROLES_KEY });
    return queryClient.invalidateQueries({ queryKey: [...USER_ROLES_KEY, userId] });
  };
  return {
    assign: useMutation({ mutationFn: (roleId: string) => assignRole(userId ?? '', roleId), onSuccess }),
    revoke: useMutation({ mutationFn: (roleId: string) => revokeRole(userId ?? '', roleId), onSuccess }),
  };
}
