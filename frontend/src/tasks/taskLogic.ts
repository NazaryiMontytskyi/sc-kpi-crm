import { ApiProblemError, problemMessage } from '../api/problem';
import { hasPermission, type CurrentUser } from '../auth/CurrentUser';
import type { CustomField, CustomFieldValues, FilterCriteria, Space, StatusCategory, TaskQuery, TaskSummary } from './tasksApi';

export const TASKS_VIEW = 'tasks.view';
export const TASKS_CREATE = 'tasks.create';
export const TASKS_ARCHIVE = 'tasks.archive';
export const SPACES_MANAGE = 'spaces.manage';
export const TASKS_EDIT_ANY = 'tasks.edit.any';
export const TASKS_ASSIGN = 'tasks.assign';

export const MAX_TITLE = 300;
export const MAX_FIELD_TEXT = 2000;
export const MAX_LABELS = 20;
export const MAX_LABEL_LENGTH = 50;

export const TASK_VERSION_CONFLICT = 'TASK_VERSION_CONFLICT';

/**
 * Task codes with their own message (spec §6.5 reload hint, AS-5 contributor rule, AS-1/AS-3 assignee rules,
 * ST-1/ST-3 subtask rules, AR-2 artifact rules).
 */
const TASK_PROBLEM_CODES: readonly string[] = [
  TASK_VERSION_CONFLICT,
  'TASK_CONTRIBUTE_FORBIDDEN',
  'TASK_DEADLINE_BEFORE_SUBTASKS',
  'SUBTASK_DEADLINE_AFTER_PARENT',
  'SUBTASK_DEPTH_EXCEEDED',
  'TASK_ASSIGN_FORBIDDEN',
  'TASK_ASSIGNEE_INACTIVE',
  'TASK_ASSIGNEE_DUPLICATE',
  'TASK_ARTIFACT_INVALID',
  'TASK_ARTIFACT_FILE_INVALID',
  'TASK_ARTIFACT_DUPLICATE',
  'TASK_ARTIFACT_URL_INVALID',
  'TASK_ARTIFACT_URL_TOO_LONG',
];

/** i18n key for a failed task action; other 403s read as "no permission" (the backend stays authoritative). */
export function taskErrorKey(error: unknown): string {
  if (error instanceof ApiProblemError && error.code && TASK_PROBLEM_CODES.includes(error.code)) return `tasks.errors.${error.code}`;
  if (error instanceof ApiProblemError && error.status === 403) return 'problems.FORBIDDEN';
  return problemMessage(error).key;
}

/** A stale edit (optimistic locking, spec §6.5): the view must be reloaded before trying again. */
export function isVersionConflict(error: unknown): boolean {
  return error instanceof ApiProblemError && error.status === 409 && (error.code === TASK_VERSION_CONFLICT || error.code === undefined);
}

/**
 * Whether to offer the status selector: creator, author, an active assignee, `tasks.edit.any` or Admin (AS-5).
 * Convenience only; the backend `TaskEditPolicy` decides and answers 403 `TASK_CONTRIBUTE_FORBIDDEN` otherwise.
 */
export function canChangeStatus(
  user: CurrentUser | null,
  task: Pick<TaskSummary, 'authorId' | 'assigneeIds'> & { creatorId?: string | null },
): boolean {
  if (!user) return false;
  if (user.isAdmin || hasPermission(user, TASKS_EDIT_ANY)) return true;
  return user.id === task.creatorId || user.id === task.authorId || task.assigneeIds.includes(user.id);
}

/**
 * Whether to offer the edit action: the author or an Admin (CONTEXT.md decision Q7). Convenience only;
 * the backend `TaskEditPolicy` decides and a 403 is shown gracefully.
 */
export function canEditTask(user: CurrentUser | null, task: Pick<TaskSummary, 'authorId'>): boolean {
  return !!user && (user.isAdmin || user.id === task.authorId);
}

export const DEFAULT_QUERY: TaskQuery = {
  labels: [],
  includeArchived: false,
  sortBy: 'createdAt',
  direction: 'desc',
  page: 0,
  size: 20,
};

/** Number of active filters (shown on the drawer button); sort and paging are not filters. */
export function activeFilterCount(q: TaskQuery): number {
  return [q.spaceId, q.statusId, q.deadlineFrom, q.deadlineTo, q.executorId, q.authorId, q.resolutionState].filter(Boolean).length + (q.labels.length > 0 ? 1 : 0) + (q.includeArchived ? 1 : 0);
}

/** The saved-filter part of a query (sorting, paging and the archived switch are not stored). */
export function criteriaFromQuery(q: TaskQuery): FilterCriteria {
  return {
    spaceId: q.spaceId,
    statusId: q.statusId,
    labels: [...q.labels],
    executorId: q.executorId,
    authorId: q.authorId,
    resolutionState: q.resolutionState,
    view: q.view,
    deadlineFrom: q.deadlineFrom,
    deadlineTo: q.deadlineTo,
  };
}

/** Applies stored criteria to a query: every filter is replaced, sorting and page size are kept, paging restarts. */
export function queryFromCriteria(c: FilterCriteria, base: TaskQuery): TaskQuery {
  return {
    ...base,
    spaceId: c.spaceId,
    statusId: c.statusId,
    labels: [...c.labels],
    executorId: c.executorId,
    authorId: c.authorId,
    resolutionState: c.resolutionState,
    view: c.view,
    deadlineFrom: c.deadlineFrom,
    deadlineTo: c.deadlineTo,
    includeArchived: false,
    page: 0,
  };
}

const norm = (c: FilterCriteria) =>
  JSON.stringify([
    c.spaceId ?? null,
    c.statusId ?? null,
    [...c.labels].sort(),
    c.executorId ?? null,
    c.authorId ?? null,
    c.resolutionState ?? null,
    c.view ?? null,
    c.deadlineFrom ?? null,
    c.deadlineTo ?? null,
  ]);

export function sameCriteria(a: FilterCriteria, b: FilterCriteria): boolean {
  return norm(a) === norm(b);
}

/** Whether the criteria carry any filter at all (an empty one is not worth saving). */
export function hasCriteria(c: FilterCriteria): boolean {
  return !sameCriteria(c, { labels: [] });
}

/** Today in Europe/Kyiv as `YYYY-MM-DD` (due dates are calendar dates, never shifted). */
export function todayKyiv(now: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Kyiv', year: 'numeric', month: '2-digit', day: '2-digit' }).format(now);
}

export function isOverdue(task: Pick<TaskSummary, 'deadline' | 'archived'>, today: string): boolean {
  return !!task.deadline && !task.archived && task.deadline < today;
}

/** Overdue unless the task is already closed (status category DONE or CANCELED). */
export function isOpenAndOverdue(
  task: Pick<TaskSummary, 'deadline' | 'archived'>,
  today: string,
  category: StatusCategory | undefined,
): boolean {
  return category !== 'DONE' && category !== 'CANCELED' && isOverdue(task, today);
}

// ---- "My tasks" overview (spec UI-4) -----------------------------------------------------------

/** Deadline groups of the overview, in display order. */
export const DEADLINE_GROUPS = ['OVERDUE', 'THIS_WEEK', 'LATER', 'NO_DEADLINE'] as const;
export type DeadlineGroup = (typeof DEADLINE_GROUPS)[number];

/** `YYYY-MM-DD` shifted by whole calendar days (pure date arithmetic, no time zone involved). */
export function addDays(isoDate: string, days: number): string {
  const [y, m, d] = isoDate.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d + days)).toISOString().slice(0, 10);
}

/**
 * Last day (Sunday) of the calendar week containing `today`. Weeks run Monday to Sunday as in Ukraine
 * [ASSUMPTION: "This week" means the rest of the current calendar week, today included].
 */
export function endOfWeek(today: string): string {
  const [y, m, d] = today.split('-').map(Number);
  const weekday = new Date(Date.UTC(y, m - 1, d)).getUTCDay(); // 0 = Sunday
  return addDays(today, (7 - weekday) % 7);
}

/**
 * Group of one deadline relative to `today` (a Europe/Kyiv calendar date, see `todayKyiv`): a deadline is due at the
 * end of its day in Kyiv, so it is overdue only from the next Kyiv day on.
 */
export function deadlineGroup(deadline: string | null, today: string): DeadlineGroup {
  if (!deadline) return 'NO_DEADLINE';
  if (deadline < today) return 'OVERDUE';
  return deadline <= endOfWeek(today) ? 'THIS_WEEK' : 'LATER';
}

export interface TaskOverviewGroups<T> {
  groups: Record<DeadlineGroup, T[]>;
  /** Closed tasks (status category DONE/CANCELED); never overdue and kept apart from the deadline groups. */
  closed: T[];
  /** Open tasks in all deadline groups. */
  openCount: number;
}

type GroupableTask = Pick<TaskSummary, 'id' | 'deadline' | 'title' | 'createdAt' | 'archived'>;

const byDeadline = (a: GroupableTask, b: GroupableTask) =>
  (a.deadline ?? '').localeCompare(b.deadline ?? '') || a.title.localeCompare(b.title) || a.id.localeCompare(b.id);
const newestFirst = (a: GroupableTask, b: GroupableTask) => b.createdAt.localeCompare(a.createdAt) || a.id.localeCompare(b.id);

/**
 * Splits tasks into the overview groups (spec UI-4) using Europe/Kyiv day boundaries of `now`. Archived tasks are
 * left out; closed ones go to `closed`. Dated groups are sorted by deadline (soonest first), the others newest first.
 */
export function groupTasksByDeadline<T extends GroupableTask>(
  tasks: readonly T[],
  now: Date,
  isClosed: (task: T) => boolean = () => false,
): TaskOverviewGroups<T> {
  const today = todayKyiv(now);
  const groups: Record<DeadlineGroup, T[]> = { OVERDUE: [], THIS_WEEK: [], LATER: [], NO_DEADLINE: [] };
  const closed: T[] = [];
  for (const task of tasks) {
    if (task.archived) continue;
    if (isClosed(task)) closed.push(task);
    else groups[deadlineGroup(task.deadline, today)].push(task);
  }
  groups.OVERDUE.sort(byDeadline);
  groups.THIS_WEEK.sort(byDeadline);
  groups.LATER.sort(byDeadline);
  groups.NO_DEADLINE.sort(newestFirst);
  closed.sort(newestFirst);
  const openCount = DEADLINE_GROUPS.reduce((n, g) => n + groups[g].length, 0);
  return { groups, closed, openCount };
}

/** Sections of `/tasks` (search param `section`); "My tasks" is the default and has no param. */
export const TRACKER_SECTIONS = ['my', 'issued', 'all'] as const;
export type TrackerSection = (typeof TRACKER_SECTIONS)[number];

export function trackerSection(value: string | null): TrackerSection {
  return (TRACKER_SECTIONS as readonly string[]).includes(value ?? '') ? (value as TrackerSection) : 'my';
}

// ---- master/detail panel ---------------------------------------------------------------------

/** >= 1024 px: side panel next to the list. */
export const PANEL_DESKTOP_QUERY = '(min-width: 64em)';
/** >= 768 px: overlay; below it a full-screen sheet. */
export const PANEL_TABLET_QUERY = '(min-width: 48em)';

export type PanelVariant = 'side' | 'overlay' | 'sheet';

/** Highlight of the list/board card whose task is open in the panel (anchor token: brand shade 6 light, shade 2 dark). */
export const SELECTED_CARD_STYLE = {
  borderColor: 'var(--mantine-color-anchor)',
  boxShadow: 'inset 4px 0 0 var(--mantine-color-anchor)',
} as const;

/** How the task panel is presented for the current viewport (spec UI-1, UI-3). */
export function panelVariant(desktop: boolean, tablet: boolean): PanelVariant {
  if (desktop) return 'side';
  return tablet ? 'overlay' : 'sheet';
}

/**
 * Search params of the tracker itself (section, view mode and space) kept while tasks open and close; `tab` etc. are
 * dropped.
 */
export const TRACKER_PARAMS = ['section', 'layout', 'space'] as const;

export function trackerSearch(search: string): string {
  const from = new URLSearchParams(search);
  const kept = new URLSearchParams();
  for (const key of TRACKER_PARAMS) {
    const value = from.get(key);
    if (value) kept.set(key, value);
  }
  const out = kept.toString();
  return out ? `?${out}` : '';
}

/** Only http(s) links are rendered as clickable (defence in depth; the backend validates too, AR-2). */
export function isSafeHttpUrl(url: string | null | undefined): boolean {
  if (!url) return false;
  try {
    const parsed = new URL(url);
    return parsed.protocol === 'http:' || parsed.protocol === 'https:';
  } catch {
    return false;
  }
}

export function isRealDate(value: string): boolean {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!m) return false;
  const d = new Date(Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3])));
  return d.getUTCFullYear() === Number(m[1]) && d.getUTCMonth() === Number(m[2]) - 1 && d.getUTCDate() === Number(m[3]);
}

// ---- custom fields ---------------------------------------------------------------------------

/** Editable form values of custom fields are strings; they are converted to typed values on submit. */
export type FieldDraft = Record<string, string>;

export function activeFields(space: Space | undefined): CustomField[] {
  return (space?.customFields ?? []).filter((f) => !f.archived);
}

/** Typed stored value -> form string. */
export function draftFromValues(fields: CustomField[], values: CustomFieldValues): FieldDraft {
  const draft: FieldDraft = {};
  for (const f of fields) {
    const v = values[f.id];
    draft[f.id] = v === undefined || v === null ? '' : String(v);
  }
  return draft;
}

export type FieldErrorCode = 'required' | 'number' | 'date' | 'option' | 'tooLong';

/** Validates one custom field draft; `null` when valid. Mirrors the backend rules (type, required, length). */
export function validateFieldValue(field: CustomField, raw: string): FieldErrorCode | null {
  const value = raw.trim();
  if (value === '') return field.required ? 'required' : null;
  switch (field.type) {
    case 'TEXT':
      return value.length > MAX_FIELD_TEXT ? 'tooLong' : null;
    case 'NUMBER':
      return Number.isFinite(Number(value.replace(',', '.'))) ? null : 'number';
    case 'DATE':
      return isRealDate(value) ? null : 'date';
    case 'SELECT':
      return field.options.includes(value) ? null : 'option';
    case 'PERSON':
      return null;
  }
}

export function validateFieldDrafts(fields: CustomField[], draft: FieldDraft): Record<string, FieldErrorCode> {
  const errors: Record<string, FieldErrorCode> = {};
  for (const f of fields) {
    const code = validateFieldValue(f, draft[f.id] ?? '');
    if (code) errors[f.id] = code;
  }
  return errors;
}

/** Form strings -> typed values for the API; empty values are omitted. */
export function valuesFromDraft(fields: CustomField[], draft: FieldDraft): CustomFieldValues {
  const out: CustomFieldValues = {};
  for (const f of fields) {
    const raw = (draft[f.id] ?? '').trim();
    if (raw === '') continue;
    out[f.id] = f.type === 'NUMBER' ? Number(raw.replace(',', '.')) : raw;
  }
  return out;
}

export interface TaskFormValues {
  title: string;
  priority: string;
  deadline: string;
  labels: string[];
}

export type TaskFormErrors = Partial<Record<'title' | 'deadline' | 'labels', string>>;

/** Returns i18n keys per invalid field. */
export function validateTaskForm(v: TaskFormValues): TaskFormErrors {
  const errors: TaskFormErrors = {};
  const title = v.title.trim();
  if (title === '') errors.title = 'tasks.validation.titleRequired';
  else if (title.length > MAX_TITLE) errors.title = 'tasks.validation.titleTooLong';
  if (v.deadline !== '' && !isRealDate(v.deadline)) errors.deadline = 'tasks.validation.dueDateInvalid';
  if (v.labels.length > MAX_LABELS || v.labels.some((l) => l.trim().length > MAX_LABEL_LENGTH)) {
    errors.labels = 'tasks.validation.labelsInvalid';
  }
  return errors;
}

/** Trims, drops blanks and case-insensitive duplicates. */
export function normalizeLabels(labels: string[]): string[] {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const l of labels.map((x) => x.trim()).filter(Boolean)) {
    const key = l.toLowerCase();
    if (!seen.has(key)) {
      seen.add(key);
      out.push(l);
    }
  }
  return out;
}

/** Moves the item at `from` to `to`; returns a new array. */
export function moveItem<T>(items: readonly T[], from: number, to: number): T[] {
  if (from === to || from < 0 || to < 0 || from >= items.length || to >= items.length) return [...items];
  const next = [...items];
  const [item] = next.splice(from, 1);
  next.splice(to, 0, item);
  return next;
}

export const priorityColor = (p: string): string =>
  p === 'URGENT' ? 'red' : p === 'HIGH' ? 'orange' : p === 'LOW' ? 'gray' : 'cyan';
