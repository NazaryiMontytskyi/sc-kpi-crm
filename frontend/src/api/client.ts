import createClient, { type Middleware } from 'openapi-fetch';
import { env } from '../config/env';
import { CSRF_COOKIE, CSRF_HEADER, isStateChanging, readCookie } from './csrf';
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

export const api = createClient<paths>({
  baseUrl: env.apiBaseUrl,
  credentials: 'include',
});
api.use(csrfMiddleware);

export type { paths, components } from './generated/schema';
