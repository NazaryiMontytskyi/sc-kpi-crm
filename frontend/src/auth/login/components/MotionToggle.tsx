import { useTranslation } from 'react-i18next';
import { useMotionPreference } from '../hooks/useMotionPreference';
import classes from './MotionToggle.module.css';

function PauseIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <rect x="6" y="5" width="4" height="14" rx="1" />
      <rect x="14" y="5" width="4" height="14" rx="1" />
    </svg>
  );
}

function PlayIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M7 4.5v15a1 1 0 0 0 1.5.86l12-7.5a1 1 0 0 0 0-1.72l-12-7.5A1 1 0 0 0 7 4.5z" />
    </svg>
  );
}

/**
 * Pause/play of the background motion (FT-3, WCAG 2.2.2): slideshow, Ken Burns, tagline rotation and the other
 * decorations. A toggle button: the name stays the same and `aria-pressed` tells whether motion is paused; the
 * icon shows what pressing it does. The choice is stored in localStorage (useMotionPreference).
 */
export function MotionToggle() {
  const { t } = useTranslation();
  const { paused, togglePaused } = useMotionPreference();
  const label = t('auth.login.motion.pause');
  return (
    <button
      type="button"
      className={classes.toggle}
      aria-pressed={paused}
      aria-label={label}
      title={label}
      onClick={togglePaused}
      data-testid="login-motion-toggle"
    >
      {paused ? <PlayIcon /> : <PauseIcon />}
    </button>
  );
}
