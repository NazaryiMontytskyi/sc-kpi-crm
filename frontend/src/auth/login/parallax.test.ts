import { describe, expect, it } from 'vitest';
import { loginPageConfig } from './loginPage.config';
import { lerp, parallaxTargets, stepOffsets, ZERO_OFFSETS } from './parallax';

const { backgroundMaxPx, heroMaxPx } = loginPageConfig.parallax;

describe('parallax maths (AMB-1)', () => {
  it('is at rest with the pointer in the centre', () => {
    expect(parallaxTargets(500, 400, 1000, 800)).toEqual(ZERO_OFFSETS);
  });

  it('reaches exactly 12 px for the background and 6 px for the hero at the edges', () => {
    expect(parallaxTargets(1000, 800, 1000, 800)).toEqual({
      bgX: -backgroundMaxPx,
      bgY: -backgroundMaxPx,
      heroX: heroMaxPx,
      heroY: heroMaxPx,
    });
    expect(parallaxTargets(0, 0, 1000, 800)).toEqual({
      bgX: backgroundMaxPx,
      bgY: backgroundMaxPx,
      heroX: -heroMaxPx,
      heroY: -heroMaxPx,
    });
    expect(backgroundMaxPx).toBe(12);
    expect(heroMaxPx).toBe(6);
  });

  it('clamps pointer positions outside the viewport', () => {
    const far = parallaxTargets(-5000, 99999, 1000, 800);
    expect(far).toEqual({ bgX: backgroundMaxPx, bgY: -backgroundMaxPx, heroX: -heroMaxPx, heroY: heroMaxPx });
    for (let x = -200; x <= 1200; x += 37) {
      const t = parallaxTargets(x, x, 1000, 800);
      expect(Math.abs(t.bgX)).toBeLessThanOrEqual(12);
      expect(Math.abs(t.bgY)).toBeLessThanOrEqual(12);
      expect(Math.abs(t.heroX)).toBeLessThanOrEqual(6);
      expect(Math.abs(t.heroY)).toBeLessThanOrEqual(6);
    }
  });

  it('moves the background opposite to the hero', () => {
    for (const [x, y] of [
      [100, 700],
      [900, 50],
      [250, 250],
      [760, 610],
    ]) {
      const t = parallaxTargets(x, y, 1000, 800);
      expect(Math.sign(t.bgX)).toBe(-Math.sign(t.heroX));
      expect(Math.sign(t.bgY)).toBe(-Math.sign(t.heroY));
    }
  });

  it('is safe with a zero-sized or invalid viewport', () => {
    expect(parallaxTargets(10, 10, 0, 0)).toEqual(ZERO_OFFSETS);
    expect(parallaxTargets(Number.NaN, 10, 100, 100).bgX).toBe(0);
  });

  it('lerps part of the way per frame', () => {
    expect(lerp(0, 10, 0.5)).toBe(5);
    expect(lerp(10, 10, 0.3)).toBe(10);
    expect(lerp(-4, 4, 0)).toBe(-4);
  });

  it('converges to the target and then reports settled', () => {
    const target = parallaxTargets(1000, 0, 1000, 800);
    let current = { ...ZERO_OFFSETS };
    let frames = 0;
    let settled = false;
    while (!settled && frames < 1000) {
      const step = stepOffsets(current, target);
      // Monotonic: never overshoots.
      expect(Math.abs(target.bgX - step.next.bgX)).toBeLessThanOrEqual(Math.abs(target.bgX - current.bgX));
      current = step.next;
      settled = step.settled;
      frames++;
    }
    expect(settled).toBe(true);
    expect(current).toEqual(target);
    // About 1-2 s at 60 fps with the configured smoothing: smooth, but not sluggish.
    expect(frames).toBeGreaterThan(10);
    expect(frames).toBeLessThan(200);
  });
});
