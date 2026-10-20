import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import {
  getMemberResolutionCounts,
  listMemberResolutions,
  type MemberResolutionQuery,
} from './memberResolutionsApi';
import { RESOLUTIONS_KEY } from './useResolutions';

/** Resolutions of one person, page by page; any resolution change invalidates it through RESOLUTIONS_KEY. */
export function useMemberResolutions(memberId: string, query: Omit<MemberResolutionQuery, 'page'>, enabled = true) {
  return useInfiniteQuery({
    queryKey: [...RESOLUTIONS_KEY, 'member', memberId, query.role, query.active ?? false, query.state ?? null],
    queryFn: ({ pageParam }) => listMemberResolutions(memberId, { ...query, page: pageParam }),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
    enabled: enabled && memberId !== '',
  });
}

export function useMemberResolutionCounts(memberId: string, enabled = true) {
  return useQuery({
    queryKey: [...RESOLUTIONS_KEY, 'member-counts', memberId],
    queryFn: () => getMemberResolutionCounts(memberId),
    enabled: enabled && memberId !== '',
  });
}
