/** RFC 7807 problem as returned by the backend; `code` is the stable machine-readable key. */
export class ApiProblemError extends Error {
  constructor(
    readonly status: number,
    readonly code: string | undefined,
    /** Seconds from the Retry-After header (lockout), when present. */
    readonly retryAfterSeconds?: number,
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

/** Builds an error from an openapi-fetch result that carries an error body. */
export function toProblemError(response: Response, body: unknown): ApiProblemError {
  const raw = response.headers.get('Retry-After');
  const retry = raw !== null && /^\d+$/.test(raw) ? Number(raw) : undefined;
  return new ApiProblemError(response.status, problemCodeOf(body), retry);
}

const KNOWN_CODES = [
  'INVALID_CREDENTIALS',
  'LOGIN_LOCKED',
  'CSRF_INVALID',
  'CURRENT_PASSWORD_INVALID',
  'PASSWORD_UNCHANGED',
  'PASSWORD_POLICY_VIOLATION',
  'PASSWORD_CHANGE_REQUIRED',
] as const;

/** i18n key + params for an error; unknown codes and network failures fall back to a generic message. */
export function problemMessage(error: unknown): { key: string; params: { minutes?: number } } {
  if (error instanceof ApiProblemError && error.code && (KNOWN_CODES as readonly string[]).includes(error.code)) {
    const minutes = error.retryAfterSeconds !== undefined ? Math.max(1, Math.ceil(error.retryAfterSeconds / 60)) : 15;
    return { key: `problems.${error.code}`, params: { minutes } };
  }
  return { key: 'problems.UNKNOWN', params: {} };
}
