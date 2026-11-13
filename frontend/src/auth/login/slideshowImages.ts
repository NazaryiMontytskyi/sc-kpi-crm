/**
 * Sign-in background photos (MAIN_PAGE_FEATURE.md BG-1, BG-2, CAP-2).
 *
 * /assets/main_page/images/ (repository root) is the single source of truth: every photo there is picked up at
 * build time through import.meta.glob, exactly like the brand assets (src/brand/assets.ts). Adding or removing a
 * file and rebuilding needs no code change. vite-imagetools (vite.config.ts, ADR-0009) turns each photo into AVIF
 * and WebP variants at 768/1280/1920 px (never upscaled); the originals are never served.
 *
 * Captions come from the optional captions.json next to the photos: `{ "<file name>": { "uk": "...", "en": "..." } }`.
 */

/** Output of vite-imagetools `as=picture`: one srcset per format plus the largest fallback image. */
interface PictureModule {
  sources?: Record<string, unknown>;
  img?: { src?: unknown; w?: unknown; h?: unknown };
}

export type SlideFormat = 'avif' | 'webp';

export interface SlideImage {
  /** File name in assets/main_page/images, e.g. `image_1.jpg`; also the captions.json key. */
  id: string;
  /** `srcset` strings (with `w` descriptors) per format, best format first when rendered. */
  srcSets: Partial<Record<SlideFormat, string>>;
  /** Plain `src` for the `<img>` element (WebP). */
  fallbackSrc: string;
  /** Intrinsic size of the fallback variant, for aspect ratio. */
  width: number;
  height: number;
}

export interface SlideCaption {
  uk?: string;
  en?: string;
}

const slideModules = import.meta.glob(
  '../../../../assets/main_page/images/*.{jpg,jpeg,jfif,png,webp,avif,JPG,JPEG,JFIF,PNG,WEBP,AVIF}',
  {
    eager: true,
    query: { w: '768;1280;1920', format: 'avif;webp', as: 'picture' },
    import: 'default',
  },
) as Record<string, unknown>;

const captionFiles = import.meta.glob('../../../../assets/main_page/images/captions.json', {
  eager: true,
  query: '?raw',
  import: 'default',
}) as Record<string, unknown>;

const FORMATS: readonly SlideFormat[] = ['avif', 'webp'];
const IMAGE_FILE_NAME = /^[^\s/]+\.(avif|jfif|jpe?g|png|webp)$/i;

function fileName(path: string): string {
  return path.slice(path.lastIndexOf('/') + 1);
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

function toSlide(path: string, module: unknown): SlideImage | undefined {
  if (typeof module !== 'object' || module === null) return undefined;
  const picture = module as PictureModule;
  const img = picture.img;
  if (!img || !isNonEmptyString(img.src)) return undefined;
  const width = typeof img.w === 'number' ? img.w : 0;
  const height = typeof img.h === 'number' ? img.h : 0;

  const srcSets: Partial<Record<SlideFormat, string>> = {};
  for (const format of FORMATS) {
    const srcSet = picture.sources?.[format];
    if (isNonEmptyString(srcSet)) srcSets[format] = srcSet;
  }
  // imagetools omits the fallback format's source when it has a single variant (photo narrower than 768 px).
  if (!srcSets.webp && width > 0) srcSets.webp = `${img.src} ${width}w`;

  return { id: fileName(path), srcSets, fallbackSrc: img.src, width, height };
}

/**
 * Turns the glob result into slides, sorted by file name. Entries that are not processed pictures
 * (for example in unit tests, where images are not encoded) are skipped, so an empty or broken folder yields [].
 */
export function discoverSlides(modules: Record<string, unknown> = slideModules): SlideImage[] {
  return Object.entries(modules)
    .map(([path, module]) => toSlide(path, module))
    .filter((slide): slide is SlideImage => slide !== undefined)
    .sort((a, b) => a.id.localeCompare(b.id, 'en', { numeric: true }));
}

/** Parses captions.json; undefined when the file is missing or malformed. */
export function parseCaptions(raw: unknown): Record<string, SlideCaption> | undefined {
  if (typeof raw !== 'string') return undefined;
  try {
    const parsed: unknown = JSON.parse(raw);
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) return undefined;
    return parsed as Record<string, SlideCaption>;
  } catch {
    return undefined;
  }
}

/** Captions bundled with the build, or undefined when captions.json is absent or invalid. */
export function loadCaptions(files: Record<string, unknown> = captionFiles): Record<string, SlideCaption> | undefined {
  const raw = Object.values(files)[0];
  return parseCaptions(raw);
}

/**
 * Localized caption of a slide (CAP-2). Returns undefined when there are no captions, the file is malformed, the
 * image has no entry or no text in that language. Never falls back to the file name.
 */
export function captionFor(id: string, lang: string, captions: unknown): string | undefined {
  if (typeof captions !== 'object' || captions === null || Array.isArray(captions)) return undefined;
  if (!Object.prototype.hasOwnProperty.call(captions, id)) return undefined;
  const entry: unknown = (captions as Record<string, unknown>)[id];
  if (typeof entry !== 'object' || entry === null || Array.isArray(entry)) return undefined;
  const base = lang.toLowerCase().split('-')[0];
  if (!Object.prototype.hasOwnProperty.call(entry, base)) return undefined;
  const text: unknown = (entry as Record<string, unknown>)[base];
  if (!isNonEmptyString(text)) return undefined;
  const trimmed = text.trim();
  // A caption that is just a file name is a mistake in captions.json: show nothing rather than a file name.
  return trimmed === id || IMAGE_FILE_NAME.test(trimmed) ? undefined : trimmed;
}
