/**
 * Contrast of the sign-in overlay text over the background photos (MAIN_PAGE_FEATURE.md §7, WCAG 2.x AA).
 *
 * Model (deliberately conservative, see overlay-contrast.test.mjs):
 * - Every photo is reduced to a grid of regions about one text line high (REGION_COLUMNS across) and the brightest
 *   region is taken, wherever it is: the photo is shown with `object-fit: cover` and Ken Burns, so any part of it can
 *   end up behind the text, and cropping or zooming never makes a region brighter.
 * - The dark overlay is a black layer of the given opacity, blended in sRGB like the browser does. Only its weakest
 *   point (`overlay.topOpacity`) is used: the gradient only gets darker towards the bottom and the vignette only adds.
 * - The film grain (`mix-blend-mode: overlay`) can brighten a dark pixel by at most its opacity (a white noise pixel
 *   doubles a dark base), so the composited channels are scaled by `1 + grainOpacity`.
 * - Text shadows are ignored (they only help).
 * Pure functions only; the photos are read with sharp by the caller.
 */

/** sRGB channel (0..255) to linear light (WCAG 2.x relative luminance definition). */
export function channelToLinear(channel) {
  const c = channel / 255;
  return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
}

/** WCAG relative luminance of an sRGB colour [r, g, b] (0..255). */
export function relativeLuminance([r, g, b]) {
  return 0.2126 * channelToLinear(r) + 0.7152 * channelToLinear(g) + 0.0722 * channelToLinear(b);
}

/** WCAG contrast ratio of two relative luminances (order does not matter). */
export function contrastRatio(a, b) {
  const [hi, lo] = a >= b ? [a, b] : [b, a];
  return (hi + 0.05) / (lo + 0.05);
}

/** `#rrggbb` / `#rgb` to [r, g, b]. */
export function hexToRgb(hex) {
  const value = hex.replace('#', '');
  const full = value.length === 3 ? [...value].map((c) => c + c).join('') : value;
  return [0, 2, 4].map((i) => parseInt(full.slice(i, i + 2), 16));
}

/**
 * Colour of a photo pixel under the overlay: `opacity` of `overlayRgb` blended over it in sRGB, then brightened by
 * the worst case of the film grain.
 */
export function composite(rgb, opacity, { overlayRgb = [0, 0, 0], grain = 0 } = {}) {
  return rgb.map((c, i) => Math.min(255, (c * (1 - opacity) + overlayRgb[i] * opacity) * (1 + grain)));
}

/** Contrast of a text colour over a photo region with the overlay applied. */
export function overlayContrast(regionRgb, textRgb, opacity, options) {
  return contrastRatio(relativeLuminance(textRgb), relativeLuminance(composite(regionRgb, opacity, options)));
}

/**
 * Smallest overlay opacity (0..1, rounded up to 0.001) at which the text reaches `ratio` over the region, or
 * `Infinity` when even an opaque overlay does not reach it. Contrast of light text grows monotonically with the
 * opacity of a dark overlay, so a bisection is exact to the rounding.
 */
export function minOverlayOpacity(regionRgb, textRgb, ratio, options) {
  const passes = (opacity) => overlayContrast(regionRgb, textRgb, opacity, options) >= ratio;
  if (passes(0)) return 0;
  if (!passes(1)) return Infinity;
  let lo = 0;
  let hi = 1;
  while (hi - lo > 1e-6) {
    const mid = (lo + hi) / 2;
    if (passes(mid)) hi = mid;
    else lo = mid;
  }
  return Math.ceil(hi * 1000) / 1000;
}

/** Brightest pixel (by relative luminance) of a raw interleaved buffer with `channels` per pixel. */
export function brightestPixel(data, channels) {
  let best = [0, 0, 0];
  let bestLuminance = -1;
  for (let i = 0; i + 2 < data.length; i += channels) {
    const rgb = [data[i], data[i + 1], data[i + 2]];
    const luminance = relativeLuminance(rgb);
    if (luminance > bestLuminance) {
      bestLuminance = luminance;
      best = rgb;
    }
  }
  return best;
}

/** Regions across the photo: on a 1920 px wide screen one region is about 30 px, i.e. one line of large text. */
export const REGION_COLUMNS = 64;

/** WCAG AA thresholds. */
export const AA_NORMAL = 4.5;
export const AA_LARGE = 3;

/**
 * Checks every photo against every text colour.
 * photos: [{ name, brightest: [r, g, b] }]; texts: [{ name, rgb, ratio }]; opacity: weakest overlay opacity.
 * Returns one row per photo and text, and the failures.
 */
export function evaluateOverlayContrast(photos, texts, opacity, options) {
  const rows = [];
  const errors = [];
  for (const photo of photos) {
    for (const text of texts) {
      const ratio = overlayContrast(photo.brightest, text.rgb, opacity, options);
      const threshold = minOverlayOpacity(photo.brightest, text.rgb, text.ratio, options);
      rows.push({ photo: photo.name, text: text.name, ratio: Math.round(ratio * 100) / 100, minOpacity: threshold });
      if (ratio < text.ratio) {
        errors.push(
          `${photo.name}: ${text.name} text has ${ratio.toFixed(2)}:1 at overlay ${opacity}, needs ${text.ratio}:1 ` +
            `(overlay >= ${threshold})`,
        );
      }
    }
  }
  return { rows, errors };
}
