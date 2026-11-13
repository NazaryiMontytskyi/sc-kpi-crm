import { loginPageConfig } from './loginPage.config';

/**
 * Pure maths of the mouse parallax (MAIN_PAGE_FEATURE.md AMB-1): where the layers want to be for a pointer position,
 * and the lerp that eases them there frame by frame (useParallax drives it with requestAnimationFrame).
 */

export interface ParallaxOffsets {
  /** Background offset in px, opposite to the pointer, at most `backgroundMaxPx` per axis. */
  bgX: number;
  bgY: number;
  /** Hero offset in px, with the pointer, at most `heroMaxPx` per axis. */
  heroX: number;
  heroY: number;
}

export const ZERO_OFFSETS: Readonly<ParallaxOffsets> = { bgX: 0, bgY: 0, heroX: 0, heroY: 0 };

interface ParallaxLimits {
  backgroundMaxPx: number;
  heroMaxPx: number;
}

const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value));
/** Avoids `-0`, which would print as "-0px" and fail strict equality in tests. */
const noNegativeZero = (value: number) => value || 0;

/** Pointer position relative to the viewport centre, -1 (left/top edge) .. 1 (right/bottom edge), clamped. */
function normalized(position: number, size: number): number {
  if (!(size > 0) || !Number.isFinite(position)) return 0;
  return clamp((position / size) * 2 - 1, -1, 1);
}

/** Target offsets for a pointer at (`x`, `y`) in a viewport of `width` x `height`. */
export function parallaxTargets(
  x: number,
  y: number,
  width: number,
  height: number,
  limits: ParallaxLimits = loginPageConfig.parallax,
): ParallaxOffsets {
  const nx = normalized(x, width);
  const ny = normalized(y, height);
  return {
    bgX: noNegativeZero(-nx * limits.backgroundMaxPx),
    bgY: noNegativeZero(-ny * limits.backgroundMaxPx),
    heroX: noNegativeZero(nx * limits.heroMaxPx),
    heroY: noNegativeZero(ny * limits.heroMaxPx),
  };
}

export function lerp(from: number, to: number, factor: number): number {
  return from + (to - from) * factor;
}

/**
 * One smoothing step of every offset towards its target. `settled` once all offsets are within `settlePx`; then the
 * offsets snap to the target and the loop may sleep until the pointer moves again.
 */
export function stepOffsets(
  current: ParallaxOffsets,
  target: ParallaxOffsets,
  factor: number = loginPageConfig.parallax.smoothing,
  settlePx: number = loginPageConfig.parallax.settlePx,
): { next: ParallaxOffsets; settled: boolean } {
  const keys = Object.keys(ZERO_OFFSETS) as (keyof ParallaxOffsets)[];
  const settled = keys.every((key) => Math.abs(target[key] - current[key]) < settlePx);
  if (settled) return { next: { ...target }, settled };
  const next = { ...current };
  for (const key of keys) next[key] = lerp(current[key], target[key], factor);
  return { next, settled };
}
