import { createContext, useContext, type ReactNode } from 'react';
import { changeLanguage, type Language } from '../i18n';

export interface CurrentUser {
  id: string;
  displayName: string;
  isAdmin: boolean;
  /** Permission keys from CONTEXT.md §5.2, e.g. "tasks.create". */
  permissions: readonly string[];
}

export interface AuthState {
  status: 'loading' | 'authenticated' | 'anonymous';
  user: CurrentUser | null;
  /** True while a temporary password must be replaced; only the change-password screen is reachable. */
  mustChangePassword: boolean;
  signOut: () => void;
  /** Applies the language locally and persists it to the profile when signed in. */
  changeLocale: (lng: Language) => Promise<void>;
}

export const ANONYMOUS_STATE: AuthState = {
  status: 'anonymous',
  user: null,
  mustChangePassword: false,
  signOut: () => undefined,
  changeLocale: changeLanguage,
};

const AuthContext = createContext<AuthState>(ANONYMOUS_STATE);

export function CurrentUserProvider({ value, children }: { value: AuthState; children: ReactNode }) {
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  return useContext(AuthContext);
}

/** Admin is the only hardcoded role (CONTEXT.md §5); everyone else is checked by permission key. */
export function hasPermission(user: CurrentUser | null, permission: string): boolean {
  if (!user) return false;
  return user.isAdmin || user.permissions.includes(permission);
}

/** Whether the signed-in user holds `permission` (from /auth/me). UI convenience only; the backend enforces. */
export function usePermission(permission: string): boolean {
  return hasPermission(useAuth().user, permission);
}

/** Static signed-in state for tests. Obviously fake person. */
export const STUB_AUTH_STATE: AuthState = {
  status: 'authenticated',
  user: { id: 'stub', displayName: 'Test User', isAdmin: false, permissions: [] },
  mustChangePassword: false,
  signOut: () => undefined,
  changeLocale: changeLanguage,
};
