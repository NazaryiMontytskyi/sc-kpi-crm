export interface MemberFormValues {
  login: string;
  lastName: string;
  firstName: string;
  middleName: string;
  facultyId: string | null;
  studyGroup: string;
  telegram: string;
  joinedAt: string;
  bio: string;
}

export type MemberField = keyof MemberFormValues;
/** Field -> i18n key of the error. Empty object means valid. */
export type MemberErrors = Partial<Record<MemberField, string>>;

const LOGIN_PATTERN = /^[A-Za-z0-9._-]{3,64}$/;
const TELEGRAM_PATTERN = /^@?[A-Za-z0-9_]{5,32}$/;

function isRealDate(value: string): boolean {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!m) return false;
  const [y, mo, d] = [Number(m[1]), Number(m[2]), Number(m[3])];
  const date = new Date(Date.UTC(y, mo - 1, d));
  return date.getUTCFullYear() === y && date.getUTCMonth() === mo - 1 && date.getUTCDate() === d;
}

export interface ValidationMode {
  /** Creation also needs a login. */
  withLogin: boolean;
  /** Self-edit covers telegram and bio only. */
  selfOnly?: boolean;
}

/** Client-side mirror of the backend rules (the backend stays authoritative). */
export function validateMember(values: MemberFormValues, mode: ValidationMode): MemberErrors {
  const errors: MemberErrors = {};
  const tooLong = (field: MemberField, max: number, key = 'members.validation.tooLong') => {
    if (values[field] && String(values[field]).trim().length > max) errors[field] = key;
  };

  if (!mode.selfOnly) {
    if (mode.withLogin && !LOGIN_PATTERN.test(values.login.trim())) {
      errors.login = values.login.trim() ? 'members.validation.loginFormat' : 'members.validation.required';
    }
    for (const f of ['lastName', 'firstName'] as const) {
      if (!values[f].trim()) errors[f] = 'members.validation.required';
      else tooLong(f, 100);
    }
    tooLong('middleName', 100);
    if (!values.facultyId) errors.facultyId = 'members.validation.required';
    if (!values.studyGroup.trim()) errors.studyGroup = 'members.validation.required';
    else tooLong('studyGroup', 50);
    if (!values.joinedAt) errors.joinedAt = 'members.validation.required';
    else if (!isRealDate(values.joinedAt)) errors.joinedAt = 'members.validation.dateInvalid';
  }

  const telegram = values.telegram.trim();
  if (!telegram) errors.telegram = 'members.validation.required';
  else if (!TELEGRAM_PATTERN.test(telegram)) errors.telegram = 'members.validation.telegramFormat';

  if (values.bio.length > 4000) errors.bio = 'members.validation.bioTooLong';
  return errors;
}
