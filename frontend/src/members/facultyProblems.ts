import { ApiProblemError, fieldErrorMap } from '../api/problem';
import type { Faculty, FacultyRequest } from './facultyApi';
import type { FacultyField } from './facultyValidation';

/** Per-field server error: an i18n `key` or an already-localized server `text`. */
export type ServerFieldErrors = Partial<Record<FacultyField, { key?: string; text?: string }>>;

/**
 * Maps a failed save to per-field errors: validation errors by field name; duplicate code on `code`.
 * The backend reports a duplicate name without saying which language, so the error is attached to the
 * name field(s) whose value matches an existing faculty, or to both when none is identifiable.
 */
export function serverFieldErrors(error: unknown, values: FacultyRequest, known: readonly Faculty[]): ServerFieldErrors {
  const result: ServerFieldErrors = {};
  const byField = fieldErrorMap(error);
  for (const f of ['code', 'nameUk', 'nameEn'] as const) {
    if (byField[f]) result[f] = { text: byField[f] };
  }
  if (error instanceof ApiProblemError) {
    if (error.code === 'FACULTY_CODE_TAKEN') result.code = { key: 'problems.FACULTY_CODE_TAKEN' };
    if (error.code === 'FACULTY_NAME_TAKEN') {
      const same = (a: string, b: string) => a.trim().toLowerCase() === b.trim().toLowerCase();
      const matches = (['nameUk', 'nameEn'] as const).filter((f) => known.some((k) => same(k[f], values[f])));
      for (const f of matches.length > 0 ? matches : (['nameUk', 'nameEn'] as const)) {
        result[f] = { key: 'problems.FACULTY_NAME_TAKEN' };
      }
    }
  }
  return result;
}
