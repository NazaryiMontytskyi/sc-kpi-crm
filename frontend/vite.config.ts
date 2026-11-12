import react from '@vitejs/plugin-react';
import { defineConfig, loadEnv } from 'vite';
import { imagetools } from 'vite-imagetools';

// Sign-in background photos (../assets/main_page/images, see src/auth/login/slideshowImages.ts and ADR-0009).
// Only these files are transformed; brand assets and every other image import are left to Vite.
// `.jfif` is a JPEG container: sharp detects the format by content, so it is processed like any JPEG.
const SLIDE_IMAGES = /\/assets\/main_page\/images\/[^/?]+\.(avif|jfif|jpeg|jpg|png|webp)\?.*$/i;

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const backend = env.VITE_DEV_PROXY_TARGET || 'http://localhost:8080';

  return {
    // Unit tests never encode images (slow and covered by `npm run check:images` on the build output);
    // there the slide globs resolve to plain asset URLs, which discoverSlides() ignores.
    plugins: [react(), ...(mode === 'test' ? [] : [imagetools({ include: SLIDE_IMAGES })])],
    // Vite does not know the `.jfif` extension; treat it as an asset like `.jpg`.
    assetsInclude: ['**/*.jfif'],
    server: {
      port: 5173,
      // Brand assets and sign-in photos live outside the frontend root (../assets), see src/brand/ and src/auth/login/.
      fs: { allow: ['..'] },
      // Same-origin in dev as behind nginx in production: session and XSRF cookies work unchanged.
      proxy: { '/api': { target: backend, changeOrigin: true } },
    },
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: ['./src/test/setup.ts'],
      css: false,
      // Playwright scenarios (e2e/) run with `npm run e2e`, not with vitest.
      exclude: ['**/node_modules/**', '**/dist/**', 'e2e/**'],
    },
  };
});
