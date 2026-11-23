import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { getMemberTaskCounts, listMemberTasks } from './memberTasksApi';
import { TASKS_KEY } from './useTasks';

/** Open tasks of one member, page by page; lives under TASKS_KEY, so any task change refreshes it. */
export function useMemberTasks(memberId: string, enabled = true) {
  return useInfiniteQuery({
    queryKey: [...TASKS_KEY, 'member', memberId],
    queryFn: ({ pageParam }) => listMemberTasks(memberId, pageParam),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
    enabled: enabled && memberId !== '',
  });
}

export function useMemberTaskCounts(memberId: string, enabled = true) {
  return useQuery({
    queryKey: [...TASKS_KEY, 'member-counts', memberId],
    queryFn: () => getMemberTaskCounts(memberId),
    enabled: enabled && memberId !== '',
  });
}
