import { Fragment, useState, type CSSProperties } from 'react';
import { loginPageConfig } from '../loginPage.config';
import classes from './StaggeredHeadline.module.css';

const { staggerStepMs, wordMs, risePx } = loginPageConfig.headline;
const STYLE = {
  '--login-headline-step': `${staggerStepMs}ms`,
  '--login-headline-word': `${wordMs}ms`,
  '--login-headline-rise': `${risePx}px`,
} as CSSProperties;

interface StaggeredHeadlineProps {
  text: string;
  className?: string;
  /** False under reduced motion or pause: plain text, no spans (§6.3). */
  animate: boolean;
}

/**
 * The hero `h1` with a word-by-word fade + rise on first paint (HERO-5). Assistive technology gets the full text
 * through `aria-label`; the animated word spans are `aria-hidden`. Once the last word has arrived, or when motion is
 * reduced/paused, it is plain text. Nothing reserves extra room: the spans occupy their final place from the start.
 */
export function StaggeredHeadline({ text, className, animate }: StaggeredHeadlineProps) {
  const [revealed, setRevealed] = useState(false);
  const words = text.split(/\s+/).filter(Boolean);

  if (!animate || revealed || words.length === 0) {
    return (
      <h1 className={className} data-reveal="off">
        {text}
      </h1>
    );
  }

  return (
    <h1 className={className} aria-label={text} style={STYLE} data-reveal="on">
      {words.map((word, index) => (
        <Fragment key={`${index}-${word}`}>
          <span
            aria-hidden="true"
            className={classes.word}
            style={{ '--login-headline-index': index } as CSSProperties}
            onAnimationEnd={index === words.length - 1 ? () => setRevealed(true) : undefined}
          >
            {word}
          </span>
          {index < words.length - 1 ? ' ' : null}
        </Fragment>
      ))}
    </h1>
  );
}
