import { vi } from 'vitest';

/**
 * Test helper: a matchMedia that evaluates `(min-width: Nem|Npx)` / `(max-width: ...)` against a fake viewport
 * width (16 px per em) and reports `false` for every other query. `resize` fires `change` on the live lists.
 */
type Listener = (event: { matches: boolean; media: string }) => void;

export function stubViewport(initialWidthPx: number) {
  let width = initialWidthPx;
  const lists = new Set<{
    query: string;
    listeners: Set<Listener>;
    matches: boolean;
  }>();

  function evaluate(query: string): boolean {
    const m = /\((min|max)-width:\s*([\d.]+)(em|px)\)/.exec(query);
    if (!m) return false;
    const px = Number(m[2]) * (m[3] === 'em' ? 16 : 1);
    return m[1] === 'min' ? width >= px : width <= px;
  }

  const original = window.matchMedia;
  window.matchMedia = vi.fn((query: string) => {
    const entry = { query, listeners: new Set<Listener>(), matches: evaluate(query) };
    lists.add(entry);
    return {
      get matches() {
        return evaluate(query);
      },
      media: query,
      onchange: null,
      addEventListener: (_: string, l: Listener) => entry.listeners.add(l),
      removeEventListener: (_: string, l: Listener) => entry.listeners.delete(l),
      addListener: (l: Listener) => entry.listeners.add(l),
      removeListener: (l: Listener) => entry.listeners.delete(l),
      dispatchEvent: () => false,
    } as unknown as MediaQueryList;
  });

  return {
    resize(px: number) {
      width = px;
      lists.forEach((entry) =>
        entry.listeners.forEach((l) => l({ matches: evaluate(entry.query), media: entry.query })),
      );
    },
    listenerCount: () => [...lists].reduce((n, entry) => n + entry.listeners.size, 0),
    restore: () => (window.matchMedia = original),
  };
}
