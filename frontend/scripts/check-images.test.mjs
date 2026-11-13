import { describe, expect, it } from 'vitest';
import { phoneSrcSet, pictureSources } from '../src/auth/login/slidePicture.ts';
import {
  entryScript,
  evaluate,
  evaluateChunks,
  expectedWidths,
  PHONE_BUDGET_BYTES,
  phoneVariants,
} from './check-images.mjs';

function variants(stem, widths, bytes = 100_000) {
  return ['avif', 'webp'].flatMap((format) =>
    widths.map((width, i) => ({
      file: `assets/${stem}-ABCDEF_${i}.${format}`,
      format,
      width,
      bytes,
      sha256: `${stem}-${format}-${width}`,
    })),
  );
}

describe('check-images', () => {
  it('clamps the widths to the original and never upscales', () => {
    expect(expectedWidths(2000)).toEqual([768, 1280, 1920]);
    expect(expectedWidths(1200)).toEqual([768, 1200]);
    expect(expectedWidths(678)).toEqual([678]);
  });

  it('passes for a complete set within the budget', () => {
    const sources = [
      { name: 'image_1.jpg', width: 2000, sha256: 'src-1' },
      { name: 'image_11.jfif', width: 678, sha256: 'src-11' },
    ];
    const outputs = [...variants('image_1', [768, 1280, 1920]), ...variants('image_11', [678])];
    expect(evaluate(sources, outputs).errors).toEqual([]);
  });

  it('fails when the 768 px WebP exceeds 250 KB', () => {
    const sources = [{ name: 'big.png', width: 3000, sha256: 'src' }];
    const outputs = variants('big', [768, 1280, 1920]).map((o) =>
      o.format === 'webp' && o.width === 768 ? { ...o, bytes: PHONE_BUDGET_BYTES + 1 } : o,
    );
    expect(evaluate(sources, outputs).errors).toEqual([
      `big.png: 768 px WebP is ${PHONE_BUDGET_BYTES + 1} bytes, budget ${PHONE_BUDGET_BYTES}`,
    ]);
  });

  it('fails when a format or width is missing', () => {
    const sources = [{ name: 'image_2.png', width: 2000, sha256: 'src' }];
    const outputs = variants('image_2', [768, 1280, 1920]).filter((o) => !(o.format === 'avif' && o.width === 1920));
    expect(evaluate(sources, outputs).errors).toEqual(['image_2.png: avif widths [768, 1280], expected [768, 1280, 1920]']);
  });

  it('fails when an original is served as-is', () => {
    const sources = [{ name: 'image_3.png', width: 1665, sha256: 'original' }];
    const outputs = [
      ...variants('image_3', [768, 1280, 1665]),
      { file: 'assets/image_3-12345678.png', format: 'png', bytes: 3_600_000, sha256: 'original' },
    ];
    expect(evaluate(sources, outputs).errors).toEqual([
      'assets/image_3-12345678.png: original image_3.png is served as-is instead of being converted',
    ]);
  });

  it('does not mix up image_1 and image_11', () => {
    const sources = [{ name: 'image_1.jpg', width: 2000, sha256: 'src' }];
    expect(evaluate(sources, variants('image_11', [768, 1280, 1920])).errors).toHaveLength(2);
  });

  it('fails when the narrowest AVIF a phone can select exceeds 250 KB', () => {
    const sources = [{ name: 'big.png', width: 3000, sha256: 'src' }];
    const outputs = variants('big', [768, 1280, 1920]).map((o) =>
      o.format === 'avif' && o.width === 768 ? { ...o, bytes: PHONE_BUDGET_BYTES + 5 } : o,
    );
    expect(evaluate(sources, outputs).errors).toEqual([
      `big.png: 768 px AVIF is ${PHONE_BUDGET_BYTES + 5} bytes, budget ${PHONE_BUDGET_BYTES}`,
    ]);
  });

  it('ignores the large 1280/1920 px variants for the phone budget: phones never select them', () => {
    // Real case: image_11 is 341 KB at 1920 px and image_2 is 268 KB at 1280 px (WebP).
    const sources = [{ name: 'image_11.webp', width: 2000, sha256: 'src' }];
    const outputs = variants('image_11', [768, 1280, 1920]).map((o) =>
      o.width === 768 ? o : { ...o, bytes: 341_000 },
    );
    expect(evaluate(sources, outputs).errors).toEqual([]);
    expect(phoneVariants(sources[0], outputs).map((v) => `${v.format}@${v.width}`)).toEqual(['avif@768', 'webp@768']);
  });

  it('matches the phone policy of the slideshow markup (only the narrowest candidate per format)', () => {
    const srcSet = '/a-768.webp 768w, /a-1280.webp 1280w, /a-1920.webp 1920w';
    expect(phoneSrcSet(srcSet)).toBe('/a-768.webp 768w');
    const slide = {
      id: 'a.jpg',
      srcSets: { avif: srcSet.replaceAll('webp', 'avif'), webp: srcSet },
      fallbackSrc: '/a-1920.webp',
      width: 1920,
      height: 1080,
    };
    const phone = pictureSources(slide).filter((s) => s.media === '(max-width: 767px)');
    expect(phone.map((s) => s.srcSet)).toEqual(['/a-768.avif 768w', '/a-768.webp 768w']);
    // The narrowest width the build emits is the one the phone sources list.
    expect(expectedWidths(1920)[0]).toBe(768);
  });
});

describe('check-images: photo metadata only in a lazy chunk', () => {
  const html = '<script type="module" crossorigin src="/assets/index-AAAA1111.js"></script>';
  const names = ['image_1-BR7YxAyt.avif', 'image_1-K36b5_RT.webp'];

  it('reads the entry script from index.html', () => {
    expect(entryScript(html)).toBe('assets/index-AAAA1111.js');
    expect(entryScript('<html></html>')).toBeUndefined();
  });

  it('passes when only a lazy chunk references the variants', () => {
    const chunks = [
      { file: 'assets/index-AAAA1111.js', code: 'import("./BackgroundLayer-x.js")' },
      { file: 'assets/BackgroundLayer-x.js', code: `"/assets/${names[0]}","/assets/${names[1]}"` },
    ];
    expect(evaluateChunks(entryScript(html), chunks, names)).toEqual([]);
  });

  it('fails when the entry chunk contains the photo metadata', () => {
    const chunks = [{ file: 'assets/index-AAAA1111.js', code: `"/assets/${names[0]}"` }];
    expect(evaluateChunks(entryScript(html), chunks, names)).toEqual([
      'entry chunk assets/index-AAAA1111.js contains photo metadata (1 variants, e.g. image_1-BR7YxAyt.avif)',
      'no lazy chunk references the photo variants',
    ]);
  });

  it('fails without an entry script', () => {
    expect(evaluateChunks(undefined, [], names)).toEqual(['index.html has no module entry script']);
  });
});
