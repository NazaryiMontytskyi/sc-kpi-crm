import { ActionIcon, Alert, Button, Text, Title } from '@mantine/core';
import { useId, useState, type CSSProperties, type KeyboardEvent, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { usePrefersReducedMotion } from '../hooks/usePrefersReducedMotion';
import type { LoginFormState } from '../hooks/useLoginForm';
import { loginPageConfig } from '../loginPage.config';
import { FloatingField } from './FloatingField';
import { LoginAlternativesSlot } from './LoginAlternativesSlot';
import classes from './LoginCard.module.css';

const {
  entranceMs,
  entranceRisePx,
  fieldStaggerMs,
  shakeMs,
  shakeDistancePx,
  errorFadeMs,
  labelFloatMs,
  focusGlowMs,
  buttonLiftPx,
  buttonLiftMs,
  shineMs,
  glassBlurPx,
} = loginPageConfig.card;

/** Timings of loginPage.config.ts handed to the CSS module. */
export const LOGIN_CARD_STYLE = {
  '--login-card-entrance': `${entranceMs}ms`,
  '--login-card-rise': `${entranceRisePx}px`,
  '--login-field-stagger': `${fieldStaggerMs}ms`,
  '--login-shake': `${shakeMs}ms`,
  '--login-shake-distance': `${shakeDistancePx}px`,
  '--login-error-fade': `${errorFadeMs}ms`,
  '--login-label-float': `${labelFloatMs}ms`,
  '--login-focus-glow': `${focusGlowMs}ms`,
  '--login-lift-distance': `${buttonLiftPx}px`,
  '--login-lift': `${buttonLiftMs}ms`,
  '--login-shine': `${shineMs}ms`,
  '--login-glass-blur': `${glassBlurPx}px`,
} as CSSProperties;

const itemStyle = (index: number) => ({ '--login-item': index }) as CSSProperties;

function EyeIcon({ crossed }: { crossed: boolean }) {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
      <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z" />
      <circle cx="12" cy="12" r="3" />
      {crossed && <path d="m3 3 18 18" />}
    </svg>
  );
}

function WarningIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
      <path d="M12 3 2 21h20L12 3z" />
      <path d="M12 10v5M12 18h.01" />
    </svg>
  );
}

interface LoginCardProps {
  /** Sign-in form state from `useLoginForm`. */
  form: LoginFormState;
  /** CARD-9: future sign-in alternatives below the button; nothing today. */
  alternatives?: ReactNode;
}

/**
 * The glass sign-in card (CARD-1..9): entrance with a staggered reveal, floating-label fields, password visibility
 * toggle and Caps Lock hint, submit button with hover lift and light sweep, shake + fading message on a failed
 * attempt. The form logic itself lives in `useLoginForm`.
 */
export function LoginCard({ form, alternatives }: LoginCardProps) {
  const { t } = useTranslation();
  const reducedMotion = usePrefersReducedMotion();
  const ids = useId();
  const titleId = `${ids}-title`;
  const loginId = `${ids}-login`;
  const passwordId = `${ids}-password`;
  const errorId = `${ids}-error`;
  const capsId = `${ids}-caps`;
  const [passwordVisible, setPasswordVisible] = useState(false);
  const [capsLock, setCapsLock] = useState(false);

  const { problem, failureCount } = form;
  // Two identical keyframes alternate so that every failure restarts the animation (CARD-7); none when reduced.
  const shakeClass = !reducedMotion && failureCount > 0 ? (failureCount % 2 === 1 ? classes.shakeA : classes.shakeB) : '';

  const describedBy = (...parts: (string | false)[]) => parts.filter(Boolean).join(' ') || undefined;
  const trackCapsLock = (e: KeyboardEvent<HTMLInputElement>) => setCapsLock(e.getModifierState('CapsLock'));

  return (
    <div
      className={`${classes.host} ${shakeClass}`.trim()}
      style={LOGIN_CARD_STYLE}
      data-testid="login-card"
      data-shake={shakeClass ? 'on' : undefined}
    >
      <section className={classes.card} aria-labelledby={titleId}>
        <header className={classes.header}>
          <Title order={2} size="h4" id={titleId} className={classes.title}>
            {t('app.name')}
          </Title>
          <Text size="sm" className={classes.subtitle}>
            {t('auth.subtitle')}
          </Text>
        </header>
        <form onSubmit={form.submit} noValidate className={classes.form}>
          <div aria-live="polite" className={classes.errorRegion}>
            {problem && (
              <Alert
                key={failureCount}
                id={errorId}
                color="red"
                role="alert"
                data-testid="login-error"
                className={classes.error}
              >
                {t(problem.key, problem.params)}
              </Alert>
            )}
          </div>
          <div className={classes.item} style={itemStyle(0)}>
            <FloatingField
              id={loginId}
              label={t('auth.loginLabel')}
              autoComplete="username"
              autoCapitalize="none"
              spellCheck={false}
              value={form.login}
              onValueChange={form.setLogin}
              aria-describedby={describedBy(!!problem && errorId)}
              required
            />
          </div>
          <div className={classes.item} style={itemStyle(1)}>
            <FloatingField
              id={passwordId}
              label={t('auth.password')}
              type={passwordVisible ? 'text' : 'password'}
              autoComplete="current-password"
              value={form.password}
              onValueChange={form.setPassword}
              onKeyDown={trackCapsLock}
              onKeyUp={trackCapsLock}
              // Stale once focus leaves the field; kept while moving to the visibility toggle inside it.
              onBlur={(e) => {
                if (!e.currentTarget.parentElement?.contains(e.relatedTarget)) setCapsLock(false);
              }}
              aria-describedby={describedBy(!!problem && errorId, capsLock && capsId)}
              required
              rightSection={
                <ActionIcon
                  type="button"
                  variant="subtle"
                  color="gray"
                  size="lg"
                  className={classes.toggle}
                  aria-label={t(passwordVisible ? 'auth.login.hidePassword' : 'auth.login.showPassword')}
                  aria-controls={passwordId}
                  onClick={() => setPasswordVisible((v) => !v)}
                >
                  <EyeIcon crossed={passwordVisible} />
                </ActionIcon>
              }
            />
            <div aria-live="polite" className={classes.capsRegion}>
              {capsLock && (
                <p id={capsId} className={classes.capsHint} data-testid="login-caps-lock">
                  <span className={classes.capsIcon}>
                    <WarningIcon />
                  </span>
                  {t('auth.login.capsLock')}
                </p>
              )}
            </div>
          </div>
          <div className={classes.item} style={itemStyle(2)}>
            <Button
              type="submit"
              size="md"
              fullWidth
              className={classes.submit}
              loading={form.pending}
              disabled={form.disabled}
            >
              {t('auth.signIn')}
            </Button>
          </div>
          <LoginAlternativesSlot>{alternatives}</LoginAlternativesSlot>
        </form>
      </section>
    </div>
  );
}
