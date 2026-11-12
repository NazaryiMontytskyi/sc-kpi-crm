import { useCallback, useSyncExternalStore } from 'react';
import { loginPageConfig } from '../loginPage.config';
import { usePrefersReducedMotion } from './usePrefersReducedMotion';

/**
 * The user's pause of the sign-in page motion (MAIN_PAGE_FEATURE.md FT-3, WCAG 2.2.2), persisted in localStorage.
 *
 * One small external store shared by every consumer (the card in the entry chunk, the lazily loaded background, the
 * toggle), so a toggle updates them all at once. Every storage access is wrapped in try/catch: when storage is
 * unavailable (private mode, blocked cookies, quota) the choice lives in memory for this page load only.
 */

const STORED_PAUSED = 'true';
const STORED_RUNNING = 'false';

const listeners = new Set<() => void>();
/** Set only when writing to storage failed; then it wins over storage for the rest of the page load. */
let memory: boolean | undefined;

function storage(): Storage | undefined {
  return typeof window === 'undefined' ? undefined : window.localStorage;
}

function readPaused(): boolean {
  if (memory !== undefined) return memory;
  try {
    return storage()?.getItem(loginPageConfig.motion.pauseStorageKey) === STORED_PAUSED;
  } catch {
    return false;
  }
}

function writePaused(paused: boolean) {
  try {
    const store = storage();
    if (!store) throw new Error('localStorage unavailable');
    store.setItem(loginPageConfig.motion.pauseStorageKey, paused ? STORED_PAUSED : STORED_RUNNING);
  } catch {
    memory = paused;
  }
  listeners.forEach((listener) => listener());
}

function onStorage(event: StorageEvent) {
  // Another tab changed the choice.
  if (event.key === null || event.key === loginPageConfig.motion.pauseStorageKey) {
    listeners.forEach((listener) => listener());
  }
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  if (listeners.size === 1 && typeof window !== 'undefined') window.addEventListener('storage', onStorage);
  return () => {
    listeners.delete(listener);
    if (listeners.size === 0 && typeof window !== 'undefined') window.removeEventListener('storage', onStorage);
  };
}

/** Tests only: forgets the in-memory fallback used when storage failed. */
export function resetMotionPauseMemory() {
  memory = undefined;
}

export interface MotionPreference {
  /** `prefers-reduced-motion: reduce` of the operating system. */
  prefersReducedMotion: boolean;
  /** The user pressed pause (FT-3). */
  paused: boolean;
  /** Either of the two: no Ken Burns, shake, staggers, parallax, particles or intro (spec §6.3). */
  motionReduced: boolean;
  setPaused: (paused: boolean) => void;
  togglePaused: () => void;
}

/** Combined motion preference of the sign-in page (spec §6.3), reactive to both sources. */
export function useMotionPreference(): MotionPreference {
  const prefersReducedMotion = usePrefersReducedMotion();
  const paused = useSyncExternalStore(subscribe, readPaused, () => false);
  const setPaused = useCallback((value: boolean) => writePaused(value), []);
  const togglePaused = useCallback(() => writePaused(!readPaused()), []);
  return { prefersReducedMotion, paused, motionReduced: prefersReducedMotion || paused, setPaused, togglePaused };
}
