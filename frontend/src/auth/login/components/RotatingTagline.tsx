import { useEffect, useState, type CSSProperties } from 'react';
import { loginPageConfig } from '../loginPage.config';
import classes from './RotatingTagline.module.css';

const { intervalMs: DEFAULT_INTERVAL, transitionMs, wordStaggerMs, risePx, fadeMs } = loginPageConfig.tagline;
const STYLE = {
  '--login-tagline-transition': `${transitionMs}ms`,
  '--login-tagline-stagger': `${wordStaggerMs}ms`,
  '--login-tagline-rise': `${risePx}px`,
  '--login-tagline-fade': `${fadeMs}ms`,
} as CSSProperties;

/**
 * `motion`: word-level slide-up transition (HERO-4); `fade`: plain cross-fade under reduced motion; `static`: the
 * user paused motion, the current phrase stays (§6.3, WCAG 2.2.2).
 */
export type TaglineMode = 'motion' | 'fade' | 'static';

interface RotatingTaglineProps {
  phrases: readonly string[];
  mode: TaglineMode;
  intervalMs?: number;
  className?: string;
}

type PhraseState = 'shown' | 'in' | 'out' | 'idle';

/**
 * Tagline under the hero subtitle cycling through the i18n phrases every `tagline.intervalMs` (HERO-4). Every phrase
 * is stacked in the same grid cell, so the box always has the height of the tallest one (no layout shift). Only the
 * current phrase is exposed to assistive technology, without a live region, so the rotation is never announced.
 * A single phrase is static and starts no timer.
 */
export function RotatingTagline({ phrases, mode, intervalMs = DEFAULT_INTERVAL, className }: RotatingTaglineProps) {
  const [step, setStep] = useState({ index: 0, previous: null as number | null });
  const count = phrases.length;
  const rotating = count > 1 && mode !== 'static';

  useEffect(() => {
    if (!rotating) return;
    const timer = setInterval(
      () => setStep((s) => ({ index: (s.index + 1) % count, previous: s.index % count })),
      intervalMs,
    );
    return () => clearInterval(timer);
  }, [rotating, count, intervalMs]);

  if (count === 0) return null;
  const index = step.index % count;

  const stateOf = (i: number): PhraseState => {
    if (i === index) return step.previous === null ? 'shown' : 'in';
    return i === step.previous ? 'out' : 'idle';
  };

  return (
    <p className={`${classes.tagline} ${className ?? ''}`.trim()} style={STYLE} data-mode={mode} data-testid="login-tagline">
      {phrases.map((phrase, i) => {
        const state = stateOf(i);
        const current = i === index;
        return (
          <span
            key={i}
            className={`${classes.phrase} ${mode === 'motion' ? classes.moving : classes.fading}`}
            data-state={state}
            aria-hidden={current ? undefined : true}
          >
            {mode === 'motion'
              ? phrase.split(/\s+/).filter(Boolean).map((word, w, words) => (
                  <span key={`${w}-${word}`} className={classes.word} style={{ '--login-tagline-word': w } as CSSProperties}>
                    {word}
                    {w < words.length - 1 ? ' ' : null}
                  </span>
                ))
              : phrase}
          </span>
        );
      })}
    </p>
  );
}
