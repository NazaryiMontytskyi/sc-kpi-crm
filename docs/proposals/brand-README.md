# Brand assets

Single source of truth for brand files of the Student Council of KPI information system. Never draw, recolor or generate logos; add only files supplied by the Student Council.

Brand color: `#0700CD` (shade 6 of the Mantine palette `studrada`, defined in `frontend/src/theme/colors.ts`). Components use theme tokens only.

## Files

| File | Original name | What it is | Where used |
|---|---|---|---|
| `logo/logo-mark-blue.png` | `Лого знак_синє.png` | Logo mark (symbol only), blue artwork for light backgrounds | Favicon; 404 / 403 / offline pages; compact header/sidebar placement |
| `logo/logo-full-blue.png` | `Лого СР_синє.png` | Full logo (mark + wordmark), blue artwork for light backgrounds | Sign-in page; header/sidebar when expanded |

Files are picked up at build time by `frontend/src/brand/assets.ts` (lookup order: `logo-*.svg`, `logo-*.png`, then `*-blue.png`). When a file is absent, the UI shows the product name as text.

## Dark theme

The blue artwork has about 1.6:1 contrast on dark surfaces. In dark theme the logo is shown on a white chip (rounded, 4 px padding) and is never recolored. If a dark-background variant is supplied as `logo/logo-full-light.svg|png`, it is used instead of the chip.

## Missing variants (not available, not generated)

- SVG versions of the mark and the full logo
- Logo variant for dark backgrounds (`logo-full-light`)
- Favicon set (`.ico`, 16/32/180 px) and PWA icons (192/512 px, maskable); PWA is `[v2]`
- Monochrome / print variants
