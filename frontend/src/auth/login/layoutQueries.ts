import { loginPageConfig } from './loginPage.config';

/** HERO-1 / CAP-1: from this media query on the sign-in page uses its desktop layout (1024 px). */
export const DESKTOP_QUERY = `(min-width: ${loginPageConfig.layout.desktopMinWidthEm}em)`;

/**
 * AMB-1 / AMB-3: parallax and the cursor light only run for a mouse-like pointer that can hover (desktop); touch
 * devices, pens without hover and phones in device emulation never match.
 */
export const FINE_POINTER_QUERY = '(hover: hover) and (pointer: fine)';

/** AMB-2: phone-sized viewport (below 768 px); with a coarse pointer it halves the particle count. */
export const PHONE_QUERY = `(max-width: ${loginPageConfig.particles.phoneMaxWidthEm}em)`;

/** AMB-2: any touch-first device (phones and tablets) counts as a phone for the particle budget. */
export const COARSE_POINTER_QUERY = '(pointer: coarse)';
