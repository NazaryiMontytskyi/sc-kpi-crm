import { DEFAULT_THEME } from '@mantine/core';
import { readdirSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, extname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { beforeAll, describe, expect, it } from 'vitest';
import { loginPageConfig } from '../src/auth/login/loginPage.config.ts';
import { studrada } from '../src/theme/colors.ts';
import { SOURCE_EXTENSIONS } from './check-images.mjs';
import {
  AA_LARGE,
  AA_NORMAL,
  brightestPixel,
  composite,
  contrastRatio,
  evaluateOverlayContrast,
  hexToRgb,
  minOverlayOpacity,
  overlayContrast,
  REGION_COLUMNS,
  relativeLuminance,
} from './overlay-contrast.mjs';

const WHITE = hexToRgb(DEFAULT_THEME.white);
const BLACK = hexToRgb(DEFAULT_THEME.black);
const SHADE_2 = hexToRgb(studrada[2]);
const { topOpacity, bottomOpacity, grainOpacity } = loginPageConfig.overlay;
const OPTIONS = { overlayRgb: BLACK, grain: grainOpacity };

describe('overlay contrast arithmetic', () => {
  it('follows the WCAG definitions', () => {
    expect(relativeLuminance(WHITE)).toBeCloseTo(1, 6);
    expect(relativeLuminance(BLACK)).toBe(0);
    expect(contrastRatio(1, 0)).toBeCloseTo(21, 6);
    expect(contrastRatio(0, 1)).toBeCloseTo(21, 6);
    // Brand shade 6 on white is about 11:1 (CLAUDE.md).
    expect(contrastRatio(relativeLuminance(hexToRgb(studrada[6])), 1)).toBeGreaterThan(10.5);
    expect(hexToRgb('#fff')).toEqual([255, 255, 255]);
  });

  it('blends the overlay in sRGB and adds the worst case of the grain', () => {
    expect(composite([200, 100, 0], 0.5)).toEqual([100, 50, 0]);
    composite([200, 100, 0], 0.5, { grain: 0.1 }).forEach((c, i) => expect(c).toBeCloseTo([110, 55, 0][i], 9));
    expect(composite([255, 255, 255], 0, { grain: 0.5 })).toEqual([255, 255, 255]);
  });

  it('computes the threshold opacity: at it the text passes, just below it fails', () => {
    // White text over a white photo region, no grain: the overlay must reach about 53.5 %.
    const threshold = minOverlayOpacity(WHITE, WHITE, AA_NORMAL, { overlayRgb: BLACK });
    expect(threshold).toBeCloseTo(0.535, 3);
    expect(overlayContrast(WHITE, WHITE, threshold, { overlayRgb: BLACK })).toBeGreaterThanOrEqual(AA_NORMAL);
    expect(overlayContrast(WHITE, WHITE, threshold - 0.002, { overlayRgb: BLACK })).toBeLessThan(AA_NORMAL);
    // Grain and darker text need more overlay; large text needs less; a dark region needs none.
    expect(minOverlayOpacity(WHITE, WHITE, AA_NORMAL, OPTIONS)).toBeGreaterThan(threshold);
    expect(minOverlayOpacity(WHITE, SHADE_2, AA_NORMAL, OPTIONS)).toBeGreaterThan(threshold);
    expect(minOverlayOpacity(WHITE, WHITE, AA_LARGE, OPTIONS)).toBeLessThan(threshold);
    expect(minOverlayOpacity([20, 20, 20], WHITE, AA_NORMAL, OPTIONS)).toBe(0);
    // Unreachable: mid-grey text over a mid-grey region fails with no overlay and with an opaque one.
    expect(minOverlayOpacity([128, 128, 128], [90, 90, 90], AA_NORMAL, OPTIONS)).toBe(Infinity);
  });

  it('finds the brightest pixel of a raw buffer', () => {
    const rgba = Uint8Array.from([10, 10, 10, 255, 250, 240, 230, 255, 0, 0, 255, 255]);
    expect(brightestPixel(rgba, 4)).toEqual([250, 240, 230]);
  });

  it('reports a failure, with the required opacity, when the overlay is too weak', () => {
    const photos = [{ name: 'bright.jpg', brightest: WHITE }];
    const texts = [{ name: 'white', rgb: WHITE, ratio: AA_NORMAL }];
    const threshold = minOverlayOpacity(WHITE, WHITE, AA_NORMAL, OPTIONS);
    expect(evaluateOverlayContrast(photos, texts, threshold, OPTIONS).errors).toEqual([]);
    const weak = evaluateOverlayContrast(photos, texts, threshold - 0.01, OPTIONS);
    expect(weak.errors).toHaveLength(1);
    expect(weak.errors[0]).toContain(`overlay >= ${threshold}`);
  });
});

describe('sign-in photos under the overlay (MAIN_PAGE_FEATURE.md §7)', { timeout: 60000 }, () => {
  const imagesDir = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', 'assets', 'main_page', 'images');
  /** @type {{ name: string, brightest: number[] }[]} */
  let photos = [];

  beforeAll(async () => {
    // sharp is the engine of vite-imagetools (see check-images.mjs); no extra dependency.
    const require = createRequire(import.meta.url);
    const sharpModule = createRequire(require.resolve('vite-imagetools'))('sharp');
    const sharp = sharpModule.default ?? sharpModule;
    const names = readdirSync(imagesDir).filter((name) => SOURCE_EXTENSIONS.includes(extname(name).toLowerCase()));
    photos = await Promise.all(
      names.map(async (name) => {
        const { data, info } = await sharp(join(imagesDir, name))
          .rotate()
          .removeAlpha()
          .resize({ width: REGION_COLUMNS })
          .raw()
          .toBuffer({ resolveWithObject: true });
        return { name, brightest: brightestPixel(data, info.channels) };
      }),
    );
  });

  it('measures every photo of assets/main_page/images', () => {
    expect(photos.length).toBeGreaterThan(0);
  });

  it('keeps white overlay text at >= 4.5:1 (and large text >= 3:1) on the brightest region of every photo', () => {
    const texts = [
      { name: 'white', rgb: WHITE, ratio: AA_NORMAL },
      { name: 'white (large)', rgb: WHITE, ratio: AA_LARGE },
    ];
    const { errors, rows } = evaluateOverlayContrast(photos, texts, topOpacity, OPTIONS);
    expect(errors).toEqual([]);
    expect(rows).toHaveLength(photos.length * texts.length);
  });

  it('fails as soon as the overlay drops below the computed threshold', () => {
    const texts = [{ name: 'white', rgb: WHITE, ratio: AA_NORMAL }];
    const threshold = Math.max(...photos.map((p) => minOverlayOpacity(p.brightest, WHITE, AA_NORMAL, OPTIONS)));
    expect(topOpacity).toBeGreaterThanOrEqual(threshold);
    expect(evaluateOverlayContrast(photos, texts, threshold - 0.01, OPTIONS).errors.length).toBeGreaterThan(0);
  });

  it('keeps the shade-2 focus rings of the slide indicator at >= 3:1 in the bottom band (WCAG 1.4.11)', () => {
    // The indicator sits in the bottom tenth of the screen, where the gradient is at least this dark.
    const bottomBand = topOpacity + (bottomOpacity - topOpacity) * 0.9;
    const rings = [{ name: 'studrada-2 ring', rgb: SHADE_2, ratio: AA_LARGE }];
    expect(evaluateOverlayContrast(photos, rings, bottomBand, OPTIONS).errors).toEqual([]);
  });

  it('keeps the card text at >= 4.5:1 on the translucent card in both schemes', () => {
    const dark = DEFAULT_THEME.colors.dark;
    // Light scheme: 82 % white card; the worst case behind it is a black region (dark text needs a light surface).
    const lightCard = composite(BLACK, 0.82, { overlayRgb: WHITE });
    for (const text of [BLACK, hexToRgb(DEFAULT_THEME.colors.gray[7])]) {
      expect(contrastRatio(relativeLuminance(text), relativeLuminance(lightCard))).toBeGreaterThanOrEqual(AA_NORMAL);
    }
    // Dark scheme: 74 % dark-7 card over the brightest photo region under the overlay.
    for (const photo of photos) {
      const behind = composite(photo.brightest, topOpacity, OPTIONS);
      const darkCard = composite(behind, 0.74, { overlayRgb: hexToRgb(dark[7]) });
      for (const text of [hexToRgb(dark[0]), hexToRgb(dark[1])]) {
        expect(contrastRatio(relativeLuminance(text), relativeLuminance(darkCard))).toBeGreaterThanOrEqual(AA_NORMAL);
      }
    }
  });
});
