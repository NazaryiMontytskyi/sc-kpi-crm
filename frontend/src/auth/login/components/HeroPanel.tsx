import type { Ref } from 'react';
import { useTranslation } from 'react-i18next';
import { BrandLogo } from '../../../brand/BrandLogo';
import { useMediaQueryMatch } from '../hooks/useMediaQueryMatch';
import { useMotionPreference } from '../hooks/useMotionPreference';
import { useTimeOfDayGreeting } from '../hooks/useTimeOfDayGreeting';
import { DESKTOP_QUERY } from '../layoutQueries';
import classes from './HeroPanel.module.css';
import { RotatingTagline, type TaglineMode } from './RotatingTagline';
import { StaggeredHeadline } from './StaggeredHeadline';

export const HERO_DESKTOP_QUERY = DESKTOP_QUERY;

interface HeroPanelProps {
  /** Injected in tests: the moment used for the greeting. */
  now?: () => Date;
  /** Injected in tests: brand files (defaults to assets/brand/). */
  assets?: Record<string, string>;
  /** The section element; LoginShell moves it with the pointer (AMB-1 parallax). */
  ref?: Ref<HTMLElement>;
}

/** The i18n tagline phrases (HERO-4); anything but a list of non-empty strings yields none. */
export function taglinePhrases(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value.filter((phrase): phrase is string => typeof phrase === 'string' && phrase.trim().length > 0);
}

/**
 * HERO-1..5 over the dark photo overlay: white logo (full on desktop, mark below 1024 px; never on a chip),
 * time-of-day greeting, title with a staggered reveal, subtitle and rotating tagline (both desktop only, like the
 * subtitle the tagline belongs under). All text is white or studrada shade 2. Under reduced motion or the user's
 * pause the title is plain text and the tagline fades (reduced motion) or stays (paused).
 */
export function HeroPanel({ now, assets, ref }: HeroPanelProps) {
  const { t } = useTranslation();
  const greeting = useTimeOfDayGreeting(now);
  const desktop = useMediaQueryMatch(HERO_DESKTOP_QUERY);
  const { paused, prefersReducedMotion } = useMotionPreference();
  const taglineMode: TaglineMode = paused ? 'static' : prefersReducedMotion ? 'fade' : 'motion';
  const phrases = taglinePhrases(t('auth.login.taglines', { returnObjects: true }));

  return (
    <section ref={ref} className={classes.hero} data-testid="login-hero" data-layout={desktop ? 'desktop' : 'compact'}>
      <div className={classes.logo}>
        {desktop ? (
          <BrandLogo variant="full-white" height={72} assets={assets} />
        ) : (
          <BrandLogo variant="mark-white" short height={48} assets={assets} />
        )}
      </div>
      <p className={classes.greeting}>{t(greeting)}</p>
      <StaggeredHeadline className={classes.title} text={t('auth.login.title')} animate={!paused && !prefersReducedMotion} />
      <p className={classes.subtitle}>{t('auth.login.subtitle')}</p>
      {desktop && <RotatingTagline phrases={phrases} mode={taglineMode} />}
    </section>
  );
}
