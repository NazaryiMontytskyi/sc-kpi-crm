import type { FacultyRequest } from './facultyApi';

export type FacultyField = 'code' | 'nameUk' | 'nameEn';
/** Field -> i18n key of the error. Empty object means valid. */
export type FacultyErrors = Partial<Record<FacultyField, string>>;

const CODE_PATTERN = /^[A-Za-z0-9._-]+$/;

/** Client-side mirror of the backend rules (the backend stays authoritative). */
export function validateFaculty(values: FacultyRequest): FacultyErrors {
  const errors: FacultyErrors = {};
  const code = values.code.trim();
  if (!code) errors.code = 'faculties.validation.required';
  else if (code.length > 30) errors.code = 'faculties.validation.codeTooLong';
  else if (!CODE_PATTERN.test(code)) errors.code = 'faculties.validation.codeFormat';
  for (const f of ['nameUk', 'nameEn'] as const) {
    const v = values[f].trim();
    if (!v) errors[f] = 'faculties.validation.required';
    else if (v.length > 200) errors[f] = 'faculties.validation.nameTooLong';
  }
  return errors;
}
