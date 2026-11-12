# Brand assets

Single source of truth for brand files of the Student Council of KPI information system. Never draw, recolor or generate logos; add only files supplied by the Student Council.

Brand color: `#0700CD` (shade 6 of the Mantine palette `studrada`, defined in `frontend/src/theme/colors.ts`). Components use theme tokens only.

## Files

| File | Original name | What it is | Where used |
|---|---|---|---|
| `logo/logo-mark-blue.png` | `Лого знак_синє.png` | Logo mark (symbol only), blue artwork for light backgrounds | Favicon; 404 / 403 / offline pages; compact header/sidebar placement |
| `logo/logo-full-blue.png` | `Лого СР_синє.png` | Full logo (mark + wordmark), blue artwork for light backgrounds | Change-password page; header/sidebar when expanded |
| `logo/logo-mark-white.png` | `Лого знак_біле.png` | Logo mark (symbol only), white artwork for dark backgrounds | Sign-in page hero below 1024 px (over the dark photo overlay); intro splash of the sign-in page |
| `logo/logo-full-white.png` | `Лого СР_біле.png` | Full logo (mark + wordmark), white artwork for dark backgrounds | Sign-in page hero from 1024 px (over the dark photo overlay) |

Files are picked up at build time by `frontend/src/brand/assets.ts` (lookup order: `logo-*.svg`, `logo-*.png`, then `*-blue.png`; the white variants `logo-mark-white` / `logo-full-white` are looked up as `.svg`, then `.png`). When a file is absent, the UI shows the product name as text. The white files are copied byte-identical from the originals supplied by the Student Council; they are never derived from the blue files.

## Dark theme

The blue artwork has about 1.6:1 contrast on dark surfaces. In the app shell and on the change-password page the dark theme shows the blue logo on a white chip (rounded, 4 px padding); it is never recolored. If a dark-theme variant is supplied as `logo/logo-full-light.svg|png`, it is used there instead of the chip.

The sign-in page draws a dark overlay over photos in both colour schemes, so it uses the white artwork (`BrandLogo` variants `full-white` / `mark-white`) directly on the overlay, without a chip.

## Missing variants (not available, not generated)

- SVG versions of the mark and the full logo
- White artwork for dark backgrounds is available (`logo-*-white.png`); its use in the app shell / change-password page (the `logo-full-light` slot) is not decided yet
- Favicon set (`.ico`, 16/32/180 px) and PWA icons (192/512 px, maskable); PWA is `[v2]`
- Monochrome / print variants
