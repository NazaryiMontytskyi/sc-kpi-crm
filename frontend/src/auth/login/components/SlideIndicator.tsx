import type { CSSProperties } from 'react';
import { useTranslation } from 'react-i18next';
import { loginPageConfig } from '../loginPage.config';
import classes from './SlideIndicator.module.css';

export type IndicatorMode = 'animate' | 'static';

interface SlideIndicatorProps {
  /** Number of photos in the rotation. */
  count: number;
  /** Position of the photo on screen in the rotation (0-based). */
  active: number;
  /** Changes whenever the current slide starts its lifetime; restarts the fill. */
  progressKey: number;
  /** Lifetime of a slide; the active segment fills over it. */
  durationMs: number;
  /** False while paused, while the tab is hidden or while the photo is still loading: the fill freezes. */
  running: boolean;
  /** `static` under reduced motion: the active segment is shown full, nothing animates. */
  mode: IndicatorMode;
  /** Jump to the photo at this position (CAP-4). */
  onSelect: (position: number) => void;
}

/**
 * "Stories" progress bar of the background slideshow (CAP-3, CAP-4): one segment per photo up to
 * `indicator.maxSegments`, each a button that jumps to its photo; with more photos one continuous line instead.
 * The fill animates `transform: scaleX` only.
 */
export function SlideIndicator({ count, active, progressKey, durationMs, running, mode, onSelect }: SlideIndicatorProps) {
  const { t } = useTranslation();
  if (count < 2) return null;
  const style = { '--login-indicator-duration': `${durationMs}ms` } as CSSProperties;
  const fill = (
    <span
      key={progressKey}
      className={`${classes.fill} ${mode === 'animate' ? classes.filling : classes.full}`}
      data-running={running ? 'true' : 'false'}
      data-testid="login-indicator-fill"
    />
  );

  if (count > loginPageConfig.indicator.maxSegments) {
    return (
      <div className={classes.line} style={style} data-testid="login-indicator" data-variant="line" aria-hidden="true">
        <span className={classes.track}>{fill}</span>
      </div>
    );
  }

  return (
    <div
      role="group"
      aria-label={t('auth.login.indicator.label')}
      className={classes.segments}
      style={style}
      data-testid="login-indicator"
      data-variant="segments"
    >
      {Array.from({ length: count }, (_, position) => (
        <button
          key={position}
          type="button"
          className={classes.segment}
          aria-label={t('auth.login.indicator.goTo', { index: position + 1, count })}
          aria-current={position === active ? 'true' : undefined}
          data-state={position < active ? 'done' : position === active ? 'active' : 'todo'}
          onClick={() => onSelect(position)}
        >
          <span className={classes.track}>{position === active ? fill : null}</span>
        </button>
      ))}
    </div>
  );
}
