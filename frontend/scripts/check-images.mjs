#!/usr/bin/env node
/**
 * Post-build check of the sign-in background photos (MAIN_PAGE_FEATURE.md §6.1, §8; ADR-0009).
 * Run after `npm run build`: `npm run check:images [-- <distDir>]`.
 *
 * For every photo in ../assets/main_page/images it verifies that the build emitted AVIF and WebP variants at
 * 768/1280/1920 px (clamped to the original width, never upscaled), that the 768 px WebP variant stays within the
 * phone budget of 250 KB, and that no original file is served as-is. Exits with code 1 on any failure.
 */
import { createHash } from 'node:crypto';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { createRequire } from 'node:module';
import { basename, dirname, extname, join, relative, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

export const WIDTHS = [768, 1280, 1920];
export const FORMATS = ['avif', 'webp'];
/** §8: largest variant served to phones (the 768 px WebP) must not exceed 250 KB. */
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
    const phone = variants.find((v) => v.format === 'webp' && v.width === expected[0]);
    if (phone && phone.bytes > budget) {
      errors.push(`${source.name}: ${expected[0]} px WebP is ${phone.bytes} bytes, budget ${budget}`);
    }
    rows.push({
      source: source.name,
      width: source.width,
      variants: variants.length,
      phoneWebpKB: phone ? Math.round(phone.bytes / 100) / 10 : null,
    });
  }
  return { errors, rows };
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
  if (rows.length === 0) console.log('check:images: no photos in assets/main_page/images (aurora fallback only)');
  else console.table(rows);

  if (errors.length > 0) {
    for (const error of errors) console.error(`check:images: ${error}`);
    process.exit(1);
  }
  console.log(`check:images: OK (${sources.length} photos, ${FORMATS.join(' + ')} at ${WIDTHS.join('/')} px)`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  await main();
}
