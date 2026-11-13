import { describe, expect, it } from 'vitest';
import { loginPageConfig } from './loginPage.config';
import { buildParticles, particleCount, particleVariantFor } from './particles';

const config = loginPageConfig.particles;

/** Deterministic pseudo-random source. */
function seeded(seed = 7) {
  let s = seed;
  return () => {
    s = (s * 16807) % 2147483647;
    return (s - 1) / 2147483646;
  };
}

describe('particle count (AMB-2)', () => {
  it('stays within 15-30 on desktop, from the config', () => {
    const desktop = particleCount(false);
    expect(desktop).toBeGreaterThanOrEqual(15);
    expect(desktop).toBeLessThanOrEqual(30);
    expect(desktop).toBe(config.count);
  });

  it('halves (rounded up) on phones', () => {
    expect(particleCount(true)).toBe(Math.ceil(particleCount(false) / 2));
    expect(particleCount(true, { ...config, count: 25 })).toBe(13);
  });

  it('clamps a mistuned count into the allowed range', () => {
    expect(particleCount(false, { ...config, count: 100 })).toBe(30);
    expect(particleCount(false, { ...config, count: 3 })).toBe(15);
    expect(particleCount(true, { ...config, count: 3 })).toBe(8);
  });
});

describe('particle layout', () => {
  it('builds the requested number within the variant ranges', () => {
    for (const variant of ['default', 'snow'] as const) {
      const look = config[variant];
      const particles = buildParticles(24, variant, seeded());
      expect(particles).toHaveLength(24);
      for (const p of particles) {
        expect(p.leftPercent).toBeGreaterThanOrEqual(0);
        expect(p.leftPercent).toBeLessThanOrEqual(100);
        expect(p.sizePx).toBeGreaterThanOrEqual(look.minSizePx);
        expect(p.sizePx).toBeLessThanOrEqual(look.maxSizePx);
        expect(p.durationMs).toBeGreaterThanOrEqual(look.minDurationMs);
        expect(p.durationMs).toBeLessThanOrEqual(look.maxDurationMs);
        expect(p.delayMs).toBeLessThanOrEqual(0);
        expect(-p.delayMs).toBeLessThanOrEqual(p.durationMs);
        expect(p.opacity).toBeGreaterThan(0);
        expect(p.opacity).toBeLessThanOrEqual(look.maxOpacity);
        expect(p.swayMs).toBeGreaterThanOrEqual(look.minSwayMs);
        expect(p.swayMs).toBeLessThanOrEqual(look.maxSwayMs);
      }
    }
  });

  it('spreads the particles over the whole width', () => {
    const lefts = buildParticles(20, 'default', seeded(3)).map((p) => p.leftPercent);
    expect(Math.min(...lefts)).toBeLessThan(10);
    expect(Math.max(...lefts)).toBeGreaterThan(90);
  });

  it('is slow enough never to flash (spec §7: at most 3 per second)', () => {
    for (const variant of ['default', 'snow'] as const) {
      expect(config[variant].minDurationMs).toBeGreaterThanOrEqual(5000);
      expect(config[variant].minSwayMs).toBeGreaterThanOrEqual(1000);
    }
  });
});

describe('seasonal variant (SEA-1, SEA-2)', () => {
  const at = (y: number, m: number, d: number) => new Date(y, m - 1, d, 12, 0);

  it('snows from 1 December to the end of February', () => {
    expect(particleVariantFor(at(2031, 11, 30), true)).toBe('default');
    expect(particleVariantFor(at(2031, 12, 1), true)).toBe('snow');
    expect(particleVariantFor(at(2032, 1, 15), true)).toBe('snow');
    expect(particleVariantFor(at(2031, 2, 28), true)).toBe('snow');
    expect(particleVariantFor(at(2032, 2, 29), true)).toBe('snow');
    expect(particleVariantFor(at(2032, 3, 1), true)).toBe('default');
    expect(particleVariantFor(at(2031, 7, 1), true)).toBe('default');
  });

  it('always uses the default particles when seasonal accents are off', () => {
    for (const date of [at(2031, 12, 1), at(2032, 1, 15), at(2032, 2, 29), at(2031, 6, 1)]) {
      expect(particleVariantFor(date, false)).toBe('default');
    }
  });

  it('follows the flag in the config by default', () => {
    expect(particleVariantFor(at(2031, 12, 24))).toBe(loginPageConfig.seasonal.enabled ? 'snow' : 'default');
  });
});
