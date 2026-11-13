import { useMemo, useRef, useState, type CSSProperties, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { LanguageSwitch } from '../../LanguageSwitch';
import { useMediaQueryMatch } from '../hooks/useMediaQueryMatch';
import { useMotionPreference } from '../hooks/useMotionPreference';
import { useParallax } from '../hooks/useParallax';
import { FINE_POINTER_QUERY } from '../layoutQueries';
import { loginPageConfig } from '../loginPage.config';
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
  /**
   * TR-3: `exiting` plays the success transition (card scales down and fades, background zooms and blurs,
   * full-screen fade); the frame is inert meanwhile. `onExitEnd` fires when the full-screen fade has finished.
   */
  phase?: LoginPhase;
  onExitEnd?: () => void;
}

export type LoginPhase = 'idle' | 'exiting';

const { success, parallax } = loginPageConfig;

/** Timings of the success transition and the parallax bleed, handed to the CSS module. */
const PHASE_STYLE = {
  '--login-success-card': `${success.cardMs}ms`,
  '--login-success-card-scale': String(success.cardScale),
  '--login-success-bg': `${success.backgroundMs}ms`,
  '--login-success-bg-scale': String(success.backgroundScale),
  '--login-success-blur': `${success.backgroundBlurPx}px`,
  '--login-success-fade': `${success.fadeMs}ms`,
  '--login-success-fade-delay': `${success.fadeDelayMs}ms`,
  '--login-parallax-bleed': `${parallax.backgroundMaxPx}px`,
} as CSSProperties;

/**
 * Sign-in page layout over the photo background: language switch top-right (FT-1, first in tab order), hero and card
 * side by side from 1024 px or stacked below it (HERO-1), slide caption and indicator slots (CAP-1, CAP-3; filled by
 * the lazy background), footer line (FT-2) with the motion toggle last in tab order (FT-3). Mobile-first; no
 * horizontal overflow at 360 px. The slots have a fixed height, so the controls never shift the layout.
 *
 * Ambient motion (AMB-1): with a fine hover pointer and motion allowed, the background drifts opposite to the pointer
 * and the hero with it (useParallax writes CSS variables; no re-renders). The background sits in a slightly larger
 * layer, so its edges never show.
 */
export function LoginShell({ background, children, now = () => new Date(), phase = 'idle', onExitEnd }: LoginShellProps) {
  const { t } = useTranslation();
  const exiting = phase === 'exiting';
  const finePointer = useMediaQueryMatch(FINE_POINTER_QUERY);
  const { motionReduced } = useMotionPreference();
  const backgroundLayer = useRef<HTMLDivElement>(null);
  const hero = useRef<HTMLElement>(null);
  useParallax(backgroundLayer, hero, finePointer && !motionReduced && !exiting);
  const year = now().getFullYear();
  const [captionCompact, setCaptionCompact] = useState<HTMLElement | null>(null);
  const [captionWide, setCaptionWide] = useState<HTMLElement | null>(null);
  const [indicator, setIndicator] = useState<HTMLElement | null>(null);
  const hosts = useMemo(() => ({ captionCompact, captionWide, indicator }), [captionCompact, captionWide, indicator]);

  return (
    <SlideControlsHostsContext.Provider value={hosts}>
      {background && (
        <div className={classes.backdrop} style={PHASE_STYLE} data-phase={phase} data-testid="login-backdrop">
          <div className={classes.successLayer}>
            <div ref={backgroundLayer} className={classes.parallaxLayer}>
              {background}
            </div>
          </div>
        </div>
      )}
      <div className={classes.shell} style={PHASE_STYLE} data-phase={phase} inert={exiting} data-testid="login-shell">
        <header className={classes.topBar}>
          <div ref={setCaptionCompact} className={classes.captionCompact} data-testid="login-caption-slot-compact" />
          <LanguageSwitch size="xs" color="studrada" classNames={{ root: classes.switchRoot, label: classes.switchLabel }} />
        </header>
        <main className={classes.main}>
          <HeroPanel ref={hero} now={now} />
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
      {exiting && (
        <div
          className={classes.successFade}
          style={PHASE_STYLE}
          aria-hidden="true"
          data-testid="login-success-fade"
          onAnimationEnd={(event) => {
            if (event.target === event.currentTarget) onExitEnd?.();
          }}
        />
      )}
    </SlideControlsHostsContext.Provider>
  );
}
