import { ApiProblemError } from '../api/problem';
import { hasPermission, type CurrentUser } from '../auth/CurrentUser';
import { attachmentErrorKey } from './attachmentLogic';
import {
  canChangeStatus,
  isRealDate,
  MAX_TITLE,
  TASKS_ASSIGN,
  TASKS_CREATE,
  TASKS_EDIT_ANY,
  taskErrorKey,
} from './taskLogic';
import {
  SubtaskDeadlineConflictError,
  type ConflictingSubtask,
  type SpaceStatus,
  type StatusCategory,
  type Task,
  type TaskInput,
  type TaskSummary,
} from './tasksApi';

/*
 * Pure rules behind the inline actions of the task panel (spec UI-5, ST-2..ST-5, AS-3, AR-2, AR-3). They only decide
 * which controls to offer and validate input before a request; the backend stays authoritative for every rule.
 */

// ---- permissions ------------------------------------------------------------------------------------------------

/** Who may assign other people (AS-3, ST-2 "for someone"): `tasks.assign` (Admin holds every key). */
export function canAssignOthers(user: CurrentUser | null): boolean {
  return hasPermission(user, TASKS_ASSIGN);
}

/**
 * Whether `user` may add or remove `targetId` as an assignee (mirrors the backend `AssigneeRules`): `tasks.assign`
 * for anybody; oneself also with `tasks.edit.any`, or as the creator of the task holding `tasks.create`.
 */
export function canManageAssignee(
  user: CurrentUser | null,
  task: Pick<Task, 'creatorId' | 'authorId'>,
  targetId: string,
): boolean {
  if (!user) return false;
  if (canAssignOthers(user)) return true;
  if (user.id !== targetId) return false;
  const creator = (task.creatorId ?? task.authorId) === user.id;
  return hasPermission(user, TASKS_EDIT_ANY) || (creator && hasPermission(user, TASKS_CREATE));
}

/** Adding a subtask (ST-1, ST-2): `tasks.create`, the parent is not archived and is not itself a subtask. */
export function canAddSubtask(user: CurrentUser | null, task: Pick<Task, 'parentId' | 'archived'>): boolean {
  return hasPermission(user, TASKS_CREATE) && !task.archived && !task.parentId;
}

/** Artifacts follow the contribute rule (AR-3, AS-5): creator, active assignee, `tasks.edit.any` or Admin. */
export function canManageArtifacts(
  user: CurrentUser | null,
  task: Pick<Task, 'authorId' | 'assigneeIds' | 'creatorId' | 'archived'>,
): boolean {
  return !task.archived && canChangeStatus(user, task);
}

// ---- statuses ---------------------------------------------------------------------------------------------------

const firstOf = (statuses: readonly SpaceStatus[], category: StatusCategory) =>
  statuses.find((s) => !s.archived && s.category === category);

/** Active DONE-category statuses in board order (targets of the suggest-close banner). */
export function doneTargets(statuses: readonly SpaceStatus[]): SpaceStatus[] {
  return statuses.filter((s) => !s.archived && s.category === 'DONE');
}

/**
 * Status a subtask checkbox moves to: the first active DONE status when ticked; when unticked the first active TODO
 * status, else the first IN_PROGRESS one. `undefined` when the space has no such status.
 */
export function subtaskToggleTarget(statuses: readonly SpaceStatus[], done: boolean): string | undefined {
  if (done) return firstOf(statuses, 'DONE')?.id;
  return (firstOf(statuses, 'TODO') ?? firstOf(statuses, 'IN_PROGRESS'))?.id;
}

/**
 * ST-5: moving a parent with open subtasks into a DONE status is allowed but the UI warns first. True when the
 * target is a DONE status, the task is not already in one and the backend reports open subtasks.
 */
export function needsOpenSubtaskWarning(
  task: Pick<Task, 'openSubtaskCount'>,
  current: StatusCategory | undefined,
  target: StatusCategory | undefined,
): boolean {
  return target === 'DONE' && current !== 'DONE' && task.openSubtaskCount > 0;
}

/**
 * ST-4: the banner is driven only by the backend flag `suggestClose`; it is hidden for archived tasks, for tasks
 * already closed and when the space has no active DONE status to move to. Nothing ever happens without a click.
 */
export function showSuggestClose(
  task: Pick<Task, 'suggestClose' | 'archived'>,
  current: StatusCategory | undefined,
  statuses: readonly SpaceStatus[],
): boolean {
  return task.suggestClose && !task.archived && current !== 'DONE' && current !== 'CANCELED' && doneTargets(statuses).length > 0;
}

// ---- deadlines (ST-3) -------------------------------------------------------------------------------------------

/**
 * Subtasks that would block `deadline` as the new parent deadline: non-archived ones with a later deadline.
 * A cleared deadline imposes no limit.
 */
export function subtasksBlockingDeadline(deadline: string | null, subtasks: readonly TaskSummary[]): ConflictingSubtask[] {
  if (!deadline) return [];
  return subtasks
    .filter((s) => !s.archived && !!s.deadline && s.deadline > deadline)
    .map((s) => ({ id: s.id, title: s.title, deadline: s.deadline ?? '' }));
}

/** The conflicting subtasks named by a rejected deadline change; falls back to the locally known subtasks. */
export function deadlineConflictsOf(error: unknown, deadline: string | null, subtasks: readonly TaskSummary[]): ConflictingSubtask[] {
  if (error instanceof SubtaskDeadlineConflictError && error.conflictingSubtasks.length > 0) return [...error.conflictingSubtasks];
  return subtasksBlockingDeadline(deadline, subtasks);
}

/** i18n key of an invalid subtask deadline, or null: a real date, not later than the parent deadline (ST-3). */
export function subtaskDeadlineError(deadline: string, parentDeadline: string | null): string | null {
  if (deadline === '') return null;
  if (!isRealDate(deadline)) return 'tasks.validation.dueDateInvalid';
  if (parentDeadline && deadline > parentDeadline) return 'tasks.errors.SUBTASK_DEADLINE_AFTER_PARENT';
  return null;
}

/** The full PUT body of a task with only the deadline changed (the edit endpoint replaces every editable field). */
export function inputWithDeadline(task: Task, deadline: string | null): TaskInput {
  return {
    title: task.title,
    description: task.description ?? undefined,
    priority: task.priority,
    deadline: deadline ?? undefined,
    labels: task.labels,
    customFieldValues: task.customFieldValues,
  };
}

// ---- subtasks (ST-2) --------------------------------------------------------------------------------------------

export type SubtaskTarget = 'me' | 'someone';

export interface SubtaskDraft {
  title: string;
  deadline: string;
  target: SubtaskTarget;
  assigneeId: string | null;
}

export type SubtaskDraftErrors = Partial<Record<'title' | 'deadline' | 'assignee', string>>;

/** Validates the inline subtask form; returns i18n keys per invalid field. */
export function validateSubtaskDraft(d: SubtaskDraft, parentDeadline: string | null): SubtaskDraftErrors {
  const errors: SubtaskDraftErrors = {};
  const title = d.title.trim();
  if (title === '') errors.title = 'tasks.validation.titleRequired';
  else if (title.length > MAX_TITLE) errors.title = 'tasks.validation.titleTooLong';
  const deadline = subtaskDeadlineError(d.deadline, parentDeadline);
  if (deadline) errors.deadline = deadline;
  if (d.target === 'someone' && !d.assigneeId) errors.assignee = 'tasks.subtasks.assigneeRequired';
  return errors;
}

/** Request body: "for me" omits `assigneeIds` (the backend assigns the caller), "for someone" names that member. */
export function subtaskRequest(d: SubtaskDraft): { title: string; deadline?: string; assigneeIds?: string[] } {
  return {
    title: d.title.trim(),
    deadline: d.deadline || undefined,
    assigneeIds: d.target === 'someone' && d.assigneeId ? [d.assigneeId] : undefined,
  };
}

// ---- artifacts (AR-2) -------------------------------------------------------------------------------------------

/** Backend limits of link artifacts. */
export const MAX_ARTIFACT_URL = 2048;
export const MAX_ARTIFACT_TITLE = 300;

/**
 * Validates a link before it is sent (mirrors the backend `ArtifactUrlValidator`): after trimming, an absolute
 * http(s) URL with a host, no whitespace inside, at most 2048 characters. Returns an i18n key or null.
 */
export function linkUrlError(raw: string): string | null {
  const url = raw.trim();
  if (url === '') return 'tasks.artifacts.validation.urlRequired';
  if (url.length > MAX_ARTIFACT_URL) return 'tasks.artifacts.validation.urlTooLong';
  // eslint-disable-next-line no-control-regex
  if (/[\s\u0000-\u001f\u007f]/.test(url)) return 'tasks.artifacts.validation.urlInvalid';
  let parsed: URL;
  try {
    parsed = new URL(url);
  } catch {
    return 'tasks.artifacts.validation.urlInvalid';
  }
  if ((parsed.protocol !== 'http:' && parsed.protocol !== 'https:') || parsed.hostname === '') {
    return 'tasks.artifacts.validation.urlInvalid';
  }
  // `new URL` accepts "https:example.org"; the backend needs the authority form "https://host".
  if (!/^https?:\/\/[^/]/i.test(url)) return 'tasks.artifacts.validation.urlInvalid';
  return null;
}

export function linkTitleError(raw: string): string | null {
  return raw.trim().length > MAX_ARTIFACT_TITLE ? 'tasks.artifacts.validation.titleTooLong' : null;
}

/** i18n key of a failed artifact action: the contribute rule reads as an artifact rule, files keep their wording. */
export function artifactErrorKey(error: unknown): string {
  if (error instanceof ApiProblemError) {
    if (error.code === 'TASK_CONTRIBUTE_FORBIDDEN') return 'tasks.artifacts.forbidden';
    if (error.code === 'FILE_TYPE_NOT_ALLOWED') return attachmentErrorKey(error);
  }
  return taskErrorKey(error);
}
