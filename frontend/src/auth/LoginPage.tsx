import { Alert, Button, PasswordInput, Stack, TextInput } from '@mantine/core';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Navigate, useLocation } from 'react-router-dom';
import { problemMessage } from '../api/problem';
import { usePageTitle } from '../layout/pageTitle';
import { SESSION_KEY } from './AuthProvider';
import { AuthLayout } from './AuthLayout';
import { loginRequest } from './authApi';
import { useAuth } from './CurrentUser';

export function LoginPage() {
  const { t } = useTranslation();
  usePageTitle('auth.signIn');
  const { status, mustChangePassword } = useAuth();
  const location = useLocation();
  const queryClient = useQueryClient();
  const [login, setLogin] = useState('');
  const [password, setPassword] = useState('');
  const mutation = useMutation({
    mutationFn: () => loginRequest(login.trim(), password),
    onSuccess: (session) => queryClient.setQueryData(SESSION_KEY, session),
  });

  if (status === 'authenticated') {
    const from = (location.state as { from?: string } | null)?.from;
    return <Navigate to={mustChangePassword ? '/change-password' : (from ?? '/')} replace />;
  }

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (!login.trim() || !password) return;
    mutation.mutate();
  };
  const problem = mutation.error ? problemMessage(mutation.error) : null;

  return (
    <AuthLayout title={t('app.name')} subtitle={t('auth.subtitle')}>
      <form onSubmit={submit} noValidate>
        <Stack gap="sm">
          {problem && (
            <Alert color="red" role="alert" data-testid="login-error">
              {t(problem.key, problem.params)}
            </Alert>
          )}
          <TextInput
            label={t('auth.login')}
            autoComplete="username"
            value={login}
            onChange={(e) => setLogin(e.currentTarget.value)}
            size="md"
            required
          />
          <PasswordInput
            label={t('auth.password')}
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.currentTarget.value)}
            size="md"
            required
          />
          <Button type="submit" size="md" fullWidth loading={mutation.isPending} disabled={!login.trim() || !password}>
            {t('auth.signIn')}
          </Button>
        </Stack>
      </form>
    </AuthLayout>
  );
}
