import { loginPageConfig } from './loginPage.config';

/** AMB-2 / SEA-1: the particle variants; `snow` is a variant of the same layer, not a separate system. */
export type ParticleVariant = 'default' | 'snow';

type ParticlesConfig = typeof loginPageConfig.particles;

/** The part of the particle config that decides the count. */
export interface ParticleCountConfig {
  minCount: number;
  maxCount: number;
  count: number;
  phoneFactor: number;
}

/**
 * SEA-1 / SEA-2: light snowfall in the configured months (Dec-Feb by default, local date), otherwise the default
 * particles; always the default when seasonal accents are switched off.
 */
export function particleVariantFor(
  date: Date,
  seasonalEnabled: boolean = loginPageConfig.seasonal.enabled,
  snowMonths: readonly number[] = loginPageConfig.seasonal.snowMonths,
): ParticleVariant {
  return seasonalEnabled && snowMonths.includes(date.getMonth()) ? 'snow' : 'default';
}

/** AMB-2: the desktop count clamped to 15-30, halved (rounded up) on phones. */
export function particleCount(phone: boolean, config: ParticleCountConfig = loginPageConfig.particles): number {
  const desktop = Math.min(config.maxCount, Math.max(config.minCount, Math.round(config.count)));
  return phone ? Math.ceil(desktop * config.phoneFactor) : desktop;
}

/** One particle: everything the CSS needs, handed over as custom properties. */
export interface ParticleSpec {
  /** Horizontal start position, percent of the layer width. */
  leftPercent: number;
  sizePx: number;
  /** One trip across the screen. */
  durationMs: number;
  /** Negative: the particle starts part-way through its trip, so the screen is never empty at first paint. */
  delayMs: number;
  /** Sideways sway amplitude and the period of one sway. */
  swayPx: number;
  swayMs: number;
  opacity: number;
}

const between = (random: () => number, min: number, max: number) => min + random() * (max - min);
const round = (value: number, digits = 2) => Number(value.toFixed(digits));

/** `count` particles of a variant, evenly spread over the width with jitter, from the given random source. */
export function buildParticles(
  count: number,
  variant: ParticleVariant,
  random: () => number = Math.random,
  config: ParticlesConfig = loginPageConfig.particles,
): ParticleSpec[] {
  const look = config[variant];
  return Array.from({ length: Math.max(0, count) }, (_, i) => {
    const durationMs = Math.round(between(random, look.minDurationMs, look.maxDurationMs));
    return {
      leftPercent: round(((i + random()) / count) * 100),
      sizePx: round(between(random, look.minSizePx, look.maxSizePx), 1),
      durationMs,
      delayMs: -Math.round(random() * durationMs),
      swayPx: round(between(random, look.swayPx / 2, look.swayPx), 1),
      swayMs: Math.round(between(random, look.minSwayMs, look.maxSwayMs)),
      opacity: round(between(random, look.maxOpacity / 2, look.maxOpacity)),
    };
  });
}
