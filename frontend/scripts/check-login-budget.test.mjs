import { describe, expect, it } from 'vitest';
import { evaluateBudget, gzipBytes, isFeatureModule, isPhotoMetadataModule, LOGIN_BUDGET_BYTES } from './check-login-budget.mjs';

const ROOT = 'C:/repo/frontend';

/** Incompressible, deterministic filler whose gzip size is just at or above `bytes`. */
function noise(bytes, seed = 1) {
  let x = seed;
  let out = '';
  while (gzipBytes(out) < bytes) {
    for (let i = 0; i < 200; i++) {
      // xorshift32: exact in 32-bit integer arithmetic, so the sequence never degenerates.
      x ^= x << 13;
      x ^= x >>> 17;
      x ^= x << 5;
      out += String.fromCharCode(33 + ((x >>> 0) % 90));
    }
  }
  return out;
}

function chunks({ entryFeature = 'export const card = 1;', lazyFeature = 'export const slides = [];', leak = false } = {}) {
  const entryModules = {
    [`${ROOT}/src/main.tsx`]: 'render(App)',
    [`${ROOT}/src/auth/login/components/LoginCard.tsx`]: entryFeature,
    [`${ROOT}/src/auth/LoginPage.tsx`]: 'export function LoginPage() {}',
  };
  if (leak) entryModules[`${ROOT}/src/auth/login/slideshowImages.ts`] = 'glob()';
  return [
    { fileName: 'assets/index-A.js', isEntry: true, isDynamicEntry: false, code: 'entry', modules: entryModules, css: [] },
    {
      fileName: 'assets/BackgroundLayer-B.js',
      isEntry: false,
      isDynamicEntry: true,
      code: lazyFeature,
      modules: {
        [`${ROOT}/src/auth/login/slideshowImages.ts`]: lazyFeature,
        [`${ROOT}/../assets/main_page/images/image_1.jpg?w=768`]: '{}',
      },
      css: ['assets/BackgroundLayer-B.css'],
    },
  ];
}

describe('check-login-budget', () => {
  it('attributes the sign-in modules to the feature and finds the photo metadata', () => {
    expect(isFeatureModule(`${ROOT}/src/auth/login/hooks/useParallax.ts`)).toBe(true);
    expect(isFeatureModule('C:\\repo\\frontend\\src\\auth\\LoginPage.tsx')).toBe(true);
    expect(isFeatureModule(`${ROOT}/src/auth/ChangePasswordPage.tsx`)).toBe(false);
    expect(isFeatureModule(`${ROOT}/node_modules/react/index.js`)).toBe(false);
    expect(isPhotoMetadataModule(`${ROOT}/src/auth/login/slideshowImages.ts`)).toBe(true);
    expect(isPhotoMetadataModule('C:/repo/assets/main_page/images/image_2.png?as=picture')).toBe(true);
    expect(isPhotoMetadataModule(`${ROOT}/src/auth/login/slidePicture.ts`)).toBe(false);
  });

  it('passes and reports the sizes within the budget', () => {
    const { errors, report } = evaluateBudget(chunks(), { 'assets/BackgroundLayer-B.css': '.a{opacity:0}' }, '.b{}');
    expect(errors).toEqual([]);
    expect(report.lazyChunks).toEqual(['assets/BackgroundLayer-B.js']);
    expect(report.totalGzip).toBe(report.featureJsGzip + report.featureCssGzip);
    expect(report.budget).toBe(30_000);
  });

  it('fails above 30 KB gzip of feature JS + CSS, counting the entry and lazy chunks alike', () => {
    const half = noise(LOGIN_BUDGET_BYTES / 2 + 500);
    const { errors } = evaluateBudget(chunks({ entryFeature: half, lazyFeature: 'x' }), {}, noise(500, 7));
    expect(errors).toHaveLength(0);
    const over = evaluateBudget(chunks({ entryFeature: half, lazyFeature: noise(LOGIN_BUDGET_BYTES / 2, 3) }), {}, '');
    expect(over.errors.some((e) => e.startsWith('feature JS'))).toBe(true);
    expect(gzipBytes(half)).toBeGreaterThan(LOGIN_BUDGET_BYTES / 2);
  });

  it('fails when the CSS pushes it over the budget', () => {
    const js = noise(LOGIN_BUDGET_BYTES - 2000, 5);
    expect(evaluateBudget(chunks({ entryFeature: js }), {}, '').errors).toEqual([]);
    const { errors } = evaluateBudget(chunks({ entryFeature: js }), {}, noise(3000, 9));
    expect(errors).toHaveLength(1);
    expect(errors[0]).toMatch(/^feature JS \d+ B \+ CSS \d+ B gzip exceed the budget of 30000 B$/);
  });

  it('fails when the lazy decoration chunks with their CSS exceed the budget', () => {
    const { errors } = evaluateBudget(chunks(), { 'assets/BackgroundLayer-B.css': noise(LOGIN_BUDGET_BYTES + 100, 11) }, '');
    expect(errors).toEqual([expect.stringMatching(/^lazy decoration chunks \d+ B gzip exceed the budget/)]);
  });

  it('fails when the entry chunk holds the slideshow glob metadata', () => {
    const { errors } = evaluateBudget(chunks({ leak: true }), {}, '');
    expect(errors).toEqual([`entry chunk assets/index-A.js holds photo metadata: ${ROOT}/src/auth/login/slideshowImages.ts`]);
  });

  it('fails when no lazy chunk holds the background', () => {
    const [entry] = chunks();
    expect(evaluateBudget([entry], {}, '').errors).toEqual(['no lazy chunk holds the sign-in background (slideshowImages)']);
  });
});
