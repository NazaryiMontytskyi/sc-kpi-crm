import { useMemo, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { LanguageSwitch } from '../../LanguageSwitch';
import { HeroPanel } from './HeroPanel';
import classes from './LoginShell.module.css';
import { MotionToggle } from './MotionToggle';
import { SlideControlsHostsContext } from './slideControlsHosts';

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
 * side by side from 1024 px or stacked below it (HERO-1), slide caption and indicator slots (CAP-1, CAP-3; filled by
 * the lazy background), footer line (FT-2) with the motion toggle last in tab order (FT-3). Mobile-first; no
 * horizontal overflow at 360 px. The slots have a fixed height, so the controls never shift the layout.
 */
export function LoginShell({ background, children, now = () => new Date() }: LoginShellProps) {
  const { t } = useTranslation();
  const year = now().getFullYear();
  const [captionCompact, setCaptionCompact] = useState<HTMLElement | null>(null);
  const [captionWide, setCaptionWide] = useState<HTMLElement | null>(null);
  const [indicator, setIndicator] = useState<HTMLElement | null>(null);
  const hosts = useMemo(() => ({ captionCompact, captionWide, indicator }), [captionCompact, captionWide, indicator]);

  return (
    <SlideControlsHostsContext.Provider value={hosts}>
      {background}
      <div className={classes.shell} data-testid="login-shell">
        <header className={classes.topBar}>
          <div ref={setCaptionCompact} className={classes.captionCompact} data-testid="login-caption-slot-compact" />
          <LanguageSwitch size="xs" color="studrada" classNames={{ root: classes.switchRoot, label: classes.switchLabel }} />
        </header>
        <main className={classes.main}>
          <HeroPanel now={now} />
          <div className={classes.cardColumn}>
            {children}
          </div>
        </main>
        <footer className={classes.footer}>
          <div className={classes.slideBar}>
            <div ref={setCaptionWide} className={classes.captionWide} data-testid="login-caption-slot-wide" />
            <div ref={setIndicator} className={classes.indicatorSlot} data-testid="login-indicator-slot" />
          </div>
          <div className={classes.footerLine}>
            <span className={classes.footerSpacer} aria-hidden="true" />
            <p className={classes.footerText}>{t('auth.login.footer', { year })}</p>
            <MotionToggle />
          </div>
        </footer>
      </div>
    </SlideControlsHostsContext.Provider>
  );
}
