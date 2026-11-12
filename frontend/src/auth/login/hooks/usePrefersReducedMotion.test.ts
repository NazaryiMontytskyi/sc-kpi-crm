import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { REDUCED_MOTION_QUERY, usePrefersReducedMotion } from './usePrefersReducedMotion';

/** A controllable MediaQueryList for `prefers-reduced-motion`. */
function stubMatchMedia(initial: boolean) {
  const listeners = new Set<() => void>();
  const query = {
    matches: initial,
    media: REDUCED_MOTION_QUERY,
    addEventListener: vi.fn((_: string, l: () => void) => listeners.add(l)),
    removeEventListener: vi.fn((_: string, l: () => void) => listeners.delete(l)),
  };
  const original = window.matchMedia;
  window.matchMedia = vi.fn(() => query as unknown as MediaQueryList);
  return {
    query,
    listeners,
    set(matches: boolean) {
      query.matches = matches;
      listeners.forEach((l) => l());
    },
    restore: () => (window.matchMedia = original),
  };
}

describe('usePrefersReducedMotion', () => {
  let media: ReturnType<typeof stubMatchMedia> | undefined;
  afterEach(() => media?.restore());

  it('reads the initial value', () => {
    media = stubMatchMedia(true);
    expect(renderHook(() => usePrefersReducedMotion()).result.current).toBe(true);
    media.restore();
    media = stubMatchMedia(false);
    expect(renderHook(() => usePrefersReducedMotion()).result.current).toBe(false);
    expect(window.matchMedia).toHaveBeenCalledWith(REDUCED_MOTION_QUERY);
  });

  it('reacts to a change event', () => {
    media = stubMatchMedia(false);
    const { result } = renderHook(() => usePrefersReducedMotion());
    act(() => media!.set(true));
    expect(result.current).toBe(true);
    act(() => media!.set(false));
    expect(result.current).toBe(false);
  });

  it('removes its change listener on unmount', () => {
    media = stubMatchMedia(false);
    const { unmount } = renderHook(() => usePrefersReducedMotion());
    expect(media.listeners.size).toBe(1);
    const [listener] = media.listeners;
    unmount();
    expect(media.query.removeEventListener).toHaveBeenCalledWith('change', listener);
    expect(media.listeners.size).toBe(0);
  });

  it('assumes motion is allowed where matchMedia is missing', () => {
    const original = window.matchMedia;
    // @ts-expect-error simulating an environment without matchMedia
    window.matchMedia = undefined;
    try {
      expect(renderHook(() => usePrefersReducedMotion()).result.current).toBe(false);
    } finally {
      window.matchMedia = original;
    }
  });
});
