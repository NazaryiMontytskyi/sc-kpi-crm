#!/usr/bin/env node
/**
 * Post-build check of the sign-in background photos (MAIN_PAGE_FEATURE.md §6.1, §8; ADR-0009).
 * Run after `npm run build`: `npm run check:images [-- <distDir>]`.
 *
 * For every photo in ../assets/main_page/images it verifies that the build emitted AVIF and WebP variants at
 * 768/1280/1920 px (clamped to the original width, never upscaled), that every variant a phone can select stays
 * within the phone budget of 250 KB, and that no original file is served as-is. It also verifies that the photo
 * metadata (the slideshow glob) lives in a lazily loaded chunk, never in the entry chunk named by index.html.
 * Exits with code 1 on any failure.
 *
 * Phone variants: viewports <= 767 CSS px get only the narrowest variant of each format through
 * `<source media="(max-width: 767px)">`, whatever their pixel density (src/auth/login/slidePicture.ts). So for
 * DPR 1-3 phones the selectable files are exactly the narrowest AVIF and WebP, and both must be within budget.
 */
import { createHash } from 'node:crypto';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { createRequire } from 'node:module';
import { basename, dirname, extname, join, relative, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

export const WIDTHS = [768, 1280, 1920];
export const FORMATS = ['avif', 'webp'];
/** §8: every variant a phone (viewport <= 767 CSS px, any DPR) can select must not exceed 250 KB. */
export const PHONE_BUDGET_BYTES = 250 * 1000;
export const SOURCE_EXTENSIONS = ['.jpg', '.jpeg', '.jfif', '.png', '.webp', '.avif'];

/** Widths the build must emit for an original of the given width (clamped, deduplicated, ascending). */
export function expectedWidths(sourceWidth) {
  return [...new Set(WIDTHS.map((w) => Math.min(w, sourceWidth)))].sort((a, b) => a - b);
}

function escapeRegExp(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/** Emitted variants of an original: Vite names them `<stem>-<8 char hash>.<format>`. */
export function variantsOf(sourceName, outputs) {
  const stem = basename(sourceName, extname(sourceName));
  const pattern = new RegExp(`^${escapeRegExp(stem)}-[A-Za-z0-9_-]{8}\\.(avif|webp)$`);
  return outputs.filter((o) => pattern.test(basename(o.file)));
}

/**
 * Pure evaluation, unit-tested in check-images.test.mjs.
 * sources: [{ name, width, sha256 }]; outputs: every file of dist as [{ file, width?, format?, bytes, sha256 }].
 */
export function evaluate(sources, outputs, budget = PHONE_BUDGET_BYTES) {
  const errors = [];
  const rows = [];
  const sourceHashes = new Map(sources.map((s) => [s.sha256, s.name]));

  for (const output of outputs) {
    const original = sourceHashes.get(output.sha256);
    if (original) errors.push(`${output.file}: original ${original} is served as-is instead of being converted`);
  }

  for (const source of sources) {
    const expected = expectedWidths(source.width);
    const variants = variantsOf(source.name, outputs);
    for (const format of FORMATS) {
      const actual = variants
        .filter((v) => v.format === format)
        .map((v) => v.width)
        .sort((a, b) => a - b);
      if (actual.join(',') !== expected.join(',')) {
        errors.push(`${source.name}: ${format} widths [${actual.join(', ')}], expected [${expected.join(', ')}]`);
      }
    }
    // Phones only ever get the narrowest variant of each format (see the header comment).
    for (const variant of phoneVariants(source, variants)) {
      if (variant.bytes > budget) {
        const label = variant.format === 'webp' ? 'WebP' : variant.format.toUpperCase();
        errors.push(`${source.name}: ${variant.width} px ${label} is ${variant.bytes} bytes, budget ${budget}`);
      }
    }
    const phone = variants.find((v) => v.format === 'webp' && v.width === expected[0]);
    rows.push({
      source: source.name,
      width: source.width,
      variants: variants.length,
      phoneWebpKB: phone ? Math.round(phone.bytes / 100) / 10 : null,
    });
  }
  return { errors, rows };
}

/** Variants a phone can select: the narrowest variant of each format. */
export function phoneVariants(source, variants) {
  const narrowest = expectedWidths(source.width)[0];
  return FORMATS.flatMap((format) => variants.filter((v) => v.format === format && v.width === narrowest));
}

/** The entry script of the app, from the built index.html. */
export function entryScript(indexHtml) {
  const tag = indexHtml.match(/<script\b[^>]*\btype="module"[^>]*\bsrc="([^"]+)"/);
  return tag ? tag[1].replace(/^\//, '') : undefined;
}

/**
 * The photo metadata must only be in a lazily loaded chunk (INC-057): the entry chunk must not reference any
 * emitted photo variant, and some other JS chunk must (otherwise the background has no photos at all).
 * chunks: [{ file, code }] of every JS file; variantFiles: base names of the emitted photo variants.
 */
export function evaluateChunks(entryFile, chunks, variantFiles) {
  const errors = [];
  if (!entryFile) return ['index.html has no module entry script'];
  const entry = chunks.find((c) => c.file === entryFile);
  if (!entry) return [`entry chunk ${entryFile} not found`];
  if (variantFiles.length === 0) return errors;
  const leaked = variantFiles.filter((name) => entry.code.includes(name));
  if (leaked.length > 0) {
    errors.push(`entry chunk ${entryFile} contains photo metadata (${leaked.length} variants, e.g. ${leaked[0]})`);
  }
  const holders = chunks.filter((c) => c.file !== entryFile && variantFiles.some((name) => c.code.includes(name)));
  if (holders.length === 0) errors.push('no lazy chunk references the photo variants');
  return errors;
}

function sha256(buffer) {
  return createHash('sha256').update(buffer).digest('hex');
}

function walk(dir) {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name);
    return entry.isDirectory() ? walk(path) : [path];
  });
}

/** sharp is the engine of vite-imagetools; load the copy it depends on rather than declaring a second dependency. */
async function loadSharp() {
  const pluginEntry = import.meta.resolve('vite-imagetools');
  const sharp = createRequire(fileURLToPath(pluginEntry))('sharp');
  return sharp.default ?? sharp;
}

async function main() {
  const here = dirname(fileURLToPath(import.meta.url));
  const distDir = resolve(process.argv[2] ?? join(here, '..', 'dist'));
  const sourceDir = resolve(here, '..', '..', 'assets', 'main_page', 'images');

  if (!statSync(distDir, { throwIfNoEntry: false })?.isDirectory()) {
    console.error(`check:images: ${distDir} not found, run "npm run build" first`);
    process.exit(1);
  }
  const sharp = await loadSharp();

  const sourceFiles = statSync(sourceDir, { throwIfNoEntry: false })?.isDirectory()
    ? readdirSync(sourceDir).filter((f) => SOURCE_EXTENSIONS.includes(extname(f).toLowerCase()))
    : [];
  const sources = await Promise.all(
    sourceFiles.map(async (name) => {
      const path = join(sourceDir, name);
      const meta = await sharp(path).metadata();
      return { name, width: meta.autoOrient?.width ?? meta.width, sha256: sha256(readFileSync(path)) };
    }),
  );

  const outputs = await Promise.all(
    walk(distDir).map(async (path) => {
      const buffer = readFileSync(path);
      const file = relative(distDir, path).split('\\').join('/');
      const ext = extname(path).slice(1).toLowerCase();
      const output = { file, bytes: buffer.length, sha256: sha256(buffer), format: ext };
      if (FORMATS.includes(ext)) output.width = (await sharp(buffer).metadata()).width;
      return output;
    }),
  );

  const { errors, rows } = evaluate(sources, outputs);
  const indexHtml = readFileSync(join(distDir, 'index.html'), 'utf8');
  const chunks = outputs
    .filter((o) => o.format === 'js')
    .map((o) => ({ file: o.file, code: readFileSync(join(distDir, o.file), 'utf8') }));
  const variantFiles = sources.flatMap((s) => variantsOf(s.name, outputs)).map((v) => basename(v.file));
  errors.push(...evaluateChunks(entryScript(indexHtml), chunks, variantFiles));
  if (rows.length === 0) console.log('check:images: no photos in assets/main_page/images (aurora fallback only)');
  else console.table(rows);

  if (errors.length > 0) {
    for (const error of errors) console.error(`check:images: ${error}`);
    process.exit(1);
  }
  console.log(
    `check:images: OK (${sources.length} photos, ${FORMATS.join(' + ')} at ${WIDTHS.join('/')} px, ` +
      'phone variants within budget, photo metadata only in a lazy chunk)',
  );
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  await main();
}
