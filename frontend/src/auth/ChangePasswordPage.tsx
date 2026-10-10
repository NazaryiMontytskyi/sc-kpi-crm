import { Alert, Button, PasswordInput, Stack } from '@mantine/core';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Navigate } from 'react-router-dom';
import { problemMessage } from '../api/problem';
import { usePageTitle } from '../layout/pageTitle';
import { SESSION_KEY } from './AuthProvider';
import { AuthLayout } from './AuthLayout';
import { changePasswordRequest } from './authApi';
import { useAuth } from './CurrentUser';

/** Forced screen for users with a temporary password; the only route they can reach (besides sign-out). */
export function ChangePasswordPage() {
  const { t } = useTranslation();
  usePageTitle('auth.changePassword.title');
  const { status, mustChangePassword, signOut } = useAuth();
  const queryClient = useQueryClient();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const mutation = useMutation({
    mutationFn: () => changePasswordRequest(current, next),
    onSuccess: (session) => queryClient.setQueryData(SESSION_KEY, session),
  });

  if (status === 'loading') return null;
  if (status === 'anonymous') return <Navigate to="/login" replace />;
  if (!mustChangePassword) return <Navigate to="/" replace />;

  const mismatch = confirm !== '' && confirm !== next;
  const canSubmit = current !== '' && next !== '' && confirm === next;
  const problem = mutation.error ? problemMessage(mutation.error) : null;

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (canSubmit) mutation.mutate();
  };

  return (
    <AuthLayout title={t('auth.changePassword.title')} subtitle={t('auth.changePassword.subtitle')}>
      <form onSubmit={submit} noValidate>
        <Stack gap="sm">
          {problem && (
            <Alert color="red" role="alert" data-testid="change-password-error">
              {t(problem.key, problem.params)}
            </Alert>
          )}
          <PasswordInput
            label={t('auth.changePassword.current')}
            autoComplete="current-password"
            value={current}
            onChange={(e) => setCurrent(e.currentTarget.value)}
            size="md"
            required
          />
          <PasswordInput
            label={t('auth.changePassword.new')}
            description={t('auth.changePassword.hint')}
            autoComplete="new-password"
            value={next}
            onChange={(e) => setNext(e.currentTarget.value)}
            size="md"
            required
          />
          <PasswordInput
            label={t('auth.changePassword.confirm')}
            autoComplete="new-password"
            value={confirm}
            onChange={(e) => setConfirm(e.currentTarget.value)}
            error={mismatch ? t('auth.changePassword.mismatch') : undefined}
            size="md"
            required
          />
          <Button type="submit" size="md" fullWidth loading={mutation.isPending} disabled={!canSubmit}>
            {t('auth.changePassword.submit')}
          </Button>
          <Button variant="subtle" color="gray" onClick={signOut}>
            {t('layout.signOut')}
          </Button>
        </Stack>
      </form>
    </AuthLayout>
  );
}
