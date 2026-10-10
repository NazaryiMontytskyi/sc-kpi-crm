import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, type ReactNode } from 'react';
import { onSessionEvent } from '../api/sessionEvents';
import i18n, { changeLanguage, isLanguage, type Language } from '../i18n';
import { changeLocaleRequest, fetchSession, logoutRequest, type Session } from './authApi';
import { ANONYMOUS_STATE, CurrentUserProvider, type AuthState } from './CurrentUser';

export const SESSION_KEY = ['auth', 'session'] as const;

/** Backs the auth context with GET /auth/me and reacts to 401 / PASSWORD_CHANGE_REQUIRED from any API call. */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const query = useQuery<Session | null>({
    queryKey: SESSION_KEY,
    queryFn: fetchSession,
    retry: false,
    staleTime: Infinity,
  });
  const session = query.data ?? null;

  const dropSession = useCallback(() => {
    queryClient.setQueryData(SESSION_KEY, null);
    queryClient.removeQueries({ predicate: (q) => q.queryKey[0] !== SESSION_KEY[0] });
  }, [queryClient]);

  useEffect(
    () =>
      onSessionEvent((event) => {
        if (event === 'unauthorized') dropSession();
        else void queryClient.invalidateQueries({ queryKey: SESSION_KEY });
      }),
    [queryClient, dropSession],
  );

  // Apply the profile language after sign-in / when it changes on the server.
  const profileLocale = session?.locale;
  const userId = session?.user.id;
  useEffect(() => {
    if (isLanguage(profileLocale) && profileLocale !== i18n.language) void changeLanguage(profileLocale);
  }, [userId, profileLocale]);

  const signOut = useCallback(() => {
    void logoutRequest().finally(dropSession);
  }, [dropSession]);

  const changeLocale = useCallback(
    async (lng: Language) => {
      await changeLanguage(lng);
      if (!queryClient.getQueryData<Session | null>(SESSION_KEY)) return;
      try {
        queryClient.setQueryData(SESSION_KEY, await changeLocaleRequest(lng));
      } catch {
        // The language stays applied locally; the next successful change will persist it.
      }
    },
    [queryClient],
  );

  const value = useMemo<AuthState>(() => {
    if (query.isPending) return { ...ANONYMOUS_STATE, status: 'loading', signOut, changeLocale };
    if (!session) return { ...ANONYMOUS_STATE, signOut, changeLocale };
    return {
      status: 'authenticated',
      user: session.user,
      mustChangePassword: session.mustChangePassword,
      signOut,
      changeLocale,
    };
  }, [query.isPending, session, signOut, changeLocale]);

  return <CurrentUserProvider value={value}>{children}</CurrentUserProvider>;
}
