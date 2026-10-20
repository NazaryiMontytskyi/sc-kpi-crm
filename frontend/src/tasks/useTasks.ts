import { keepPreviousData, useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  addCustomField,
  addStatus,
  archiveCustomField,
  archiveSpace,
  archiveStatus,
  archiveTask,
  changeTaskStatus,
  createSpace,
  createTask,
  getSpace,
  getTask,
  listSpaces,
  listTasks,
  reorderStatuses,
  restoreSpace,
  restoreTask,
  updateCustomField,
  updateSpace,
  updateStatus,
  updateTask,
  type CreateCustomFieldInput,
  type CreateTaskInput,
  type CustomFieldInput,
  type SpaceInput,
  type StatusInput,
  type TaskInput,
  type TaskQuery,
  type TaskSummary,
} from './tasksApi';
import { withTaskStatus } from './boardLogic';

export const TASKS_KEY = ['tasks'] as const;
export const SPACES_KEY = ['spaces'] as const;

export function useSpaces(includeArchived: boolean) {
  return useQuery({ queryKey: [...SPACES_KEY, 'list', { includeArchived }], queryFn: () => listSpaces(includeArchived) });
}

export function useSpace(id: string | undefined) {
  return useQuery({ queryKey: [...SPACES_KEY, 'one', id], queryFn: () => getSpace(id ?? ''), enabled: !!id });
}

/** Details (statuses, fields) of several spaces at once, keyed by space id. */
export function useSpaceDetails(ids: string[]) {
  return useQueries({
    queries: ids.map((id) => ({ queryKey: [...SPACES_KEY, 'one', id], queryFn: () => getSpace(id) })),
    combine: (results) => {
      const map = new Map<string, NonNullable<(typeof results)[number]['data']>>();
      for (const r of results) if (r.data) map.set(r.data.id, r.data);
      return map;
    },
  });
}

export function useTaskList(query: TaskQuery) {
  return useQuery({
    queryKey: [...TASKS_KEY, 'list', query],
    queryFn: () => listTasks(query),
    placeholderData: keepPreviousData,
  });
}

export function useTask(id: string) {
  return useQuery({ queryKey: [...TASKS_KEY, 'one', id], queryFn: () => getTask(id) });
}

/** Wraps a mutation so that task and/or space caches are refreshed on success. */
function useInvalidating<V, R>(fn: (v: V) => Promise<R>, keys: ReadonlyArray<readonly string[]>) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => Promise.all(keys.map((queryKey) => qc.invalidateQueries({ queryKey }))),
  });
}

export const useCreateTask = () => useInvalidating((i: CreateTaskInput) => createTask(i), [TASKS_KEY]);
export const useUpdateTask = () =>
  useInvalidating((v: { id: string; input: TaskInput; related: string[]; attachments: string[] }) =>
      updateTask(v.id, v.input, v.related, v.attachments), [TASKS_KEY]);
export const useChangeTaskStatus = () =>
  useInvalidating((v: { id: string; statusId: string }) => changeTaskStatus(v.id, v.statusId), [TASKS_KEY]);
export const useArchiveTask = () => useInvalidating((id: string) => archiveTask(id), [TASKS_KEY]);
export const useRestoreTask = () => useInvalidating((id: string) => restoreTask(id), [TASKS_KEY]);

export const useCreateSpace = () => useInvalidating((i: SpaceInput) => createSpace(i), [SPACES_KEY]);
export const useUpdateSpace = () =>
  useInvalidating((v: { id: string; input: SpaceInput }) => updateSpace(v.id, v.input), [SPACES_KEY]);
export const useArchiveSpace = () => useInvalidating((id: string) => archiveSpace(id), [SPACES_KEY, TASKS_KEY]);
export const useRestoreSpace = () => useInvalidating((id: string) => restoreSpace(id), [SPACES_KEY]);

export const useAddStatus = () =>
  useInvalidating((v: { spaceId: string; input: StatusInput }) => addStatus(v.spaceId, v.input), [SPACES_KEY]);
export const useUpdateStatus = () =>
  useInvalidating(
    (v: { spaceId: string; statusId: string; input: StatusInput }) => updateStatus(v.spaceId, v.statusId, v.input),
    [SPACES_KEY],
  );
export const useReorderStatuses = () =>
  useInvalidating((v: { spaceId: string; ids: string[] }) => reorderStatuses(v.spaceId, v.ids), [SPACES_KEY]);
export const useArchiveStatus = () =>
  useInvalidating(
    (v: { spaceId: string; statusId: string; replacementId?: string }) => archiveStatus(v.spaceId, v.statusId, v.replacementId),
    [SPACES_KEY, TASKS_KEY],
  );

export const useAddCustomField = () =>
  useInvalidating((v: { spaceId: string; input: CreateCustomFieldInput }) => addCustomField(v.spaceId, v.input), [SPACES_KEY]);
export const useUpdateCustomField = () =>
  useInvalidating(
    (v: { spaceId: string; fieldId: string; input: CustomFieldInput }) => updateCustomField(v.spaceId, v.fieldId, v.input),
    [SPACES_KEY],
  );
export const useArchiveCustomField = () =>
  useInvalidating((v: { spaceId: string; fieldId: string }) => archiveCustomField(v.spaceId, v.fieldId), [SPACES_KEY]);

const BOARD_PAGE_SIZE = 100;
const BOARD_MAX_PAGES = 20;

const boardKey = (spaceId: string | undefined) => [...TASKS_KEY, 'board', spaceId] as const;

/** All active tasks of one space (the board shows every card, so pages are fetched until the end). */
export function useBoardTasks(spaceId: string | undefined) {
  return useQuery({
    queryKey: boardKey(spaceId),
    enabled: !!spaceId,
    queryFn: async () => {
      const all: TaskSummary[] = [];
      for (let page = 0; page < BOARD_MAX_PAGES; page++) {
        const res = await listTasks({
          spaceId,
          labels: [],
          includeArchived: false,
          sortBy: 'createdAt',
          direction: 'desc',
          page,
          size: BOARD_PAGE_SIZE,
        });
        all.push(...res.items);
        if (page + 1 >= res.totalPages) break;
      }
      return all;
    },
  });
}

/** Moves a card: the board is updated at once and rolled back when the status-change API fails. */
export function useMoveTask(spaceId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (v: { id: string; statusId: string }) => changeTaskStatus(v.id, v.statusId),
    onMutate: async (v) => {
      await qc.cancelQueries({ queryKey: boardKey(spaceId) });
      const previous = qc.getQueryData<TaskSummary[]>(boardKey(spaceId));
      if (previous) qc.setQueryData(boardKey(spaceId), withTaskStatus(previous, v.id, v.statusId));
      return { previous };
    },
    onError: (_e, _v, ctx) => {
      if (ctx?.previous) qc.setQueryData(boardKey(spaceId), ctx.previous);
    },
    onSettled: () => qc.invalidateQueries({ queryKey: TASKS_KEY }),
  });
}
