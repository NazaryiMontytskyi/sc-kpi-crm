import { useEffect, useEffectEvent, type CSSProperties } from 'react';
import { BrandLogo } from '../../../brand/BrandLogo';
import { loginPageConfig } from '../loginPage.config';
import classes from './IntroSplash.module.css';

const { logoHoldMs, logoFadeInMs, revealMs, revealLogoScale } = loginPageConfig.intro;

/** TR-1: the whole intro (logo hold + reveal); at most 1600 ms. */
export const INTRO_TOTAL_MS = logoHoldMs + revealMs;

const STYLE = {
  '--login-intro-hold': `${logoHoldMs}ms`,
  '--login-intro-logo-in': `${logoFadeInMs}ms`,
  '--login-intro-reveal': `${revealMs}ms`,
  '--login-intro-logo-scale': String(revealLogoScale),
} as CSSProperties;

/** Events that skip the intro (TR-2): click, pointer tap, any key. */
const DISMISS_EVENTS = ['pointerdown', 'click', 'keydown'] as const;

interface IntroSplashProps {
  /** Called once when the intro has played or was skipped. */
  onDone: () => void;
}

/**
 * TR-1 / TR-2 intro: the white logo mark (through BrandLogo, no chip) centred on a dark disc that covers the screen,
 * then the disc shrinks away (a scaled circular element: transform only, no clip-path) while the logo grows and
 * fades. Purely decorative: hidden from assistive technology, never focusable and no focus trap; the form underneath
 * is rendered and focusable the whole time. Click, tap or any key ends it at once.
 */
export function IntroSplash({ onDone }: IntroSplashProps) {
  const done = useEffectEvent(onDone);

  useEffect(() => {
    const finish = () => done();
    const timer = setTimeout(finish, INTRO_TOTAL_MS);
    for (const type of DISMISS_EVENTS) window.addEventListener(type, finish, true);
    return () => {
      clearTimeout(timer);
      for (const type of DISMISS_EVENTS) window.removeEventListener(type, finish, true);
    };
  }, []);

  return (
    <div className={classes.root} style={STYLE} aria-hidden="true" data-testid="login-intro">
      <div className={classes.disc} />
      <div className={classes.logo}>
        <BrandLogo variant="mark-white" short height={96} />
      </div>
    </div>
  );
}
