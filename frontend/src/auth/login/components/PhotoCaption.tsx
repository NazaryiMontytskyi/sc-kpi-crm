import type { CSSProperties } from 'react';
import { loginPageConfig } from '../loginPage.config';
import classes from './PhotoCaption.module.css';

const { enterMs, enterRisePx } = loginPageConfig.caption;
const STYLE = { '--login-caption-enter': `${enterMs}ms`, '--login-caption-rise': `${enterRisePx}px` } as CSSProperties;

function PinIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
      <path d="M12 22s7-6.2 7-12a7 7 0 1 0-14 0c0 5.8 7 12 7 12z" />
      <circle cx="12" cy="10" r="2.5" />
    </svg>
  );
}

interface PhotoCaptionProps {
  /** Localized place name of the photo on screen; nothing is rendered when undefined (CAP-2). */
  text: string | undefined;
  /** Identity of the slide; a new slide replays the entrance. */
  slideKey: string;
  /** Reduced motion or paused: fade only, no slide-in (§6.3). */
  motionReduced: boolean;
}

/**
 * Place name of the current background photo with a location pin (CAP-1). Fades and slides in with each new slide.
 * Not a live region: a caption change every few seconds must not be announced.
 */
export function PhotoCaption({ text, slideKey, motionReduced }: PhotoCaptionProps) {
  if (!text) return null;
  return (
    <p
      key={slideKey}
      className={`${classes.caption} ${motionReduced ? classes.fade : classes.slideIn}`}
      style={STYLE}
      data-testid="login-photo-caption"
      data-motion={motionReduced ? 'off' : 'on'}
    >
      <span className={classes.pin}>
        <PinIcon />
      </span>
      <span className={classes.text}>{text}</span>
    </p>
  );
}
