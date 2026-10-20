import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  cancelResolution,
  getResolution,
  issueResolution,
  listTaskResolutions,
  redirectResolution,
  reworkResolution,
  simpleResolutionAction,
  submitResolution,
  type IssueInput,
  type RedirectInput,
  type SimpleAction,
} from './resolutionsApi';
import { TASKS_KEY } from './useTasks';

export const RESOLUTIONS_KEY = ['resolutions'] as const;

/** Resolutions of a task, newest first, loaded page by page. */
export function useTaskResolutions(taskId: string) {
  return useInfiniteQuery({
    queryKey: [...RESOLUTIONS_KEY, 'task', taskId],
    queryFn: ({ pageParam }) => listTaskResolutions(taskId, pageParam),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
  });
}

/** One resolution with completion note and history; loaded only while its card is open. */
export function useResolution(id: string, enabled: boolean) {
  return useQuery({ queryKey: [...RESOLUTIONS_KEY, 'one', id], queryFn: () => getResolution(id), enabled });
}

export function useResolutionMutations(taskId: string) {
  const qc = useQueryClient();
  // A state change can also flip the task's `suggestDone` flag, so task queries are refreshed too.
  const onSuccess = () =>
    Promise.all([qc.invalidateQueries({ queryKey: RESOLUTIONS_KEY }), qc.invalidateQueries({ queryKey: TASKS_KEY })]);
  return {
    issue: useMutation({ mutationFn: (input: IssueInput) => issueResolution(taskId, input), onSuccess }),
    simple: useMutation({ mutationFn: (v: { id: string; action: SimpleAction }) => simpleResolutionAction(v.id, v.action), onSuccess }),
    submit: useMutation({ mutationFn: (v: { id: string; note: string }) => submitResolution(v.id, v.note), onSuccess }),
    rework: useMutation({ mutationFn: (v: { id: string; comment: string }) => reworkResolution(v.id, v.comment), onSuccess }),
    cancel: useMutation({ mutationFn: (v: { id: string; comment?: string }) => cancelResolution(v.id, v.comment), onSuccess }),
    redirect: useMutation({ mutationFn: (v: { id: string; input: RedirectInput }) => redirectResolution(v.id, v.input), onSuccess }),
  };
}
