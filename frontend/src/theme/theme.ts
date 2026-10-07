import { createTheme, type CSSVariablesResolver, type MantineColorsTuple } from '@mantine/core';
import { studrada } from './colors';

export const studradaPalette = [...studrada] as unknown as MantineColorsTuple;

/** Brand shade used for links, text and icons on dark surfaces (contrast ~7:1; shade 6 would be ~1.6:1). */
export const DARK_TEXT_SHADE = 2;
/** Brand shade used for links and text on light surfaces. */
export const LIGHT_TEXT_SHADE = 6;

export const theme = createTheme({
  primaryColor: 'studrada',
  primaryShade: { light: 6, dark: 5 },
  colors: { studrada: studradaPalette },
  // Neutral surfaces: Mantine's default `gray` (light) and `dark` (dark) scales; the brand color stays an accent.
  defaultRadius: 'md',
  fontFamily: 'system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
});

/**
 * Links (anchor) and brand-colored text: shade 6 in light, shade 2 in dark.
 * Never use the primary shade 6 as text/icon color on dark backgrounds.
 */
export const cssVariablesResolver: CSSVariablesResolver = () => ({
  variables: {},
  light: {
    '--mantine-color-anchor': `var(--mantine-color-studrada-${LIGHT_TEXT_SHADE})`,
  },
  dark: {
    '--mantine-color-anchor': `var(--mantine-color-studrada-${DARK_TEXT_SHADE})`,
  },
});
