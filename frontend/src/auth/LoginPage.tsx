import { Navigate, useLocation } from 'react-router-dom';
import { usePageTitle } from '../layout/pageTitle';
import { LoginBackground } from './login/components/LoginBackground';
import { LoginCard } from './login/components/LoginCard';
import { LoginShell } from './login/components/LoginShell';
import { useLoginForm } from './login/hooks/useLoginForm';

export function LoginPage() {
  usePageTitle('auth.signIn');
  const form = useLoginForm();
  const location = useLocation();

  if (form.status === 'authenticated') {
    const from = (location.state as { from?: string } | null)?.from;
    return <Navigate to={form.mustChangePassword ? '/change-password' : (from ?? '/')} replace />;
  }

  return (
    <LoginShell background={<LoginBackground />}>
      <LoginCard form={form} />
    </LoginShell>
  );
}
