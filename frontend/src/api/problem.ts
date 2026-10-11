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
] as const;

/** i18n key + params for an error; unknown codes and network failures fall back to a generic message. */
export function problemMessage(error: unknown): { key: string; params: { minutes?: number } } {
  if (error instanceof ApiProblemError && error.code && (KNOWN_CODES as readonly string[]).includes(error.code)) {
    const minutes = error.retryAfterSeconds !== undefined ? Math.max(1, Math.ceil(error.retryAfterSeconds / 60)) : 15;
    return { key: `problems.${error.code}`, params: { minutes } };
  }
  return { key: 'problems.UNKNOWN', params: {} };
}
