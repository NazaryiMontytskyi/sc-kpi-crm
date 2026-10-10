import { api, type components } from '../api/client';
import { toProblemError } from '../api/problem';
import type { Language } from '../i18n';
import type { CurrentUser } from './CurrentUser';

type MeResponse = components['schemas']['MeResponse'];

export interface Session {
  user: CurrentUser;
  locale: string | undefined;
  mustChangePassword: boolean;
}

export function toSession(me: MeResponse): Session {
  return {
    user: {
      id: me.id ?? '',
      // The API exposes the login only; a display name arrives with the members module.
      displayName: me.login ?? '',
      // The backend returns every permission key for Admin, so no separate flag is needed.
      isAdmin: false,
      permissions: me.permissions ?? [],
    },
    locale: me.locale,
    mustChangePassword: me.mustChangePassword === true,
  };
}

/** Current session, or null when not signed in (401). */
export async function fetchSession(): Promise<Session | null> {
  const { data, error, response } = await api.GET('/api/v1/auth/me');
  if (response.status === 401) return null;
  if (!data) throw toProblemError(response, error);
  return toSession(data);
}

export async function loginRequest(login: string, password: string): Promise<Session> {
  const { data, error, response } = await api.POST('/api/v1/auth/login', { body: { login, password } });
  if (!data) throw toProblemError(response, error);
  return toSession(data);
}

export async function logoutRequest(): Promise<void> {
  await api.POST('/api/v1/auth/logout');
}

export async function changePasswordRequest(currentPassword: string, newPassword: string): Promise<Session> {
  const { data, error, response } = await api.POST('/api/v1/auth/change-password', {
    body: { currentPassword, newPassword },
  });
  if (!data) throw toProblemError(response, error);
  return toSession(data);
}

export async function changeLocaleRequest(locale: Language): Promise<Session> {
  const { data, error, response } = await api.PATCH('/api/v1/auth/me/locale', { body: { locale } });
  if (!data) throw toProblemError(response, error);
  return toSession(data);
}
