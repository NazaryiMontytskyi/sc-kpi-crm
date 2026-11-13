#!/usr/bin/env node
/**
 * JS/CSS budget of the animated sign-in page (MAIN_PAGE_FEATURE.md §8: "JS added by this feature <= 30 KB gzipped,
 * excluding images"; PLAN.md INC-062).
 * Usage: `npm run check:login-budget` (builds the app in memory with the project's Vite config; nothing is written).
 *
 * What is measured (an upper bound on purpose):
 * - Feature JS: the rendered code of every bundled module of the feature (`src/auth/LoginPage.tsx` and
 *   `src/auth/login/**`), wherever it lands (entry chunk or the lazy background chunk), concatenated and gzipped.
 *   The per-module code is taken before the final minification, so the real cost is lower.
 * - Feature CSS: every `src/auth/login/**\/*.module.css`, minified with lightningcss (the CSS minifier Vite uses),
 *   concatenated and gzipped.
 * - The lazy decoration chunk(s) (dynamic entries holding the background) with their CSS files, as emitted.
 * Fails when feature JS + feature CSS exceed 30 KB gzip, when the lazy decoration chunks with their CSS exceed it, or
 * when the entry chunk holds the slideshow glob / photo metadata (INC-057, ADR-0009).
 */
import { readdirSync, readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { gzipSync } from 'node:zlib';

/** §8: 30 KB gzip (decimal KB, like Vite's report). */
export const LOGIN_BUDGET_BYTES = 30 * 1000;

const normalise = (id) => id.split('\\').join('/');

/** Modules that belong to the sign-in feature. */
export function isFeatureModule(id) {
  const path = normalise(id);
  return /\/src\/auth\/login\//.test(path) || /\/src\/auth\/LoginPage\.tsx$/.test(path);
}

/** Modules that carry the photo glob or picture metadata; they must never be in the entry chunk. */
export function isPhotoMetadataModule(id) {
  const path = normalise(id);
  return /\/src\/auth\/login\/slideshowImages\.ts$/.test(path) || /\/assets\/main_page\/images\//.test(path);
}

export const gzipBytes = (text) => gzipSync(Buffer.from(text, 'utf8'), { level: 9 }).length;

/**
 * Pure evaluation, unit-tested in check-login-budget.test.mjs.
 * chunks: [{ fileName, isEntry, isDynamicEntry, code, modules: { [id]: code }, css: [cssFileName] }];
 * cssFiles: { [fileName]: source }; featureCss: minified CSS of the feature modules.
 */
export function evaluateBudget(chunks, cssFiles, featureCss, budget = LOGIN_BUDGET_BYTES) {
  const errors = [];
  const featureJs = chunks
    .flatMap((chunk) => Object.entries(chunk.modules).filter(([id]) => isFeatureModule(id)))
    .map(([, code]) => code)
    .join('\n');
  const featureJsGzip = gzipBytes(featureJs);
  const featureCssGzip = gzipBytes(featureCss);
  if (featureJsGzip + featureCssGzip > budget) {
    errors.push(`feature JS ${featureJsGzip} B + CSS ${featureCssGzip} B gzip exceed the budget of ${budget} B`);
  }

  const lazy = chunks.filter((chunk) => !chunk.isEntry && Object.keys(chunk.modules).some(isPhotoMetadataModule));
  if (lazy.length === 0) errors.push('no lazy chunk holds the sign-in background (slideshowImages)');
  const lazyGzip = lazy.reduce(
    (sum, chunk) => sum + gzipBytes(chunk.code) + chunk.css.reduce((s, file) => s + gzipBytes(cssFiles[file] ?? ''), 0),
    0,
  );
  if (lazyGzip > budget) errors.push(`lazy decoration chunks ${lazyGzip} B gzip exceed the budget of ${budget} B`);

  for (const entry of chunks.filter((chunk) => chunk.isEntry)) {
    const leaked = Object.keys(entry.modules).filter(isPhotoMetadataModule);
    if (leaked.length > 0) {
      errors.push(`entry chunk ${entry.fileName} holds photo metadata: ${leaked.map(normalise).join(', ')}`);
    }
  }
  return {
    errors,
    report: {
      featureJsGzip,
      featureCssGzip,
      totalGzip: featureJsGzip + featureCssGzip,
      lazyChunks: lazy.map((chunk) => chunk.fileName),
      lazyGzip,
      budget,
    },
  };
}

function walk(dir) {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name);
    return entry.isDirectory() ? walk(path) : [path];
  });
}

/** lightningcss is the CSS minifier of Vite; load the copy Vite depends on rather than declaring a new dependency. */
function loadLightningCss() {
  return createRequire(fileURLToPath(import.meta.resolve('vite')))('lightningcss');
}

async function main() {
  const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
  const { build } = await import('vite');
  const result = await build({
    root,
    configFile: join(root, 'vite.config.ts'),
    mode: 'production',
    logLevel: 'silent',
    build: { write: false },
  });
  const outputs = (Array.isArray(result) ? result : [result]).flatMap((r) => r.output);
  const chunks = outputs
    .filter((o) => o.type === 'chunk')
    .map((chunk) => ({
      fileName: chunk.fileName,
      isEntry: chunk.isEntry,
      isDynamicEntry: chunk.isDynamicEntry,
      code: chunk.code,
      modules: Object.fromEntries(Object.entries(chunk.modules).map(([id, m]) => [id, m.code ?? ''])),
      css: [...(chunk.viteMetadata?.importedCss ?? [])],
    }));
  const cssFiles = Object.fromEntries(
    outputs
      .filter((o) => o.type === 'asset' && o.fileName.endsWith('.css'))
      .map((o) => [o.fileName, typeof o.source === 'string' ? o.source : Buffer.from(o.source).toString('utf8')]),
  );

  const { transform } = loadLightningCss();
  const cssSources = walk(join(root, 'src', 'auth', 'login')).filter((path) => path.endsWith('.module.css'));
  const featureCss = cssSources
    .map((path) => transform({ filename: path, code: readFileSync(path), minify: true }).code.toString())
    .join('\n');

  const { errors, report } = evaluateBudget(chunks, cssFiles, featureCss);
  console.table({
    'feature JS (gzip, upper bound)': report.featureJsGzip,
    [`feature CSS (gzip, ${cssSources.length} modules)`]: report.featureCssGzip,
    'feature JS + CSS (gzip)': report.totalGzip,
    [`lazy decoration chunks + CSS (gzip): ${report.lazyChunks.join(', ')}`]: report.lazyGzip,
    budget: report.budget,
  });
  if (errors.length > 0) {
    for (const error of errors) console.error(`check:login-budget: ${error}`);
    process.exit(1);
  }
  console.log(
    `check:login-budget: OK (${report.totalGzip} B of ${report.budget} B gzip; photo metadata only in ` +
      `${report.lazyChunks.join(', ')})`,
  );
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  await main();
}
