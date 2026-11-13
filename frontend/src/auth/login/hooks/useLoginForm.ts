import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { problemMessage } from '../../../api/problem';
import { SESSION_KEY } from '../../AuthProvider';
import { loginRequest } from '../../authApi';
import { useAuth, type AuthState } from '../../CurrentUser';

export interface LoginFormState {
  /** Auth bootstrap status; `loading` until GET /auth/me has answered (INC-055). */
  status: AuthState['status'];
  mustChangePassword: boolean;
  login: string;
  setLogin: (value: string) => void;
  password: string;
  setPassword: (value: string) => void;
  submit: (event: FormEvent) => void;
  /** Localized message key of the last failed attempt, `null` when there is none. */
  problem: { key: string; params: { minutes?: number } } | null;
  /** Spinner on the submit button: the request is in flight or the session bootstrap is still running. */
  pending: boolean;
  /** Submit button disabled state. */
  disabled: boolean;
  /** Number of failed attempts since mount; changes on every failure (drives the shake, CARD-7). */
  failureCount: number;
  /**
   * This form's own request is in flight or has succeeded. Together with `status === 'authenticated'` it means the
   * user has just signed in here (TR-3 success transition), as opposed to arriving with a session (TR-4, no animation).
   */
  signedInByForm: boolean;
}

/**
 * State, mutation and submit handler of the sign-in form, moved unchanged from `LoginPage` (MAIN_PAGE_FEATURE.md
 * §6.4): same `loginRequest` with the trimmed login, same disabled rules and `status === 'loading'` gating (INC-055),
 * same `problemMessage` mapping, same session update on success.
 */
export function useLoginForm(): LoginFormState {
  const { status, mustChangePassword } = useAuth();
  const queryClient = useQueryClient();
  const [login, setLogin] = useState('');
  const [password, setPassword] = useState('');
  const [failureCount, setFailureCount] = useState(0);
  const mutation = useMutation({
    mutationFn: () => loginRequest(login.trim(), password),
    onSuccess: (session) => queryClient.setQueryData(SESSION_KEY, session),
    onError: () => setFailureCount((n) => n + 1),
  });

  const disabled = status === 'loading' || !login.trim() || !password;

  const submit = (e: FormEvent) => {
    e.preventDefault();
    // Same rule as the disabled button; also covers implicit submission (Enter) while a request is in flight.
    if (disabled || mutation.isPending) return;
    mutation.mutate();
  };
  const problem = mutation.error ? problemMessage(mutation.error) : null;

  return {
    status,
    mustChangePassword,
    login,
    setLogin,
    password,
    setPassword,
    submit,
    problem,
    pending: mutation.isPending || status === 'loading',
    disabled,
    failureCount,
    signedInByForm: mutation.isPending || mutation.isSuccess,
  };
}
