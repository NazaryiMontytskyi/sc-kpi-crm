import type { SlideFormat, SlideImage } from './slideshowImages';

/**
 * Responsive image policy of the background photos (MAIN_PAGE_FEATURE.md §6.1, §8; ADR-0009).
 *
 * Desktop and tablet (>= 768 CSS px) get every variant through `srcset` + `sizes`. Phones (<= 767 CSS px) get only
 * the smallest variant of each format through `<source media>`, whatever their pixel density: a DPR 2-3 phone would
 * otherwise pick the 1280 or 1920 px file, which can exceed the 250 KB phone budget. The photo sits under a dark
 * overlay and film grain, so the softer image is not noticeable there. `npm run check:images` enforces the budget
 * for exactly these phone variants (PHONE_VARIANT rule in scripts/check-images.mjs).
 */

/** Largest viewport width that counts as a phone. */
export const PHONE_MAX_WIDTH_PX = 767;
export const PHONE_MEDIA = `(max-width: ${PHONE_MAX_WIDTH_PX}px)`;
/** The photo covers the full viewport width. */
export const SLIDE_SIZES = '100vw';
/** Best format first: the browser takes the first `<source>` whose type it supports. */
export const SOURCE_FORMATS: readonly SlideFormat[] = ['avif', 'webp'];

export interface SrcSetCandidate {
  url: string;
  width: number;
}

/** Parses a `srcset` with `w` descriptors; candidates without a width descriptor are ignored. */
export function parseSrcSet(srcSet: string): SrcSetCandidate[] {
  return srcSet
    .split(',')
    .map((part) => part.trim().split(/\s+/))
    .filter((tokens) => tokens.length === 2 && /^\d+w$/.test(tokens[1]))
    .map(([url, descriptor]) => ({ url, width: Number.parseInt(descriptor, 10) }));
}

/** The phone `srcset`: only the narrowest candidate. Undefined for an empty or unparsable srcset. */
export function phoneSrcSet(srcSet: string | undefined): string | undefined {
  if (!srcSet) return undefined;
  const candidates = parseSrcSet(srcSet);
  if (candidates.length === 0) return undefined;
  const smallest = candidates.reduce((a, b) => (b.width < a.width ? b : a));
  return `${smallest.url} ${smallest.width}w`;
}

export interface PictureSource {
  type: string;
  srcSet: string;
  media?: string;
  sizes: string;
}

/** `<source>` elements of a slide in document order: phone sources first, then the full sets. */
export function pictureSources(slide: SlideImage): PictureSource[] {
  const phone: PictureSource[] = [];
  const full: PictureSource[] = [];
  for (const format of SOURCE_FORMATS) {
    const srcSet = slide.srcSets[format];
    if (!srcSet) continue;
    const type = `image/${format}`;
    const narrow = phoneSrcSet(srcSet);
    if (narrow) phone.push({ type, srcSet: narrow, media: PHONE_MEDIA, sizes: SLIDE_SIZES });
    full.push({ type, srcSet, sizes: SLIDE_SIZES });
  }
  return [...phone, ...full];
}
