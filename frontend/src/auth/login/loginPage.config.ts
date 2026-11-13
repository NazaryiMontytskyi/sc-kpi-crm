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
  layout: {
    /**
     * HERO-1: from this viewport width (64em = 1024 px) the hero and the card sit side by side and the hero shows
     * the full white logo; below it the hero collapses to the white mark and the title above the card. The CSS
     * modules use the same `64em` literal (asserted by loginStyles.test.ts).
     */
    desktopMinWidthEm: 64,
  },
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
    /**
     * BG-4: pan distance at the end of the zoom, in percent of the photo size per axis. Must stay below half of the
     * zoom overflow ((scaleTo - 1) / 2 = 4 %), so the photo edges never show.
     */
    panPercent: 3,
  },
  tagline: {
    /** HERO-4: interval between tagline phrases. */
    intervalMs: 4000,
    /** HERO-4: outgoing slide-up/fade and incoming slide-up of one word, and the delay between words. */
    transitionMs: 500,
    wordStaggerMs: 45,
    /** HERO-4: vertical travel of the words. */
    risePx: 14,
    /** §6.3: plain cross-fade of the phrases under reduced motion. */
    fadeMs: 600,
  },
  headline: {
    /** HERO-5: delay between words of the staggered headline reveal. */
    staggerStepMs: 60,
    /** HERO-5: fade + rise of one word, and how far it rises. */
    wordMs: 600,
    risePx: 12,
  },
  caption: {
    /** CAP-1: fade + slide-in of the caption of each new slide (fade only when motion is reduced or paused). */
    enterMs: 700,
    enterRisePx: 8,
  },
  card: {
    /** CARD-2: entrance rise distance and duration, and the stagger between fields. */
    entranceRisePx: 24,
    entranceMs: 600,
    fieldStaggerMs: 80,
    /** CARD-7: horizontal shake on a failed sign-in (at most 400 ms). */
    shakeMs: 400,
    /** CARD-7: horizontal amplitude of the shake. */
    shakeDistancePx: 8,
    /** CARD-7: fade-in of the error message. */
    errorFadeMs: 250,
    /** CARD-3: label moving above the field on focus / when filled. */
    labelFloatMs: 160,
    /** CARD-4: accent glow and underline of the focused field. */
    focusGlowMs: 220,
    /** CARD-6: hover lift of the submit button and the light sweep across it. */
    buttonLiftPx: 2,
    buttonLiftMs: 160,
    shineMs: 700,
    /** CARD-1: blur of the glass card background. */
    glassBlurPx: 18,
  },
  intro: {
    /** TR-1: logo hold on the dark screen (the logo fades in during `logoFadeInMs` of it), then the reveal. */
    logoHoldMs: 800,
    logoFadeInMs: 300,
    revealMs: 700,
    /** TR-1: the logo grows by this factor and fades while the dark disc shrinks away during the reveal. */
    revealLogoScale: 1.15,
    /** TR-2: sessionStorage flag set once the intro has played. */
    sessionStorageKey: 'sc-kpi.login.introPlayed',
  },
  success: {
    /**
     * TR-3: hard upper bound (at most 900 ms) after a successful sign-in; navigation happens when the full-screen
     * fade ends or at this moment, whichever comes first, never later.
     */
    transitionMs: 900,
    /** TR-3: card scale-down + fade. */
    cardMs: 450,
    cardScale: 0.92,
    /** TR-3: background zoom + blur, running through the whole visible transition. */
    backgroundMs: 800,
    backgroundScale: 1.08,
    backgroundBlurPx: 6,
    /** TR-3: full-screen fade into the app; `fadeDelayMs + fadeMs` must stay within `transitionMs`. */
    fadeDelayMs: 350,
    fadeMs: 450,
  },
  parallax: {
    /** AMB-1: maximum offset of the background (opposite to the cursor) and of the hero (with it). */
    backgroundMaxPx: 12,
    heroMaxPx: 6,
    /** Smoothing factor of the rAF lerp (0..1, higher follows the cursor faster). */
    smoothing: 0.08,
    /** The loop sleeps once every offset is closer than this to its target. */
    settlePx: 0.05,
  },
  cursorLight: {
    /** AMB-3: diameter and strength (0..1) of the soft highlight that follows the pointer over the card. */
    sizePx: 320,
    strength: 0.18,
    /** AMB-3: fade of the highlight when the pointer enters or leaves the card. */
    fadeMs: 300,
  },
  particles: {
    /** AMB-2: particle count range on desktop; halved on phones. */
    minCount: 15,
    maxCount: 30,
    /** AMB-2: desktop count (clamped to minCount..maxCount). */
    count: 24,
    phoneFactor: 0.5,
    /** AMB-2: phones get the halved count (below 48em = 768 px, or any coarse pointer). */
    phoneMaxWidthEm: 47.99,
    /**
     * AMB-2: dot diameter, one trip across the screen, sideways sway (amplitude and period range) and peak opacity of
     * the default variant. Slow on purpose: nothing may flash (spec §7).
     */
    default: {
      minSizePx: 2,
      maxSizePx: 6,
      minDurationMs: 16000,
      maxDurationMs: 28000,
      swayPx: 18,
      minSwayMs: 3000,
      maxSwayMs: 6000,
      maxOpacity: 0.45,
    },
    /** SEA-1: light snowfall (falls instead of rising, a little larger, more sway). */
    snow: {
      minSizePx: 3,
      maxSizePx: 8,
      minDurationMs: 12000,
      maxDurationMs: 22000,
      swayPx: 30,
      minSwayMs: 2500,
      maxSwayMs: 5000,
      maxOpacity: 0.7,
    },
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
    /** SEA-2: feature flag for seasonal accents (snowfall Dec-Feb); false always uses the default particles. */
    enabled: true,
    /** SEA-1: months (0 = January) with the snow variant. */
    snowMonths: [11, 0, 1] as readonly number[],
  },
} as const;

export type LoginPageConfig = typeof loginPageConfig;
