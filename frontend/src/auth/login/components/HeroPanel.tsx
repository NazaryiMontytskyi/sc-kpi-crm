import { useTranslation } from 'react-i18next';
import { BrandLogo } from '../../../brand/BrandLogo';
import { useMediaQueryMatch } from '../hooks/useMediaQueryMatch';
import { useTimeOfDayGreeting } from '../hooks/useTimeOfDayGreeting';
import { loginPageConfig } from '../loginPage.config';
import classes from './HeroPanel.module.css';

export const HERO_DESKTOP_QUERY = `(min-width: ${loginPageConfig.layout.desktopMinWidthEm}em)`;

interface HeroPanelProps {
  /** Injected in tests: the moment used for the greeting. */
  now?: () => Date;
  /** Injected in tests: brand files (defaults to assets/brand/). */
  assets?: Record<string, string>;
}

/**
 * HERO-1..3 over the dark photo overlay: white logo (full on desktop, mark below 1024 px; never on a chip),
 * time-of-day greeting, title and subtitle. All text is white or studrada shade 2.
 */
export function HeroPanel({ now, assets }: HeroPanelProps) {
  const { t } = useTranslation();
  const greeting = useTimeOfDayGreeting(now);
  const desktop = useMediaQueryMatch(HERO_DESKTOP_QUERY);

  return (
    <section className={classes.hero} data-testid="login-hero" data-layout={desktop ? 'desktop' : 'compact'}>
      <div className={classes.logo}>
        {desktop ? (
          <BrandLogo variant="full-white" height={72} assets={assets} />
        ) : (
          <BrandLogo variant="mark-white" short height={48} assets={assets} />
        )}
      </div>
      <p className={classes.greeting}>{t(greeting)}</p>
      <h1 className={classes.title}>{t('auth.login.title')}</h1>
      <p className={classes.subtitle}>{t('auth.login.subtitle')}</p>
    </section>
  );
}
