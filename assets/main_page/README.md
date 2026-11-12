# Sign-in page photos

Background photos of the sign-in page (`MAIN_PAGE_FEATURE.md` §5.1, §5.2). This folder is the single source of
truth: the frontend build reads it directly through `import.meta.glob`
(`frontend/src/auth/login/slideshowImages.ts`), the same way as `assets/brand/`. Nothing is copied into
`frontend/` or `src/`.

At build time `vite-imagetools` (see `docs/adr/0009-sign-in-photo-pipeline.md`) creates AVIF and WebP variants at
768, 1280 and 1920 px wide (never wider than the original). The originals in this folder are never served and
generated variants are never committed.

## Files

The initial set was copied byte-identical from `.claude/resources/main_page/images/` (those originals stay
untouched) and renamed `image_<n>.<original extension>`, numbered by the original file name in code-point
(ASCII) order.

| New name | Original name | Caption (uk / en) |
|---|---|---|
| `image_1.jpg` | `2024-k-18.jpg` | Корпус № 18 / Building 18 |
| `image_2.png` | `63365513065e8.png` | none (name gives no hint) |
| `image_3.png` | `ad61360f67b081b3346f1.png` | none (name gives no hint) |
| `image_4.webp` | `centr-kultury-i-iskusstv-kpi-v-kieve_5.webp` | Центр культури і мистецтв / Centre of Culture and Arts |
| `image_5.jpg` | `derzhavnyi-politekhnichnyi-muzei-1-1200x742.jpg` | Державний політехнічний музей / State Polytechnic Museum |
| `image_6.jfif` | `images.jfif` | none (name gives no hint) |
| `image_7.jpg` | `k-1-evening.jpg` | Головний корпус, вечір / Main Building, evening |
| `image_8.jpg` | `k-1-summer.jpg` | Головний корпус, літо / Main Building, summer |
| `image_9.webp` | `kpi-im-ihoria-sikorskoho-1.webp` | КПІ ім. Ігоря Сікорського / Igor Sikorsky Kyiv Polytechnic Institute |
| `image_10.jpg` | `kpi.jpg` | КПІ / KPI |
| `image_11.jpg` | `t-about-2023-12.jpg` | none (name gives no hint) |

The captions are placeholders derived from the original file names; correct them in `captions.json`.

## Adding or removing a photo

1. Put the file into `images/` (any name; the `image_<n>` convention only applies to the initial set). Supported
   extensions: `jpg`, `jpeg`, `jfif`, `png`, `webp`, `avif`. Use a landscape photo at least 1920 px wide if
   possible; smaller photos are not upscaled.
2. Optionally add a caption to `captions.json` under the exact file name.
3. Rebuild the frontend (`cd frontend && npm run build`) and run `npm run check:images`: it fails when the
   768 px WebP variant of any photo is larger than 250 KB (`MAIN_PAGE_FEATURE.md` §8).

No code change is needed. Removing a file works the same way; delete its caption entry too. An empty folder shows
the animated gradient fallback.

## Caption format

`images/captions.json` is optional. Keys are file names in this folder, values hold the Ukrainian and English text:

```json
{
  "image_7.jpg": { "uk": "Головний корпус, вечір", "en": "Main Building, evening" }
}
```

A photo without an entry (or without text in the current language) shows no caption; file names are never shown.
A missing or malformed file means no captions at all.
