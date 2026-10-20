import { ApiProblemError, problemMessage } from '../api/problem';
import type { CurrentUser } from '../auth/CurrentUser';
import type { CustomField, CustomFieldValues, Space, TaskQuery, TaskSummary } from './tasksApi';

export const TASKS_VIEW = 'tasks.view';
export const TASKS_CREATE = 'tasks.create';
export const TASKS_ARCHIVE = 'tasks.archive';
export const SPACES_MANAGE = 'spaces.manage';

export const MAX_TITLE = 300;
export const MAX_FIELD_TEXT = 2000;
export const MAX_LABELS = 20;
export const MAX_LABEL_LENGTH = 50;

/** i18n key for a failed task action; a 403 reads as "no permission" (the backend stays authoritative). */
export function taskErrorKey(error: unknown): string {
  if (error instanceof ApiProblemError && error.status === 403) return 'problems.FORBIDDEN';
  return problemMessage(error).key;
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
  return [q.spaceId, q.statusId, q.dueFrom, q.dueTo].filter(Boolean).length + (q.labels.length > 0 ? 1 : 0) + (q.includeArchived ? 1 : 0);
}

/** Today in Europe/Kyiv as `YYYY-MM-DD` (due dates are calendar dates, never shifted). */
export function todayKyiv(now: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Kyiv', year: 'numeric', month: '2-digit', day: '2-digit' }).format(now);
}

export function isOverdue(task: Pick<TaskSummary, 'dueDate' | 'archived'>, today: string): boolean {
  return !!task.dueDate && !task.archived && task.dueDate < today;
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
  dueDate: string;
  labels: string[];
}

export type TaskFormErrors = Partial<Record<'title' | 'dueDate' | 'labels', string>>;

/** Returns i18n keys per invalid field. */
export function validateTaskForm(v: TaskFormValues): TaskFormErrors {
  const errors: TaskFormErrors = {};
  const title = v.title.trim();
  if (title === '') errors.title = 'tasks.validation.titleRequired';
  else if (title.length > MAX_TITLE) errors.title = 'tasks.validation.titleTooLong';
  if (v.dueDate !== '' && !isRealDate(v.dueDate)) errors.dueDate = 'tasks.validation.dueDateInvalid';
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
