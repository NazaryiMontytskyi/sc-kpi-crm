import { ApiProblemError, fieldErrorMap, problemMessage } from '../api/problem';
import type { MemberField } from './memberValidation';

/** Per-field server error: an i18n `key` or an already-localized server `text`. */
export type MemberServerErrors = Partial<Record<MemberField, { key?: string; text?: string }>>;

const FIELDS: readonly MemberField[] = [
  'login',
  'lastName',
  'firstName',
  'middleName',
  'facultyId',
  'studyGroup',
  'telegram',
  'joinedAt',
  'bio',
];

/** Problems that belong to one input rather than to the whole form. */
const CODE_FIELD: Record<string, MemberField> = {
  LOGIN_TAKEN: 'login',
  LOGIN_INVALID: 'login',
  TELEGRAM_INVALID: 'telegram',
  FACULTY_NOT_SELECTABLE: 'facultyId',
  FACULTY_ARCHIVED: 'facultyId',
};

/** Maps a failed save to per-field errors (validation `errors` by name, known codes by field). */
export function memberFieldErrors(error: unknown): MemberServerErrors {
  const result: MemberServerErrors = {};
  const byField = fieldErrorMap(error);
  for (const f of FIELDS) if (byField[f]) result[f] = { text: byField[f] };
  if (error instanceof ApiProblemError && error.code && CODE_FIELD[error.code]) {
    result[CODE_FIELD[error.code]] = { key: `problems.${error.code}` };
  }
  return result;
}

/**
 * i18n key for an error shown outside a field. A 403 always reads as "no permission" (the backend decides,
 * the UI only reacts gracefully), even when the response carries no known problem code.
 */
export function memberErrorKey(error: unknown): string {
  if (error instanceof ApiProblemError && error.status === 403 && error.code !== 'PASSWORD_CHANGE_REQUIRED') {
    return 'problems.FORBIDDEN';
  }
  return problemMessage(error).key;
}
