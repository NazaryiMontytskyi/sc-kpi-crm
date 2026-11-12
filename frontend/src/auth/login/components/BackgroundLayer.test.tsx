import { render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createLazyBackground, LoginBackground } from './LoginBackground';

afterEach(() => vi.restoreAllMocks());

describe('LoginBackground (lazy chunk)', () => {
  it('loads the background chunk; without encoded photos (unit tests) it shows the aurora', async () => {
    const consoleError = vi.spyOn(console, 'error');
    const { container } = render(<LoginBackground />);
    expect(await screen.findByTestId('login-background')).toBeInTheDocument();
    expect(screen.getByTestId('login-aurora')).toBeInTheDocument();
    expect(screen.queryByTestId('login-background-placeholder')).not.toBeInTheDocument();
    expect(container.querySelectorAll('img')).toHaveLength(0);
    expect(consoleError).not.toHaveBeenCalled();
  });

  it('falls back to the static placeholder when the chunk fails to load', async () => {
    // React reports errors caught by a boundary on the console; that is expected here.
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});
    const Broken = createLazyBackground(() => Promise.reject(new Error('chunk load failed')));
    render(<Broken />);
    // The boundary has caught the failed import once React reports it.
    await waitFor(() => expect(consoleError).toHaveBeenCalled());
    expect(screen.getByTestId('login-background-placeholder')).toBeInTheDocument();
    expect(screen.queryByTestId('login-background')).not.toBeInTheDocument();
  });
});

describe('photo metadata stays in the lazy chunk (ADR-0009)', () => {
  const sources = import.meta.glob(['/src/**/*.{ts,tsx}', '!/src/**/*.test.{ts,tsx}', '!/src/api/generated/**'], {
    query: '?raw',
    import: 'default',
    eager: true,
  }) as Record<string, string>;
  const STATIC_IMPORT = /^\s*import\b[^;]*['"][^'"]*\/slideshowImages['"]/m;

  it('LoginPage has no static import of slideshowImages', () => {
    expect(sources['/src/auth/LoginPage.tsx']).toBeDefined();
    expect(sources['/src/auth/LoginPage.tsx']).not.toMatch(/slideshowImages/);
  });

  it('only the lazily loaded BackgroundLayer imports slideshowImages at runtime', () => {
    const importers = Object.entries(sources)
      .filter(([, code]) => STATIC_IMPORT.test(code.replace(/^\s*import type\b.*$/gm, '')))
      .map(([path]) => path);
    expect(importers).toEqual(['/src/auth/login/components/BackgroundLayer.tsx']);
    expect(sources['/src/auth/login/components/LoginBackground.tsx']).toContain("import('./BackgroundLayer')");
  });
});
