# ADR-0009: Sign-in photo pipeline (vite-imagetools)

- Status: Proposed (draft by INC-056, to be accepted with the plan "Main page (animated login)")
- Date: 2026-11-12

## Context
The animated sign-in page (MAIN_PAGE_FEATURE.md §5.1, §6.1, §8) shows campus photos as a full-screen slideshow.
The originals (11 files, about 8 MB, JPEG/PNG/WebP/JFIF, up to 3.6 MB each) must stay unmodified in one committed
folder; optimised copies must be produced at build time only (never committed), as WebP and ideally AVIF in 2-3
widths served through `srcset`, so phones never download the largest size; the 768 px variant must be at most
250 KB. Adding or removing a photo must not need a code change (BG-2). The frontend builds with Vite 8 inside
`node:22-alpine` with the repository root as Docker context (ADR-0008); the brand assets already reach the build
through `import.meta.glob` on `../assets/brand` (INC-050).

## Decision
- **Source folder.** `assets/main_page/images/` at the repository root is the single source of truth (renamed,
  byte-identical copies of the supplied originals; mapping in `assets/main_page/README.md`) plus the optional
  `captions.json`. Nothing goes into `src/` or `frontend/public`.
- **Discovery.** `frontend/src/auth/login/slideshowImages.ts` uses one eager `import.meta.glob` over
  `*.{jpg,jpeg,jfif,png,webp,avif}` (upper case too) with the query `w=768;1280;1920&format=avif;webp&as=picture`,
  and a second glob with `?raw` for `captions.json`. `discoverSlides()` turns the result into
  `SlideImage { id, srcSets: { avif, webp }, fallbackSrc, width, height }`, skipping anything that is not a
  processed picture, so an empty folder yields `[]`.
- **Optimisation: `vite-imagetools` 12.0.2** (devDependency, exact pin; peer `vite >= 8`; uses `sharp` 0.35).
  Verified in INC-056: works with Vite 8.3 (rolldown) on Windows and in `node:22-alpine` (sharp ships prebuilt
  `linuxmusl-x64`/`arm64` binaries that are recorded in `package-lock.json`). The plugin is restricted with an
  `include` regex to `/assets/main_page/images/*` (with `.jfif` added; sharp detects JPEG by content), so brand
  assets and all other image imports are untouched. It clamps requested widths to the original width (never
  upscales) and de-duplicates them; a 678 px photo therefore gets one variant per format. Output files are emitted
  by the bundler under `dist/assets/<name>-<hash>.{avif,webp}`; the plugin cache lives in
  `frontend/node_modules/.cache/imagetools`. Nothing generated is tracked by git (`dist` and `node_modules` are
  ignored).
- **Not in unit tests.** In Vite mode `test` the plugin is not registered (encoding takes about a minute on a cold
  cache); the globs then resolve to plain URLs and `discoverSlides()` ignores them. Unit tests inject glob results.
  `.jfif` is added to `assetsInclude` so Vite treats it as an asset in that mode.
- **Post-build check.** `npm run check:images` (`frontend/scripts/check-images.mjs`, uses the `sharp` that
  `vite-imagetools` depends on) verifies, for every original, AVIF and WebP variants at the clamped widths, the
  768 px WebP within 250 KB, and that no original file is emitted unchanged. Its decision logic is pure and
  unit-tested (`scripts/check-images.test.mjs`).
- **Rejected / fallback.** A custom `frontend/scripts/build-images.mjs` running `sharp` from `prebuild` into a
  git-ignored `frontend/.generated/` with a manifest. It stays the documented fallback if `vite-imagetools` ever
  breaks with a Vite or Node upgrade; it must keep the same `SlideImage` contract so `slideshowImages.ts` consumers
  do not change. Committed hand-made copies and serving the folder from Spring Boot were rejected (spec §6.1).

## Consequences
- A cold build (Docker always builds cold) spends about one minute encoding AVIF/WebP for the current 11 photos;
  incremental local builds reuse the cache. Adding large photos increases build time roughly linearly.
- The photos are emitted by the build as soon as `slideshowImages.ts` is part of the module graph (it is imported
  by `LoginPage.tsx`); the background component that renders them comes in a later increment.
- On high-density phones (`sizes="100vw"`, DPR 2-3) the browser may select the 1280 px variant; the 250 KB budget
  is enforced for the 768 px variant as the spec states. The manual Lighthouse run (INC-069) is where the real
  transfer on phones is measured.
- One more devDependency with a native binary (`sharp`); unsupported platforms would fail `npm ci` at build time,
  not at runtime.
