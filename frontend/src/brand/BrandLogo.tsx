import { Text, useComputedColorScheme } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { resolveBrandAsset, type BrandAsset } from './assets';

interface BrandLogoProps {
  variant?: 'full' | 'mark';
  /** Use the short product name in the text fallback. */
  short?: boolean;
  height?: number;
  /** Injected in tests; defaults to the files found in assets/brand/. */
  assets?: Record<string, string>;
}

/** Renders the official logo if present in assets/brand/, otherwise the product name as plain text. */
export function BrandLogo({ variant = 'full', short = false, height = 32, assets }: BrandLogoProps) {
  const { t } = useTranslation();
  const scheme = useComputedColorScheme('light');
  const asset: BrandAsset =
    variant === 'mark' ? 'logo-mark' : scheme === 'dark' ? 'logo-full-light' : 'logo-full';
  const src = resolveBrandAsset(asset, assets);
  const name = t(short ? 'app.shortName' : 'app.name');

  if (!src) {
    return (
      <Text fw={700} component="span" data-testid="brand-text">
        {name}
      </Text>
    );
  }
  return <img src={src} alt={name} height={height} style={{ display: 'block', maxWidth: '100%' }} />;
}
