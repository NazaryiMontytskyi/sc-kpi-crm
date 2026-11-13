import { Text, useComputedColorScheme } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { resolveBrandAsset, type BrandAsset } from './assets';

/**
 * `full` / `mark`: blue artwork for light surfaces (on a light chip in dark theme).
 * `full-white` / `mark-white`: white artwork for dark surfaces such as the sign-in photo overlay; never on a chip.
 */
export type BrandLogoVariant = 'full' | 'mark' | 'full-white' | 'mark-white';

interface BrandLogoProps {
  variant?: BrandLogoVariant;
  /** Use the short product name in the text fallback. */
  short?: boolean;
  height?: number;
  /** When false and no asset exists, render nothing (caller shows the name itself). */
  textFallback?: boolean;
  /** Injected in tests; defaults to the files found in assets/brand/. */
  assets?: Record<string, string>;
}

const WHITE_ASSETS: Partial<Record<BrandLogoVariant, BrandAsset>> = {
  'full-white': 'logo-full-white',
  'mark-white': 'logo-mark-white',
};

/** Renders the official logo if present in assets/brand/, otherwise the product name as plain text. */
export function BrandLogo({ variant = 'full', short = false, height = 32, textFallback = true, assets }: BrandLogoProps) {
  const { t } = useTranslation();
  const scheme = useComputedColorScheme('light');
  const name = t(short ? 'app.shortName' : 'app.name');
  const whiteAsset = WHITE_ASSETS[variant];

  let src: string | undefined;
  let onChip = false;
  if (whiteAsset) {
    // Meant for dark surfaces in both colour schemes: the white file as is, no chip, no recolouring.
    src = resolveBrandAsset(whiteAsset, assets);
  } else {
    const dark = scheme === 'dark';
    const asset: BrandAsset = variant === 'mark' ? 'logo-mark' : dark ? 'logo-full-light' : 'logo-full';
    // Dark theme: prefer a dark-background variant; otherwise show the blue logo on a light chip
    // (blue on dark has ~1.6:1 contrast). The artwork is never recolored.
    const darkVariant = dark && variant === 'full' ? resolveBrandAsset('logo-full-light', assets) : undefined;
    src = darkVariant ?? resolveBrandAsset(variant === 'mark' || !dark ? asset : 'logo-full', assets);
    onChip = dark && !darkVariant;
  }

  if (!src) {
    if (!textFallback) return null;
    return (
      <Text fw={700} component="span" data-testid="brand-text" c={whiteAsset ? 'white' : undefined}>
        {name}
      </Text>
    );
  }
  return (
    <img
      src={src}
      alt={name}
      height={height}
      data-variant={variant}
      data-on-chip={onChip ? 'true' : undefined}
      style={{
        display: 'block',
        maxWidth: '100%',
        ...(onChip ? { background: 'var(--mantine-color-white)', padding: 4, borderRadius: 'var(--mantine-radius-sm)' } : {}),
      }}
    />
  );
}
