import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { renderWithProviders } from '../test/render';
import { applyFavicon, resolveBrandAsset } from './assets';
import { BrandLogo } from './BrandLogo';

describe('brand assets', () => {
  it('renders the product name as text when no brand file exists', () => {
    renderWithProviders(<BrandLogo assets={{}} />);
    expect(screen.getByTestId('brand-text')).toHaveTextContent('SC KPI TMS');
    expect(screen.queryByRole('img')).toBeNull();
  });

  it('renders nothing when the file is missing and the text fallback is disabled', () => {
    const { container } = renderWithProviders(<BrandLogo variant="mark" textFallback={false} assets={{}} />);
    expect(container.querySelector('img')).toBeNull();
    expect(screen.queryByTestId('brand-text')).toBeNull();
  });

  it('renders the logo image when the file exists', () => {
    renderWithProviders(<BrandLogo assets={{ '../../../assets/brand/logo/logo-full.svg': '/logo.svg' }} />);
    expect(screen.getByRole('img')).toHaveAttribute('src', '/logo.svg');
  });

  it('resolves nothing for a missing asset', () => {
    expect(resolveBrandAsset('logo-mark', {})).toBeUndefined();
  });

  it('resolves the supplied *-blue.png file names', () => {
    const files = {
      '../../../assets/brand/logo/logo-full-blue.png': '/full.png',
      '../../../assets/brand/logo/logo-mark-blue.png': '/mark.png',
    };
    expect(resolveBrandAsset('logo-full', files)).toBe('/full.png');
    expect(resolveBrandAsset('logo-mark', files)).toBe('/mark.png');
    // No dark-background variant is supplied: never fall back to recoloring.
    expect(resolveBrandAsset('logo-full-light', files)).toBeUndefined();
  });

  it('shows the blue logo on a light chip in dark theme and plain in light theme', () => {
    const assets = { '../../../assets/brand/logo/logo-full-blue.png': '/full.png' };
    localStorage.setItem('mantine-color-scheme-value', 'dark');
    const { unmount } = renderWithProviders(<BrandLogo assets={assets} />);
    expect(screen.getByRole('img')).toHaveAttribute('data-on-chip', 'true');
    unmount();
    localStorage.setItem('mantine-color-scheme-value', 'light');
    renderWithProviders(<BrandLogo assets={assets} />);
    expect(screen.getByRole('img')).not.toHaveAttribute('data-on-chip');
  });

  it('sets the favicon from the logo mark only when it exists', () => {
    applyFavicon({});
    expect(document.querySelector('link[rel="icon"]')).toBeNull();
    applyFavicon({ '../../../assets/brand/logo/logo-mark-blue.png': '/mark.png' });
    expect(document.querySelector('link[rel="icon"]')?.getAttribute('href')).toBe('/mark.png');
  });
});
