import { Navigate, useLocation } from 'react-router-dom';
import { usePageTitle } from '../layout/pageTitle';
import { IntroSplash } from './login/components/IntroSplash';
import { LoginBackground } from './login/components/LoginBackground';
import { LoginCard } from './login/components/LoginCard';
import { LoginShell } from './login/components/LoginShell';
import { useIntroSplash } from './login/hooks/useIntroSplash';
import { useLoginForm } from './login/hooks/useLoginForm';
import { useMotionPreference } from './login/hooks/useMotionPreference';
import { useSuccessTransition } from './login/hooks/useSuccessTransition';

/**
 * Sign-in page. After a successful sign-in on this page the success transition (TR-3) plays for at most
 * `success.transitionMs` before navigating; under reduced motion or the user's pause it navigates at once. A user who
 * arrives already signed in is redirected in the same render with no animation (TR-4). The intro splash (TR-1/2)
 * plays once per browser session on top of the already rendered, focusable form, and only for a user who is not
 * signed in (it is decided once the session bootstrap has answered).
 */
export function LoginPage() {
  usePageTitle('auth.signIn');
  const form = useLoginForm();
  const location = useLocation();
  const { motionReduced } = useMotionPreference();
  const authenticated = form.status === 'authenticated';
  const exiting = authenticated && form.signedInByForm && !motionReduced;
  const transition = useSuccessTransition(exiting);
  // Decided only once /auth/me has answered, so a signed-in user never sees (or consumes) the splash.
  const intro = useIntroSplash(!authenticated && !motionReduced, form.status !== 'loading');

  if (authenticated && (!exiting || transition.done)) {
    const from = (location.state as { from?: string } | null)?.from;
    return <Navigate to={form.mustChangePassword ? '/change-password' : (from ?? '/')} replace />;
  }

  return (
    <>
      <LoginShell background={<LoginBackground />} phase={exiting ? 'exiting' : 'idle'} onExitEnd={transition.finish}>
        <LoginCard form={form} />
      </LoginShell>
      {intro.visible && <IntroSplash onDone={intro.dismiss} />}
    </>
  );
}
