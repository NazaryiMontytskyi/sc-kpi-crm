import { useSyncExternalStore } from 'react';

export const REDUCED_MOTION_QUERY = '(prefers-reduced-motion: reduce)';

function mediaQuery(): MediaQueryList | undefined {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return undefined;
  return window.matchMedia(REDUCED_MOTION_QUERY) ?? undefined;
}

function subscribe(onChange: () => void): () => void {
  const query = mediaQuery();
  if (!query || typeof query.addEventListener !== 'function') return () => {};
  query.addEventListener('change', onChange);
  return () => query.removeEventListener('change', onChange);
}

function getSnapshot(): boolean {
  return mediaQuery()?.matches === true;
}

function getServerSnapshot(): boolean {
  return false;
}

/**
 * `prefers-reduced-motion: reduce` of the operating system (MAIN_PAGE_FEATURE.md §6.3), reactive to changes.
 * Safe where matchMedia is missing (jsdom, old browsers): then motion is assumed to be allowed.
 */
export function usePrefersReducedMotion(): boolean {
  return useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);
}
