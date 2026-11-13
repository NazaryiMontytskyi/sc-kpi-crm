import { vi } from 'vitest';

/**
 * Test helper: a matchMedia where exactly the given queries match (by exact string), everything else does not.
 * Change listeners are recorded so `set` can flip a query at runtime. Returns a handle with `restore`.
 */
type Listener = (event: { matches: boolean; media: string }) => void;

export function stubMediaQueries(matching: Iterable<string>) {
  const active = new Set(matching);
  const listeners = new Map<string, Set<Listener>>();
  const original = window.matchMedia;

  window.matchMedia = vi.fn((query: string) => {
    const own = listeners.get(query) ?? new Set<Listener>();
    listeners.set(query, own);
    return {
      get matches() {
        return active.has(query);
      },
      media: query,
      onchange: null,
      addEventListener: (_: string, l: Listener) => own.add(l),
      removeEventListener: (_: string, l: Listener) => own.delete(l),
      addListener: (l: Listener) => own.add(l),
      removeListener: (l: Listener) => own.delete(l),
      dispatchEvent: () => false,
    } as unknown as MediaQueryList;
  });

  return {
    set(query: string, matches: boolean) {
      if (matches) active.add(query);
      else active.delete(query);
      listeners.get(query)?.forEach((l) => l({ matches, media: query }));
    },
    restore: () => (window.matchMedia = original),
  };
}
