/**
 * Brand asset loader. /assets/brand/ (repository root) is the single source of truth (CONTEXT.md 6.1);
 * files are imported at build time, never copied. When a file is missing the glob simply returns nothing
 * and callers fall back to the product name as text. Never draw or generate a logo here.
 */
const files = import.meta.glob('../../../assets/brand/**/*.{svg,png}', {
  eager: true,
  query: '?url',
  import: 'default',
}) as Record<string, string>;

export type BrandAsset = 'logo-full' | 'logo-full-light' | 'logo-mark';

const PATHS: Record<BrandAsset, string[]> = {
  'logo-full': ['logo/logo-full.svg', 'logo/logo-full.png'],
  'logo-full-light': ['logo/logo-full-light.svg', 'logo/logo-full.png'],
  'logo-mark': ['logo/logo-mark.svg', 'logo/logo-mark.png'],
};

export function resolveBrandAsset(
  asset: BrandAsset,
  available: Record<string, string> = files,
): string | undefined {
  for (const candidate of PATHS[asset]) {
    const key = Object.keys(available).find((k) => k.endsWith(`/assets/brand/${candidate}`));
    if (key) return available[key];
  }
  return undefined;
}
