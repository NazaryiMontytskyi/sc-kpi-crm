import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { renderWithProviders } from '../test/render';
import { resolveBrandAsset } from './assets';
import { BrandLogo } from './BrandLogo';

describe('brand assets', () => {
  it('renders the product name as text when no brand file exists', () => {
    renderWithProviders(<BrandLogo assets={{}} />);
    expect(screen.getByTestId('brand-text')).toHaveTextContent('SC KPI TMS');
    expect(screen.queryByRole('img')).toBeNull();
  });

  it('renders the logo image when the file exists', () => {
    renderWithProviders(<BrandLogo assets={{ '../../../assets/brand/logo/logo-full.svg': '/logo.svg' }} />);
    expect(screen.getByRole('img')).toHaveAttribute('src', '/logo.svg');
  });

  it('resolves nothing for a missing asset', () => {
    expect(resolveBrandAsset('logo-mark', {})).toBeUndefined();
  });
});
