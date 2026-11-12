import { describe, expect, it } from 'vitest';
import { evaluate, expectedWidths, PHONE_BUDGET_BYTES } from './check-images.mjs';

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
});
