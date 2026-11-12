import { describe, expect, it } from 'vitest';
import { kenBurnsVars, pickKenBurnsDirection } from './kenBurns';
import { loginPageConfig } from './loginPage.config';

/** Deterministic random source cycling through the given values. */
function sequence(...values: number[]) {
  let i = 0;
  return () => values[i++ % values.length];
}

describe('pickKenBurnsDirection (BG-4)', () => {
  const count = loginPageConfig.kenBurns.directions.length;

  it('picks any preset for the first slide', () => {
    expect(pickKenBurnsDirection(null, count, () => 0)).toBe(0);
    expect(pickKenBurnsDirection(null, count, () => 0.999999)).toBe(count - 1);
  });

  it('never repeats the previous preset, whatever the random source returns', () => {
    for (let previous = 0; previous < count; previous++) {
      for (let r = 0; r < 1; r += 0.01) {
        const picked = pickKenBurnsDirection(previous, count, () => r);
        expect(picked).not.toBe(previous);
        expect(picked).toBeGreaterThanOrEqual(0);
        expect(picked).toBeLessThan(count);
      }
    }
  });

  it('reaches every other preset', () => {
    const reached = new Set<number>();
    for (let k = 0; k < count - 1; k++) reached.add(pickKenBurnsDirection(2, count, () => (k + 0.5) / (count - 1)));
    expect([...reached].sort()).toEqual([...Array(count).keys()].filter((i) => i !== 2));
  });

  it('gives consecutive slides different directions over a long run, also with a constant random source', () => {
    for (const random of [() => 0, () => 0.999999, sequence(0.1, 0.1, 0.9, 0.5, 0.5)]) {
      let previous: number | null = null;
      for (let slide = 0; slide < 50; slide++) {
        const picked = pickKenBurnsDirection(previous, count, random);
        expect(picked).not.toBe(previous);
        previous = picked;
      }
    }
  });

  it('copes with a single preset and out-of-range random values', () => {
    expect(pickKenBurnsDirection(0, 1, () => 0.7)).toBe(0);
    expect(pickKenBurnsDirection(1, count, () => 5)).not.toBe(1);
    expect(pickKenBurnsDirection(1, count, () => -1)).not.toBe(1);
  });

  it('pans less than the zoom overflow, so the photo edges never show', () => {
    const { scaleTo, panPercent } = loginPageConfig.kenBurns;
    expect(panPercent).toBeGreaterThan(0);
    expect(panPercent).toBeLessThan(((scaleTo - 1) / 2) * 100);
    expect(kenBurnsVars({ x: -1, y: 1 })).toEqual({
      '--login-kb-x': `-${panPercent}%`,
      '--login-kb-y': `${panPercent}%`,
    });
    expect(kenBurnsVars({ x: 1, y: 0 })['--login-kb-y']).toBe('0%');
  });
});
