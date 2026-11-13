import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { loginPageConfig } from '../loginPage.config';
import { shuffledOrder, useSlideshow, type UseSlideshowOptions } from './useSlideshow';

const { slideDurationMs, reducedMotionIntervalMs } = loginPageConfig.slideshow;

/** Deterministic random source (mulberry32). */
function seeded(seed: number) {
  let a = seed;
  return () => {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const slides = (n: number) => Array.from({ length: n }, (_, i) => `image_${i + 1}.jpg`);

function setup(n: number, options: UseSlideshowOptions = {}) {
  const list = slides(n);
  return renderHook((props: UseSlideshowOptions) => useSlideshow(list, props), {
    initialProps: { random: seeded(42), ...options },
  });
}

type Hook = ReturnType<typeof setup>;

function loadAll(hook: Hook, n: number) {
  act(() => {
    for (let i = 0; i < n; i++) hook.result.current.markLoaded(i);
  });
}

function tick(ms: number) {
  act(() => {
    vi.advanceTimersByTime(ms);
  });
}

let visibility: DocumentVisibilityState = 'visible';
function setVisibility(state: DocumentVisibilityState) {
  visibility = state;
  act(() => {
    document.dispatchEvent(new Event('visibilitychange'));
  });
}

beforeEach(() => {
  vi.useFakeTimers();
  visibility = 'visible';
  Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => visibility });
});

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
});

describe('shuffledOrder', () => {
  it('returns a permutation, reproducible with an injected random source', () => {
    const order = shuffledOrder(11, seeded(7));
    expect([...order].sort((a, b) => a - b)).toEqual(Array.from({ length: 11 }, (_, i) => i));
    expect(shuffledOrder(11, seeded(7))).toEqual(order);
    expect(shuffledOrder(0)).toEqual([]);
    expect(shuffledOrder(1)).toEqual([0]);
  });

  it('copes with a random source returning the bounds', () => {
    expect([...shuffledOrder(5, () => 1)].sort()).toEqual([0, 1, 2, 3, 4]);
    expect([...shuffledOrder(5, () => 0)].sort()).toEqual([0, 1, 2, 3, 4]);
  });
});

describe('useSlideshow: order (BG-5)', () => {
  it.each([2, 3, 11])('never shows the same photo twice in a row over 3 cycles of %i photos', (n) => {
    for (const seed of [1, 42, 2024]) {
      const hook = setup(n, { random: seeded(seed) });
      loadAll(hook, n);
      const seen = [hook.result.current.current];
      for (let i = 0; i < 3 * n; i++) {
        tick(slideDurationMs);
        seen.push(hook.result.current.current);
      }
      for (let i = 1; i < seen.length; i++) expect(seen[i]).not.toBe(seen[i - 1]);
      // Every cycle shows every photo once.
      for (let cycle = 0; cycle < 3; cycle++) {
        const chunk = seen.slice(cycle * n, cycle * n + n);
        expect(new Set(chunk).size).toBe(n);
      }
      hook.unmount();
    }
  });

  it('shuffles with the injected random source', () => {
    const hook = setup(11, { random: seeded(3) });
    expect(hook.result.current.order).toEqual(shuffledOrder(11, seeded(3)));
  });

  it('stays on a single photo without error and without timers', () => {
    const hook = setup(1);
    loadAll(hook, 1);
    expect(hook.result.current.current).toBe(0);
    expect(hook.result.current.next).toBeNull();
    tick(slideDurationMs * 5);
    expect(hook.result.current.current).toBe(0);
    expect(vi.getTimerCount()).toBe(0);
  });

  it('has nothing to show for an empty list', () => {
    const hook = setup(0);
    expect(hook.result.current).toMatchObject({ current: null, next: null, failedAll: false, order: [] });
  });
});

describe('useSlideshow: timing (BG-3, §6.3)', () => {
  it('advances after the slide duration and not before', () => {
    const hook = setup(3);
    loadAll(hook, 3);
    const first = hook.result.current.current;
    tick(slideDurationMs - 1);
    expect(hook.result.current.current).toBe(first);
    tick(1);
    expect(hook.result.current.current).not.toBe(first);
  });

  it('uses the 15 s interval under reduced motion', () => {
    const hook = setup(3, { reducedMotion: true });
    loadAll(hook, 3);
    const first = hook.result.current.current;
    tick(reducedMotionIntervalMs - 1);
    expect(hook.result.current.current).toBe(first);
    tick(1);
    expect(hook.result.current.current).not.toBe(first);
  });

  it('never advances while paused, and resumes with the remaining time', () => {
    const hook = setup(3);
    loadAll(hook, 3);
    const first = hook.result.current.current;
    tick(3000);
    hook.rerender({ random: seeded(42), paused: true });
    tick(slideDurationMs * 10);
    expect(hook.result.current.current).toBe(first);
    expect(vi.getTimerCount()).toBe(0);
    hook.rerender({ random: seeded(42), paused: false });
    tick(slideDurationMs - 3000 - 1);
    expect(hook.result.current.current).toBe(first);
    tick(1);
    expect(hook.result.current.current).not.toBe(first);
  });

  it('does not start the lifetime of a slide before its image has loaded', () => {
    const hook = setup(3);
    const first = hook.result.current.current;
    tick(slideDurationMs * 3);
    expect(hook.result.current.current).toBe(first);
    expect(hook.result.current.currentLoaded).toBe(false);
  });

  it('changes progressKey on every advance', () => {
    const hook = setup(3);
    loadAll(hook, 3);
    const key = hook.result.current.progressKey;
    tick(slideDurationMs);
    expect(hook.result.current.progressKey).not.toBe(key);
  });
});

describe('useSlideshow: just-in-time preload (BG-8)', () => {
  it('requests only the first and the next photo at mount', () => {
    const hook = setup(11);
    const { order, current, next } = hook.result.current;
    expect(current).toBe(order[0]);
    expect(next).toBe(order[1]);
  });

  it('keeps the current slide while the next one is still loading, then advances on load', () => {
    const hook = setup(3);
    const { order } = hook.result.current;
    act(() => hook.result.current.markLoaded(order[0]));
    // The next photo is requested during the current slide.
    expect(hook.result.current.next).toBe(order[1]);
    tick(slideDurationMs);
    expect(hook.result.current.current).toBe(order[0]);
    tick(slideDurationMs * 2);
    expect(hook.result.current.current).toBe(order[0]);
    act(() => hook.result.current.markLoaded(order[1]));
    expect(hook.result.current.current).toBe(order[1]);
    // Only now the photo after it is requested.
    expect(hook.result.current.next).toBe(order[2]);
  });

  it('does not advance early when the next photo loads before the deadline', () => {
    const hook = setup(3);
    const { order } = hook.result.current;
    act(() => hook.result.current.markLoaded(order[0]));
    tick(1000);
    act(() => hook.result.current.markLoaded(order[1]));
    expect(hook.result.current.current).toBe(order[0]);
    tick(slideDurationMs - 1000);
    expect(hook.result.current.current).toBe(order[1]);
  });
});

describe('useSlideshow: tab visibility (BG-9)', () => {
  it('stops advancing while the tab is hidden and resumes when visible', () => {
    const hook = setup(3);
    loadAll(hook, 3);
    const first = hook.result.current.current;
    tick(5000);
    setVisibility('hidden');
    tick(slideDurationMs * 5);
    expect(hook.result.current.current).toBe(first);
    setVisibility('visible');
    tick(slideDurationMs - 5000);
    expect(hook.result.current.current).not.toBe(first);
  });

  it('does not advance on a load event while hidden, but right after becoming visible', () => {
    const hook = setup(3);
    const { order } = hook.result.current;
    act(() => hook.result.current.markLoaded(order[0]));
    tick(slideDurationMs);
    setVisibility('hidden');
    act(() => hook.result.current.markLoaded(order[1]));
    expect(hook.result.current.current).toBe(order[0]);
    setVisibility('visible');
    tick(0);
    expect(hook.result.current.current).toBe(order[1]);
  });
});

describe('useSlideshow: failures (BG-10)', () => {
  it('skips a photo that fails to load', () => {
    const hook = setup(3);
    const { order } = hook.result.current;
    act(() => {
      hook.result.current.markLoaded(order[0]);
      hook.result.current.markFailed(order[1]);
    });
    expect(hook.result.current.next).toBe(order[2]);
    expect(hook.result.current.order).toEqual([order[0], order[2]]);
    act(() => hook.result.current.markLoaded(order[2]));
    tick(slideDurationMs);
    expect(hook.result.current.current).toBe(order[2]);
    tick(slideDurationMs);
    expect(hook.result.current.current).toBe(order[0]);
  });

  it('replaces a first photo that fails before it was shown', () => {
    const hook = setup(3);
    const { order } = hook.result.current;
    act(() => hook.result.current.markFailed(order[0]));
    expect(hook.result.current.current).toBe(order[1]);
    expect(hook.result.current.next).toBe(order[2]);
    expect(hook.result.current.failedAll).toBe(false);
  });

  it('reports failedAll when every photo fails', () => {
    const hook = setup(3);
    act(() => {
      for (let i = 0; i < 3; i++) hook.result.current.markFailed(i);
    });
    expect(hook.result.current).toMatchObject({ current: null, next: null, failedAll: true, order: [] });
    expect(vi.getTimerCount()).toBe(0);
  });
});

describe('useSlideshow: goTo (CAP-4)', () => {
  it('jumps to a loaded photo at once and restarts the lifetime of the current one', () => {
    const hook = setup(4);
    loadAll(hook, 4);
    const { order } = hook.result.current;
    act(() => hook.result.current.goTo(order[3]));
    expect(hook.result.current.current).toBe(order[3]);
    tick(slideDurationMs - 1);
    expect(hook.result.current.current).toBe(order[3]);
    const key = hook.result.current.progressKey;
    act(() => hook.result.current.goTo(order[3]));
    expect(hook.result.current.progressKey).not.toBe(key);
    tick(slideDurationMs - 1);
    expect(hook.result.current.current).toBe(order[3]);
  });

  it('preloads the target first when it is not loaded yet', () => {
    const hook = setup(4);
    const { order } = hook.result.current;
    act(() => hook.result.current.markLoaded(order[0]));
    act(() => hook.result.current.goTo(order[2]));
    expect(hook.result.current.current).toBe(order[0]);
    expect(hook.result.current.next).toBe(order[2]);
    act(() => hook.result.current.markLoaded(order[2]));
    expect(hook.result.current.current).toBe(order[2]);
  });

  it('ignores unknown or failed photos', () => {
    const hook = setup(3);
    loadAll(hook, 3);
    const { order, current } = hook.result.current;
    act(() => {
      hook.result.current.markFailed(order[2]);
      hook.result.current.goTo(order[2]);
      hook.result.current.goTo(99);
    });
    expect(hook.result.current.current).toBe(current);
  });
});

describe('useSlideshow: cleanup (§6.2, §9.13)', () => {
  it('leaves no timers and no visibilitychange listener after unmount', () => {
    const add = vi.spyOn(document, 'addEventListener');
    const remove = vi.spyOn(document, 'removeEventListener');
    const hook = setup(3);
    loadAll(hook, 3);
    expect(vi.getTimerCount()).toBe(1);
    const registered = add.mock.calls.filter(([type]) => type === 'visibilitychange').map(([, l]) => l);
    expect(registered.length).toBeGreaterThan(0);
    hook.unmount();
    expect(vi.getTimerCount()).toBe(0);
    const removed = remove.mock.calls.filter(([type]) => type === 'visibilitychange').map(([, l]) => l);
    for (const listener of registered) expect(removed).toContain(listener);
  });
});
