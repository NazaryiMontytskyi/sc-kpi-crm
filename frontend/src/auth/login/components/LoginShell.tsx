import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { LanguageSwitch } from '../../LanguageSwitch';
import { HeroPanel } from './HeroPanel';
import classes from './LoginShell.module.css';

interface LoginShellProps {
  /** Full-viewport decoration behind everything (the lazily loaded photo background). */
  background?: ReactNode;
  /** The sign-in card (`LoginCard`). */
  children: ReactNode;
  /** Injected in tests: the clock used for the greeting and the footer year. */
  now?: () => Date;
}

/**
 * Sign-in page layout over the photo background: language switch top-right (FT-1, first in tab order), hero and card
 * side by side from 1024 px or stacked below it (HERO-1), footer line (FT-2). Mobile-first; no horizontal overflow
 * at 360 px.
 */
export function LoginShell({ background, children, now = () => new Date() }: LoginShellProps) {
  const { t } = useTranslation();
  const year = now().getFullYear();
  return (
    <>
      {background}
      <div className={classes.shell} data-testid="login-shell">
        <header className={classes.topBar}>
          <LanguageSwitch size="xs" color="studrada" classNames={{ root: classes.switchRoot, label: classes.switchLabel }} />
        </header>
        <main className={classes.main}>
          <HeroPanel now={now} />
          <div className={classes.cardColumn}>
            {children}
          </div>
        </main>
        <footer className={classes.footer}>{t('auth.login.footer', { year })}</footer>
      </div>
    </>
  );
}
