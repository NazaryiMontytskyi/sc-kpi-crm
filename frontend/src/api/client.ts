import createClient, { type Middleware } from 'openapi-fetch';
import { env } from '../config/env';
import { CSRF_COOKIE, CSRF_HEADER, isStateChanging, readCookie } from './csrf';
import { problemCodeOf } from './problem';
import { emitSessionEvent } from './sessionEvents';
import type { paths } from './generated/schema';

/**
 * Echoes the XSRF-TOKEN cookie in the X-XSRF-TOKEN header on state-changing calls
 * (backend scheme, see ADR-0002). The session cookie CRMSESSION is HttpOnly and sent via `credentials`.
 */
export const csrfMiddleware: Middleware = {
  onRequest({ request }) {
    if (isStateChanging(request.method)) {
      const token = readCookie(CSRF_COOKIE);
      if (token) request.headers.set(CSRF_HEADER, token);
    }
    return request;
  },
};

/** Sign-in and session probing legitimately answer 401; every other 401 means the session is gone. */
const SESSION_PROBE = /\/auth\/(login|me)$/;

export const sessionMiddleware: Middleware = {
  async onResponse({ request, response }) {
    const path = new URL(request.url).pathname;
    if (response.status === 401 && !SESSION_PROBE.test(path)) {
      emitSessionEvent('unauthorized');
    } else if (response.status === 403 && !path.endsWith('/auth/logout')) {
      const body: unknown = await response
        .clone()
        .json()
        .catch(() => undefined);
      if (problemCodeOf(body) === 'PASSWORD_CHANGE_REQUIRED') emitSessionEvent('passwordChangeRequired');
    }
    return undefined;
  },
};

export const api = createClient<paths>({
  baseUrl: env.apiBaseUrl,
  credentials: 'include',
});
api.use(csrfMiddleware);
api.use(sessionMiddleware);

export type { paths, components } from './generated/schema';
