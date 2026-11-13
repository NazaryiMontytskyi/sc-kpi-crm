# ADR-0009: Sign-in photo pipeline (vite-imagetools)

- Status: Accepted (proposed by INC-056 on 2026-11-12; accepted with INC-062, the `vite-imagetools` pipeline stays)
- Date: 2026-11-13

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
  `vite-imagetools` depends on) verifies, for every original, AVIF and WebP variants at the clamped widths, every
  phone-selectable variant (the narrowest AVIF and WebP, see "Phone variants") within 250 KB, that no original file
  is emitted unchanged, and that the photo metadata is referenced only by a lazy chunk, never by the entry chunk
  named in `index.html` (INC-057). Its decision logic is pure and unit-tested (`scripts/check-images.test.mjs`).
- **Phone variants (INC-057).** `frontend/src/auth/login/slidePicture.ts` renders, before the full `srcset`
  sources, one `<source media="(max-width: 767px)">` per format that lists only the narrowest variant. A DPR 2-3
  phone therefore never selects the 1280 or 1920 px files (some 1280 px WebPs are 263-276 KB, the 1920 px
  `image_11` WebP is 341 KB); no extra width or encoder setting was needed. The photo sits under a dark overlay
  and film grain, so the softer image on dense phone screens is acceptable.
- **Lazy loading (INC-057).** `slideshowImages.ts` is imported only by
  `src/auth/login/components/BackgroundLayer.tsx`, which `LoginBackground.tsx` loads with `React.lazy`; the glob
  and picture metadata (about 4 KB gzip with the slideshow code) stay out of the entry chunk.
- **Rejected / fallback.** A custom `frontend/scripts/build-images.mjs` running `sharp` from `prebuild` into a
  git-ignored `frontend/.generated/` with a manifest. It stays the documented fallback if `vite-imagetools` ever
  breaks with a Vite or Node upgrade; it must keep the same `SlideImage` contract so `slideshowImages.ts` consumers
  do not change. Committed hand-made copies and serving the folder from Spring Boot were rejected (spec §6.1).

- **Budgets and contrast (INC-062).** `npm run check:login-budget` (`frontend/scripts/check-login-budget.mjs`)
  builds the app in memory and fails when the sign-in code (every module of `src/auth/login/**` and
  `LoginPage.tsx`, wherever it is bundled) plus its CSS modules exceed 30 KB gzip, when the lazy background chunk
  exceeds it, or when the entry chunk holds the slideshow glob / photo metadata. `npm run check:contrast`
  (`frontend/scripts/overlay-contrast.test.mjs`, also part of `npm test`) reads every photo with the same `sharp`,
  takes its brightest region and asserts white overlay text >= 4.5:1 at the weakest overlay opacity
  (`loginPage.config.ts` `overlay.topOpacity`). Adding a much brighter photo can therefore fail `npm test` until the
  overlay is tuned there.

## Consequences
- A cold build (Docker always builds cold) spends about one minute encoding AVIF/WebP for the current 11 photos;
  incremental local builds reuse the cache. Adding large photos increases build time roughly linearly.
- The photos are emitted by the build as soon as `slideshowImages.ts` is part of the module graph (through the
  lazily loaded background chunk).
- Phones (<= 767 CSS px) get the narrowest variant whatever their pixel density, so the 250 KB budget holds for
  every variant they can select; browsers without `<picture>`/WebP support would fall back to the `<img>` WebP
  `srcset`, which is not a realistic case. The manual Lighthouse run (INC-062) is where the real transfer on
  phones is measured.
- One more devDependency with a native binary (`sharp`); unsupported platforms would fail `npm ci` at build time,
  not at runtime.
