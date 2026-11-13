import { useMemo, type CSSProperties } from 'react';
import { useDocumentVisible } from '../hooks/useDocumentVisible';
import { useMediaQueryMatch } from '../hooks/useMediaQueryMatch';
import { useMotionPreference } from '../hooks/useMotionPreference';
import { COARSE_POINTER_QUERY, PHONE_QUERY } from '../layoutQueries';
import { buildParticles, particleCount, type ParticleSpec, type ParticleVariant } from '../particles';
import classes from './Particles.module.css';

interface ParticlesProps {
  /** `default` soft dots drifting upward; `snow` (SEA-1) light snowfall. Same layer, same rules. */
  variant?: ParticleVariant;
  /** Random source of the layout (tests). */
  random?: () => number;
}

const styleOf = (p: ParticleSpec) =>
  ({
    '--p-left': `${p.leftPercent}%`,
    '--p-size': `${p.sizePx}px`,
    '--p-duration': `${p.durationMs}ms`,
    '--p-delay': `${p.delayMs}ms`,
    '--p-sway': `${p.swayPx}px`,
    '--p-sway-duration': `${p.swayMs}ms`,
    '--p-opacity': String(p.opacity),
  }) as CSSProperties;

/**
 * AMB-2 floating particles: 15-30 small soft dots (count from loginPage.config.ts, halved on phones and touch
 * devices) drifting across the screen with a slight sway, pure CSS (transform + opacity). Decorative: aria-hidden,
 * ignores the pointer, below the card. Not rendered at all under reduced motion or the user's pause (it comes back
 * on resume); frozen while the tab is hidden.
 */
export function Particles({ variant = 'default', random }: ParticlesProps) {
  const { motionReduced } = useMotionPreference();
  const narrow = useMediaQueryMatch(PHONE_QUERY);
  const coarse = useMediaQueryMatch(COARSE_POINTER_QUERY);
  const visible = useDocumentVisible();
  const count = particleCount(narrow || coarse);
  const particles = useMemo(() => buildParticles(count, variant, random), [count, variant, random]);

  if (motionReduced) return null;
  return (
    <div
      className={classes.root}
      aria-hidden="true"
      data-testid="login-particles"
      data-variant={variant}
      data-running={visible ? 'true' : 'false'}
    >
      {particles.map((particle, index) => (
        <span key={index} className={classes.particle} style={styleOf(particle)}>
          <span className={classes.dot} />
        </span>
      ))}
    </div>
  );
}
