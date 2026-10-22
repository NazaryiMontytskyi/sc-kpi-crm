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
  // *-blue.png are the files supplied so far (blue brand artwork, meant for light surfaces).
  'logo-full': ['logo/logo-full.svg', 'logo/logo-full.png', 'logo/logo-full-blue.png'],
  // Variant made for dark backgrounds; absent today, so dark theme shows the blue file on a light chip.
  'logo-full-light': ['logo/logo-full-light.svg', 'logo/logo-full-light.png'],
  'logo-mark': ['logo/logo-mark.svg', 'logo/logo-mark.png', 'logo/logo-mark-blue.png'],
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

/** Points the favicon at the logo mark when it exists; otherwise the browser default stays. */
export function applyFavicon(available: Record<string, string> = files): void {
  if (typeof document === 'undefined') return;
  const href = resolveBrandAsset('logo-mark', available);
  if (!href) return;
  let link = document.querySelector<HTMLLinkElement>('link[rel="icon"]');
  if (!link) {
    link = document.createElement('link');
    link.rel = 'icon';
    document.head.appendChild(link);
  }
  link.href = href;
}
