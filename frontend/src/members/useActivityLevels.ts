import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { listActivityLevelHistory, listActivityLevels, setActivityLevel } from './activityLevelApi';
import { MEMBERS_KEY } from './useMembers';

export const ACTIVITY_LEVELS_KEY = ['activity-levels'] as const;

/** The (editable) activity scale for pickers and labels. */
export function useActivityLevels() {
  return useQuery({ queryKey: ACTIVITY_LEVELS_KEY, queryFn: listActivityLevels });
}

export function useActivityLevelHistory(memberId: string) {
  return useQuery({
    queryKey: [...MEMBERS_KEY, 'activity-history', memberId],
    queryFn: () => listActivityLevelHistory(memberId),
  });
}

export function useSetActivityLevel(memberId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ levelId, comment }: { levelId: string; comment: string }) =>
      setActivityLevel(memberId, levelId, comment),
    // The profile (current level), the directory filter results and the history all change.
    onSuccess: () => queryClient.invalidateQueries({ queryKey: MEMBERS_KEY }),
  });
}
