/**
 * Single tunable module of the sign-in page (MAIN_PAGE_FEATURE.md §6.2). Every timing, intensity and count
 * used by the page lives here so the feel can be adjusted without hunting through components.
 * Durations are in milliseconds, distances in CSS pixels.
 */

/** Ken Burns pan direction: the photo drifts by this fraction of its overflow on each axis (-1..1). */
export interface KenBurnsDirection {
  readonly x: -1 | 0 | 1;
  readonly y: -1 | 0 | 1;
}

export const loginPageConfig = {
  slideshow: {
    /** BG-3: how long a slide stays visible. */
    slideDurationMs: 8000,
    /** BG-3: crossfade between two slides; must be shorter than the slide duration. */
    crossfadeMs: 1500,
    /** §6.3: interval of the plain slow crossfade under reduced motion. */
    reducedMotionIntervalMs: 15000,
  },
  overlay: {
    /**
     * BG-6: strength (0..1) of the dark layer over the photos, in both colour schemes. The gradient runs from
     * `topOpacity` to `bottomOpacity`; the vignette darkens the corners up to `vignetteOpacity`.
     */
    topOpacity: 0.45,
    bottomOpacity: 0.7,
    vignetteOpacity: 0.55,
    /** BG-7: opacity of the static film grain over the overlay. */
    grainOpacity: 0.08,
  },
  aurora: {
    /** BG-10: base drift period of the fallback blobs (the other blobs use 1.2x and 1.4x of it). */
    driftMs: 28000,
  },
  kenBurns: {
    /** BG-4: zoom range of the visible photo. */
    scaleFrom: 1.0,
    scaleTo: 1.08,
    /** BG-4: pan presets; consecutive slides never reuse the same one. */
    directions: [
      { x: -1, y: -1 },
      { x: 1, y: -1 },
      { x: -1, y: 1 },
      { x: 1, y: 1 },
      { x: 1, y: 0 },
      { x: -1, y: 0 },
    ] as readonly KenBurnsDirection[],
  },
  tagline: {
    /** HERO-4: interval between tagline phrases. */
    intervalMs: 4000,
  },
  headline: {
    /** HERO-5: delay between words of the staggered headline reveal. */
    staggerStepMs: 60,
  },
  card: {
    /** CARD-2: entrance rise distance and duration, and the stagger between fields. */
    entranceRisePx: 24,
    entranceMs: 600,
    fieldStaggerMs: 80,
    /** CARD-7: horizontal shake on a failed sign-in (at most 400 ms). */
    shakeMs: 400,
  },
  intro: {
    /** TR-1: logo hold on the dark screen, then the reveal; total at most 1600 ms. */
    logoHoldMs: 800,
    revealMs: 700,
    /** TR-2: sessionStorage flag set once the intro has played. */
    sessionStorageKey: 'sc-kpi.login.introPlayed',
  },
  success: {
    /** TR-3: card fade + background zoom/blur + full-screen fade before navigating. */
    transitionMs: 900,
  },
  parallax: {
    /** AMB-1: maximum offset of the background (opposite to the cursor) and of the hero (with it). */
    backgroundMaxPx: 12,
    heroMaxPx: 6,
    /** Smoothing factor of the rAF lerp (0..1, higher follows the cursor faster). */
    smoothing: 0.08,
  },
  particles: {
    /** AMB-2: particle count range on desktop; halved on phones. */
    minCount: 15,
    maxCount: 30,
    phoneFactor: 0.5,
  },
  indicator: {
    /** CAP-3: above this number of photos a single continuous progress line replaces the segments. */
    maxSegments: 10,
  },
  motion: {
    /** FT-3: localStorage key of the user's pause choice. */
    pauseStorageKey: 'sc-kpi.login.motionPaused',
  },
  seasonal: {
    /** SEA-2: feature flag for seasonal accents (snowfall Dec-Feb). */
    enabled: true,
  },
} as const;

export type LoginPageConfig = typeof loginPageConfig;
