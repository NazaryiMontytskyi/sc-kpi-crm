import type { SpaceStatus, TaskSummary } from './tasksApi';

/** Columns of the board: the space's active statuses in their configured order. */
export function boardColumns(statuses: readonly SpaceStatus[]): SpaceStatus[] {
  return statuses.filter((s) => !s.archived).sort((a, b) => a.position - b.position);
}

/** Groups tasks by status id; every column key is present even when empty. Order within a column is kept. */
export function groupByStatus(tasks: readonly TaskSummary[], columns: readonly SpaceStatus[]): Map<string, TaskSummary[]> {
  const map = new Map<string, TaskSummary[]>(columns.map((c) => [c.id, []]));
  for (const task of tasks) map.get(task.statusId)?.push(task);
  return map;
}

/** Returns a copy of the list with one task moved to another status (used for the optimistic update). */
export function withTaskStatus(tasks: readonly TaskSummary[], taskId: string, statusId: string): TaskSummary[] {
  return tasks.map((t) => (t.id === taskId ? { ...t, statusId } : t));
}

export const DRAG_MIME = 'application/x-task-id';
