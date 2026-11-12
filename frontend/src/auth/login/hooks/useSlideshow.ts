import { useCallback, useEffect, useReducer, useRef, useSyncExternalStore } from 'react';
import { loginPageConfig } from '../loginPage.config';

/**
 * Slideshow engine of the sign-in background (MAIN_PAGE_FEATURE.md BG-3, BG-5, BG-8, BG-9, §6.3).
 *
 * It does not render or fetch anything itself: the renderer shows `current`, keeps `next` mounted invisibly (that
 * is the just-in-time preload, the browser picks the right `srcset` candidate) and reports image events back
 * through `markLoaded` / `markFailed`. Slide numbers are indices into the `slides` array given at mount.
 */

export interface UseSlideshowOptions {
  /** Stops advancing on the current photo (user pause, INC-060). */
  paused?: boolean;
  /** Reduced motion: slow plain crossfade at `reducedMotionIntervalMs`. */
  reducedMotion?: boolean;
  slideDurationMs?: number;
  reducedMotionIntervalMs?: number;
  /** Random source of the shuffle, injectable for tests; defaults to Math.random. */
  random?: () => number;
}

export interface Slideshow {
  /** Rotation in display order (shuffled once per mount), without failed images. */
  order: readonly number[];
  /** Slide on screen (or about to be, while its image loads); null when there is nothing to show. */
  current: number | null;
  /** True once the image of `current` has loaded; until then the fallback stays visible. */
  currentLoaded: boolean;
  /** Slide to preload now; null with fewer than two usable slides. */
  next: number | null;
  /** Changes whenever a slide starts its lifetime (advance or restart), e.g. to restart progress animations. */
  progressKey: number;
  /** True when there were slides but every image failed to load. */
  failedAll: boolean;
  /** The lifetime of the visible slide is counting: not paused, tab visible and the image loaded (CAP-3 fill). */
  running: boolean;
  /** Lifetime of a slide in the current mode (normal or reduced motion). */
  intervalMs: number;
  /** Jumps to a slide as soon as its image is loaded (CAP-4); the current slide restarts its lifetime. */
  goTo: (slide: number) => void;
  markLoaded: (slide: number) => void;
  markFailed: (slide: number) => void;
}

type ImageStatus = 'loaded' | 'failed';

interface State {
  order: readonly number[];
  status: Readonly<Record<number, ImageStatus>>;
  current: number | null;
  /** Explicit target of goTo, shown instead of the natural successor. */
  target: number | null;
  /** The lifetime of the current slide is over; advance as soon as `next` has loaded. */
  due: boolean;
  progressKey: number;
}

type Action =
  | { type: 'deadline' }
  | { type: 'loaded'; slide: number; running: boolean }
  | { type: 'failed'; slide: number }
  | { type: 'goTo'; slide: number };

/** Fisher-Yates shuffle of 0..count-1 with an injectable random source in [0, 1). */
export function shuffledOrder(count: number, random: () => number = Math.random): number[] {
  const order = Array.from({ length: count }, (_, i) => i);
  for (let i = count - 1; i > 0; i--) {
    const r = Math.min(Math.max(random(), 0), 0.999999999);
    const j = Math.floor(r * (i + 1));
    [order[i], order[j]] = [order[j], order[i]];
  }
  return order;
}

/**
 * The slide after `from` in the cyclic order, skipping failed images and `from` itself. Because the order is a
 * permutation, the same photo can never follow itself, also when the cycle restarts (BG-5).
 */
function successor(state: Pick<State, 'order' | 'status'>, from: number | null): number | null {
  const { order, status } = state;
  const start = from === null ? -1 : order.indexOf(from);
  for (let step = 1; step <= order.length; step++) {
    const candidate = order[(start + step) % order.length];
    if (candidate !== from && status[candidate] !== 'failed') return candidate;
  }
  return null;
}

function nextOf(state: State): number | null {
  if (state.target !== null && state.target !== state.current) return state.target;
  return successor(state, state.current);
}

function advance(state: State): State {
  const next = nextOf(state);
  if (next === null) return { ...state, due: false, target: null };
  return { ...state, current: next, target: null, due: false, progressKey: state.progressKey + 1 };
}

function reducer(state: State, action: Action): State {
  switch (action.type) {
    case 'deadline': {
      const next = nextOf(state);
      if (next === null) return state.due ? { ...state, due: false } : state;
      // BG-8: a slide is never shown before its image is ready; otherwise the current one stays longer.
      return state.status[next] === 'loaded' ? advance(state) : { ...state, due: true };
    }
    case 'loaded': {
      if (state.status[action.slide] === 'loaded') return state;
      const loaded = { ...state, status: { ...state.status, [action.slide]: 'loaded' as const } };
      const isNext = nextOf(loaded) === action.slide;
      const explicit = loaded.target === action.slide;
      return loaded.due && isNext && (action.running || explicit) ? advance(loaded) : loaded;
    }
    case 'failed': {
      if (state.status[action.slide] === 'failed') return state;
      const failed = { ...state, status: { ...state.status, [action.slide]: 'failed' as const } };
      if (failed.target === action.slide) failed.target = null;
      if (failed.current === action.slide) {
        const replacement = successor(failed, action.slide);
        return { ...failed, current: replacement, target: null, due: false, progressKey: state.progressKey + 1 };
      }
      return failed;
    }
    case 'goTo': {
      const { slide } = action;
      if (!state.order.includes(slide) || state.status[slide] === 'failed') return state;
      if (slide === state.current) return { ...state, target: null, due: false, progressKey: state.progressKey + 1 };
      const targeted = { ...state, target: slide, due: true };
      return state.status[slide] === 'loaded' ? advance(targeted) : targeted;
    }
  }
}

function init({ count, random }: { count: number; random?: () => number }): State {
  const order = shuffledOrder(count, random);
  return { order, status: {}, current: order[0] ?? null, target: null, due: false, progressKey: 0 };
}

function subscribeVisibility(onChange: () => void): () => void {
  if (typeof document === 'undefined') return () => {};
  document.addEventListener('visibilitychange', onChange);
  return () => document.removeEventListener('visibilitychange', onChange);
}

function isHidden(): boolean {
  return typeof document !== 'undefined' && document.visibilityState === 'hidden';
}

/**
 * Drives the background slideshow. `slides` is read once per mount (remount with a new `key` to change it).
 * Every timer and listener is released on unmount (spec §6.2, §9.13).
 */
export function useSlideshow(slides: readonly unknown[], options: UseSlideshowOptions = {}): Slideshow {
  const {
    paused = false,
    reducedMotion = false,
    slideDurationMs = loginPageConfig.slideshow.slideDurationMs,
    reducedMotionIntervalMs = loginPageConfig.slideshow.reducedMotionIntervalMs,
    random,
  } = options;
  const [state, dispatch] = useReducer(reducer, { count: slides.length, random }, init);

  // BG-9: Page Visibility API.
  const hidden = useSyncExternalStore(subscribeVisibility, isHidden, () => false);
  const running = !paused && !hidden;
  const runningRef = useRef(running);
  useEffect(() => {
    runningRef.current = running;
  }, [running]);

  const { current, progressKey } = state;
  const currentLoaded = current !== null && state.status[current] === 'loaded';
  const order = state.order.filter((slide) => state.status[slide] !== 'failed');
  const next = current === null ? null : nextOf(state);
  const interval = reducedMotion ? reducedMotionIntervalMs : slideDurationMs;

  // Time the current slide has already been visible, kept across pauses, hidden tabs and interval changes.
  const elapsed = useRef({ key: progressKey, ms: 0 });

  useEffect(() => {
    if (!running || !currentLoaded || next === null) return;
    if (elapsed.current.key !== progressKey) elapsed.current = { key: progressKey, ms: 0 };
    const startedAt = Date.now();
    const timer = setTimeout(
      () => dispatch({ type: 'deadline' }),
      Math.max(0, interval - elapsed.current.ms),
    );
    return () => {
      clearTimeout(timer);
      if (elapsed.current.key === progressKey) elapsed.current.ms += Date.now() - startedAt;
    };
  }, [running, currentLoaded, next, interval, progressKey]);

  const goTo = useCallback((slide: number) => dispatch({ type: 'goTo', slide }), []);
  const markLoaded = useCallback(
    (slide: number) => dispatch({ type: 'loaded', slide, running: runningRef.current }),
    [],
  );
  const markFailed = useCallback((slide: number) => dispatch({ type: 'failed', slide }), []);

  return {
    order,
    current,
    currentLoaded,
    next,
    progressKey,
    failedAll: slides.length > 0 && current === null,
    running: running && currentLoaded,
    intervalMs: interval,
    goTo,
    markLoaded,
    markFailed,
  };
}
