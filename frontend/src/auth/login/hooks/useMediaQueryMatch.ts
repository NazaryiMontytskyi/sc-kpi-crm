import { useCallback, useSyncExternalStore } from 'react';

function mediaQuery(query: string): MediaQueryList | undefined {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return undefined;
  return window.matchMedia(query) ?? undefined;
}

/**
 * Whether a CSS media query matches, read synchronously on the first render (no flash of the wrong variant) and
 * updated on `change`. Where matchMedia is missing (old browsers, SSR) it reports `false`.
 */
export function useMediaQueryMatch(query: string): boolean {
  const subscribe = useCallback(
    (onChange: () => void) => {
      const list = mediaQuery(query);
      if (!list || typeof list.addEventListener !== 'function') return () => {};
      list.addEventListener('change', onChange);
      return () => list.removeEventListener('change', onChange);
    },
    [query],
  );
  const getSnapshot = useCallback(() => mediaQuery(query)?.matches === true, [query]);
  return useSyncExternalStore(subscribe, getSnapshot, () => false);
}
