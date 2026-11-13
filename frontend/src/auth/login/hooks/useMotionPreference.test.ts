import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { loginPageConfig } from '../loginPage.config';
import { resetMotionPauseMemory, useMotionPreference } from './useMotionPreference';
import { REDUCED_MOTION_QUERY } from './usePrefersReducedMotion';

const KEY = loginPageConfig.motion.pauseStorageKey;

function stubReducedMotion(reduce: boolean) {
  const original = window.matchMedia;
  window.matchMedia = vi.fn((query: string) => ({
    matches: query === REDUCED_MOTION_QUERY && reduce,
    media: query,
    addEventListener: () => {},
    removeEventListener: () => {},
  })) as unknown as typeof window.matchMedia;
  return () => (window.matchMedia = original);
}

describe('useMotionPreference (FT-3, spec §6.3, §9.9)', () => {
  beforeEach(() => {
    localStorage.removeItem(KEY);
    resetMotionPauseMemory();
  });
  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.removeItem(KEY);
    resetMotionPauseMemory();
  });

  it('is not paused by default', () => {
    const { result } = renderHook(() => useMotionPreference());
    expect(result.current.paused).toBe(false);
    expect(result.current.motionReduced).toBe(false);
  });

  it('reads the pause from localStorage on mount', () => {
    localStorage.setItem(KEY, 'true');
    const { result } = renderHook(() => useMotionPreference());
    expect(result.current.paused).toBe(true);
    expect(result.current.motionReduced).toBe(true);
  });

  it('writes the choice on toggle and keeps it across a remount', () => {
    const first = renderHook(() => useMotionPreference());
    act(() => first.result.current.togglePaused());
    expect(first.result.current.paused).toBe(true);
    expect(localStorage.getItem(KEY)).toBe('true');
    first.unmount();

    const second = renderHook(() => useMotionPreference());
    expect(second.result.current.paused).toBe(true);
    act(() => second.result.current.togglePaused());
    expect(second.result.current.paused).toBe(false);
    expect(localStorage.getItem(KEY)).toBe('false');
  });

  it('updates every consumer at once', () => {
    const a = renderHook(() => useMotionPreference());
    const b = renderHook(() => useMotionPreference());
    act(() => a.result.current.setPaused(true));
    expect(b.result.current.paused).toBe(true);
  });

  it('follows a change made in another tab', () => {
    const { result } = renderHook(() => useMotionPreference());
    act(() => {
      localStorage.setItem(KEY, 'true');
      window.dispatchEvent(new StorageEvent('storage', { key: KEY }));
    });
    expect(result.current.paused).toBe(true);
  });

  it('does not crash and is not paused when reading the storage throws', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('SecurityError');
    });
    const { result } = renderHook(() => useMotionPreference());
    expect(result.current.paused).toBe(false);
  });

  it('still toggles for this page load when writing the storage throws', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('SecurityError');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('QuotaExceededError');
    });
    const { result } = renderHook(() => useMotionPreference());
    expect(() => act(() => result.current.togglePaused())).not.toThrow();
    expect(result.current.paused).toBe(true);
    act(() => result.current.togglePaused());
    expect(result.current.paused).toBe(false);
  });

  it('combines the pause with prefers-reduced-motion', () => {
    const restore = stubReducedMotion(true);
    try {
      const { result } = renderHook(() => useMotionPreference());
      expect(result.current.prefersReducedMotion).toBe(true);
      expect(result.current.paused).toBe(false);
      expect(result.current.motionReduced).toBe(true);
    } finally {
      restore();
    }
  });

  it('removes its storage listener once the last consumer unmounts', () => {
    const add = vi.spyOn(window, 'addEventListener');
    const remove = vi.spyOn(window, 'removeEventListener');
    const a = renderHook(() => useMotionPreference());
    const b = renderHook(() => useMotionPreference());
    a.unmount();
    b.unmount();
    const added = add.mock.calls.filter(([type]) => type === 'storage').length;
    const removed = remove.mock.calls.filter(([type]) => type === 'storage').length;
    expect(added).toBeGreaterThan(0);
    expect(removed).toBe(added);
  });
});
