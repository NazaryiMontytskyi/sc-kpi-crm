import { createContext, useContext } from 'react';

/**
 * Places inside the sign-in frame (LoginShell) where the lazily loaded background renders its slide controls
 * through portals: the caption on top on phones and bottom-left on desktop (CAP-1), the indicator above the footer
 * (CAP-3). This keeps the photo metadata in the lazy chunk while the controls sit in the page's tab order
 * (after the card, before the motion toggle) and above the frame.
 */
export interface SlideControlsHosts {
  /** Phone/tablet caption slot in the top bar (< 1024 px). */
  captionCompact: HTMLElement | null;
  /** Desktop caption slot bottom-left (>= 1024 px). */
  captionWide: HTMLElement | null;
  /** Slide indicator slot above the footer line. */
  indicator: HTMLElement | null;
}

export const NO_HOSTS: SlideControlsHosts = { captionCompact: null, captionWide: null, indicator: null };

export const SlideControlsHostsContext = createContext<SlideControlsHosts>(NO_HOSTS);

export function useSlideControlsHosts(): SlideControlsHosts {
  return useContext(SlideControlsHostsContext);
}
