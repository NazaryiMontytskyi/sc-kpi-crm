import { createContext, useContext, type ReactNode } from 'react';

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
  signOut: () => void;
}

/** Abstraction over the session. INC-008 replaces the provider value with data from /auth/me. */
export const ANONYMOUS_STATE: AuthState = { status: 'anonymous', user: null, signOut: () => undefined };

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

/** Temporary stand-in until INC-008. Obviously fake person. */
export const STUB_AUTH_STATE: AuthState = {
  status: 'authenticated',
  user: { id: 'stub', displayName: 'Test User', isAdmin: false, permissions: [] },
  signOut: () => undefined,
};
