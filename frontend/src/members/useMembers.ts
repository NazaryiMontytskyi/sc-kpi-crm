import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  archiveMember,
  createMember,
  getMember,
  listMembers,
  resetMemberPassword,
  searchMembers,
  restoreMember,
  updateMember,
  updateSelf,
  type CreateMemberRequest,
  type MemberRequest,
  type SelfEditRequest,
} from './memberApi';
import type { MemberFilters } from './memberFilters';

export const MEMBERS_KEY = ['members'] as const;

export function useMembers(includeArchived: boolean) {
  return useQuery({ queryKey: [...MEMBERS_KEY, 'list', { includeArchived }], queryFn: () => listMembers(includeArchived) });
}

/** One page of the filtered directory; the previous page stays visible while the next one loads. */
export function useMemberDirectory(filters: MemberFilters) {
  return useQuery({
    queryKey: [...MEMBERS_KEY, 'directory', filters],
    queryFn: () => searchMembers(filters),
    placeholderData: keepPreviousData,
  });
}

export function useMember(id: string) {
  return useQuery({ queryKey: [...MEMBERS_KEY, 'one', id], queryFn: () => getMember(id) });
}

export function useMemberMutations() {
  const queryClient = useQueryClient();
  // Members feed the directory and profile pages; the profile also shows its audit history.
  const onSuccess = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: MEMBERS_KEY }),
      queryClient.invalidateQueries({ queryKey: ['audit'] }),
    ]);
  return {
    // gcTime 0: the one-time temporary password in the result is dropped from the mutation cache as soon as
    // the dialog has taken it over (callers also call reset()).
    create: useMutation({ mutationFn: (body: CreateMemberRequest) => createMember(body), onSuccess, gcTime: 0 }),
    resetPassword: useMutation({ mutationFn: (id: string) => resetMemberPassword(id), gcTime: 0 }),
    update: useMutation({
      mutationFn: ({ id, body }: { id: string; body: MemberRequest }) => updateMember(id, body),
      onSuccess,
    }),
    updateSelf: useMutation({ mutationFn: (body: SelfEditRequest) => updateSelf(body), onSuccess }),
    archive: useMutation({ mutationFn: (id: string) => archiveMember(id), onSuccess }),
    restore: useMutation({ mutationFn: (id: string) => restoreMember(id), onSuccess }),
  };
}
