export interface FieldError {
  field: string;
  /** Message already localized by the backend. */
  message: string;
}

/** RFC 7807 problem as returned by the backend; `code` is the stable machine-readable key. */
export class ApiProblemError extends Error {
  constructor(
    readonly status: number,
    readonly code: string | undefined,
    /** Seconds from the Retry-After header (lockout), when present. */
    readonly retryAfterSeconds?: number,
    /** Per-field validation errors (`errors` list of a VALIDATION_FAILED problem). */
    readonly fieldErrors: readonly FieldError[] = [],
  ) {
    super(code ?? `HTTP ${status}`);
    this.name = 'ApiProblemError';
  }
}

export function problemCodeOf(body: unknown): string | undefined {
  if (typeof body === 'object' && body !== null && 'code' in body) {
    const code = (body as { code?: unknown }).code;
    return typeof code === 'string' ? code : undefined;
  }
  return undefined;
}

/** Reads the `errors` list of a validation problem; malformed entries are ignored. */
export function fieldErrorsOf(body: unknown): FieldError[] {
  if (typeof body !== 'object' || body === null || !('errors' in body)) return [];
  const raw = (body as { errors?: unknown }).errors;
  if (!Array.isArray(raw)) return [];
  return raw.flatMap((e: unknown) => {
    if (typeof e !== 'object' || e === null) return [];
    const { field, message } = e as { field?: unknown; message?: unknown };
    return typeof field === 'string' && typeof message === 'string' ? [{ field, message }] : [];
  });
}

/** Field name -> first message, for forms. */
export function fieldErrorMap(error: unknown): Record<string, string> {
  const map: Record<string, string> = {};
  if (error instanceof ApiProblemError) {
    for (const e of error.fieldErrors) if (!(e.field in map)) map[e.field] = e.message;
  }
  return map;
}

/** Builds an error from an openapi-fetch result that carries an error body. */
export function toProblemError(response: Response, body: unknown): ApiProblemError {
  const raw = response.headers.get('Retry-After');
  const retry = raw !== null && /^\d+$/.test(raw) ? Number(raw) : undefined;
  return new ApiProblemError(response.status, problemCodeOf(body), retry, fieldErrorsOf(body));
}

const KNOWN_CODES = [
  'INVALID_CREDENTIALS',
  'LOGIN_LOCKED',
  'CSRF_INVALID',
  'CURRENT_PASSWORD_INVALID',
  'PASSWORD_UNCHANGED',
  'PASSWORD_POLICY_VIOLATION',
  'PASSWORD_CHANGE_REQUIRED',
  'FORBIDDEN',
  'NOT_FOUND',
  'VALIDATION_FAILED',
  'FACULTY_CODE_TAKEN',
  'FACULTY_NAME_TAKEN',
  'FACULTY_ARCHIVED',
  'ROLE_NAME_TAKEN',
  'ROLE_NAME_RESERVED',
  'ROLE_NAME_INVALID',
  'UNKNOWN_PERMISSION',
  'SYSTEM_ROLE_IMMUTABLE',
  'ADMIN_ROLE_RESERVED',
  'INVALID_FILTER',
  'MEMBER_ARCHIVED',
  'ALREADY_ARCHIVED',
  'NOT_ARCHIVED',
  'CANNOT_ARCHIVE_SELF',
  'TELEGRAM_INVALID',
  'LOGIN_TAKEN',
  'LOGIN_INVALID',
  'FACULTY_NOT_SELECTABLE',
  'FILE_EMPTY',
  'FILE_TOO_LARGE',
  'FILE_TYPE_NOT_ALLOWED',
  'ORG_UNIT_PARENT_REQUIRED',
  'ORG_UNIT_PARENT_NOT_ALLOWED',
  'ORG_UNIT_PARENT_NOT_FOUND',
  'ORG_UNIT_PARENT_ARCHIVED',
  'ORG_UNIT_CYCLE',
  'ORG_UNIT_LEADERSHIP_EXISTS',
  'ORG_UNIT_HAS_ACTIVE_CHILDREN',
  'ORG_UNIT_ARCHIVE_BLOCKED',
  'ORG_UNIT_ARCHIVED',
  'MEMBERSHIP_USER_NOT_FOUND',
  'MEMBERSHIP_ORG_UNIT_ARCHIVED',
  'MEMBERSHIP_DUPLICATE',
  'MEMBERSHIP_ALREADY_ENDED',
  'MEMBERSHIP_INVALID_PERIOD',
  'MEMBERSHIP_MOVE_SAME_UNIT',
  'POSITION_ARCHIVED',
  'POSITION_CAPACITY_INVALID',
  'POSITION_HEAD_SINGLE_HOLDER',
  'POSITION_HEAD_EXISTS',
  'POSITION_FULL',
  'POSITION_CAPACITY_BELOW_HOLDERS',
  'POSITION_HAS_ACTIVE_APPOINTMENTS',
  'POSITION_ROLE_UNAVAILABLE',
  'APPOINTMENT_USER_NOT_FOUND',
  'APPOINTMENT_DUPLICATE',
  'APPOINTMENT_ALREADY_ENDED',
  'APPOINTMENT_INVALID_PERIOD',
  'APPOINTMENT_DATE_IN_FUTURE',
  'CUSTOM_FIELD_NAME_TAKEN',
  'CUSTOM_FIELD_OPTION_DUPLICATE',
  'CUSTOM_FIELD_OPTION_INVALID',
  'CUSTOM_FIELD_OPTIONS_NOT_ALLOWED',
  'CUSTOM_FIELD_OPTIONS_REQUIRED',
  'CUSTOM_FIELD_OPTIONS_TOO_MANY',
  'CUSTOM_FIELD_REQUIRED',
  'CUSTOM_FIELD_UNKNOWN',
  'CUSTOM_FIELD_VALUE_INVALID',
  'SPACE_ARCHIVED',
  'SPACE_GENERAL_HAS_ORG_UNIT',
  'SPACE_ORG_UNIT_ARCHIVED',
  'SPACE_STATUS_CATEGORY_REQUIRED',
  'SPACE_STATUS_IN_USE',
  'SPACE_STATUS_NAME_TAKEN',
  'SPACE_STATUS_ORDER_MISMATCH',
  'SPACE_STATUS_REPLACEMENT_INVALID',
  'TASK_ARCHIVED',
  'TASK_ATTACHMENT_INVALID',
  'TASK_EDIT_FORBIDDEN',
  'TASK_LABEL_INVALID',
  'TASK_REFERENCE_INVALID',
  'TASK_RELATED_INVALID',
  'TASK_RELATED_SELF',
  'TASK_STATUS_NOT_IN_SPACE',
  'TASK_TOO_MANY_ATTACHMENTS',
  'TASK_TOO_MANY_LABELS',
  'TASK_TOO_MANY_RELATED',
  'COMMENT_ARCHIVED',
  'COMMENT_ATTACHMENT_INVALID',
  'COMMENT_BODY_INVALID',
  'COMMENT_EDIT_FORBIDDEN',
  'COMMENT_EMPTY',
  'COMMENT_MENTION_INVALID',
  'COMMENT_MENTIONS_MISMATCH',
  'COMMENT_TARGET_UNSUPPORTED',
  'COMMENT_TOO_LONG',
  'COMMENT_TOO_MANY_ATTACHMENTS',
  'COMMENT_TOO_MANY_MENTIONS',
] as const;

/** i18n key + params for an error; unknown codes and network failures fall back to a generic message. */
export function problemMessage(error: unknown): { key: string; params: { minutes?: number } } {
  if (error instanceof ApiProblemError && error.code && (KNOWN_CODES as readonly string[]).includes(error.code)) {
    const minutes = error.retryAfterSeconds !== undefined ? Math.max(1, Math.ceil(error.retryAfterSeconds / 60)) : 15;
    return { key: `problems.${error.code}`, params: { minutes } };
  }
  return { key: 'problems.UNKNOWN', params: {} };
}
