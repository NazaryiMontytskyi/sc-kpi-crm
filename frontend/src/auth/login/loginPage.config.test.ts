import { describe, expect, it } from 'vitest';
import { loginPageConfig } from './loginPage.config';

describe('loginPageConfig', () => {
  const { slideshow, kenBurns, card, intro, success, parallax, particles, indicator, tagline } = loginPageConfig;

  it('uses the BG-3 slide and crossfade durations', () => {
    expect(slideshow.slideDurationMs).toBe(8000);
    expect(slideshow.crossfadeMs).toBe(1500);
    expect(slideshow.slideDurationMs).toBeGreaterThan(slideshow.crossfadeMs);
  });

  it('slows the slideshow down under reduced motion', () => {
    expect(slideshow.reducedMotionIntervalMs).toBe(15000);
    expect(slideshow.reducedMotionIntervalMs).toBeGreaterThan(slideshow.slideDurationMs);
  });

  it('keeps Ken Burns within 1.00-1.08 with several distinct directions', () => {
    expect(kenBurns.scaleFrom).toBe(1);
    expect(kenBurns.scaleTo).toBe(1.08);
    const unique = new Set(kenBurns.directions.map((d) => `${d.x},${d.y}`));
    expect(unique.size).toBe(kenBurns.directions.length);
    expect(unique.size).toBeGreaterThanOrEqual(2);
  });

  it('respects the spec limits of the other effects', () => {
    expect(tagline.intervalMs).toBe(4000);
    expect(card.shakeMs).toBeLessThanOrEqual(400);
    expect(card.entranceRisePx).toBe(24);
    expect(intro.logoHoldMs + intro.revealMs).toBeLessThanOrEqual(1600);
    expect(success.transitionMs).toBeLessThanOrEqual(900);
    expect(parallax.backgroundMaxPx).toBe(12);
    expect(parallax.heroMaxPx).toBe(6);
    expect(particles.minCount).toBe(15);
    expect(particles.maxCount).toBe(30);
    expect(particles.phoneFactor).toBe(0.5);
    expect(indicator.maxSegments).toBe(10);
    expect(typeof loginPageConfig.seasonal.enabled).toBe('boolean');
  });
});

describe('sign-in page timings', () => {
  // Raw source of the sign-in page code; the config module and tests are the only places allowed to hold timings.
  const sources = import.meta.glob(['/src/auth/login/**/*.{ts,tsx}', '!/src/auth/login/**/*.test.{ts,tsx}'], {
    query: '?raw',
    import: 'default',
    eager: true,
  }) as Record<string, string>;
  const CONFIG = '/src/auth/login/loginPage.config.ts';

  it('keeps timer durations out of components and hooks', () => {
    const literalTimer = /\b(setTimeout|setInterval)\([^;]*?,\s*\d/;
    const offenders = Object.entries(sources)
      .filter(([path]) => path !== CONFIG)
      .filter(([, code]) => literalTimer.test(code))
      .map(([path]) => path);
    expect(Object.keys(sources)).toContain(CONFIG);
    expect(offenders).toEqual([]);
  });
});
