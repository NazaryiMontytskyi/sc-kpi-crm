import type { ReactElement } from 'react';
import { HomePage } from '../pages/HomePage';

export interface AppRoute {
  path: string;
  element: ReactElement;
  /** When set, the route appears in the navigation. Routes that do not exist are simply not listed. */
  nav?: { labelKey: string; permission?: string };
}

/**
 * Feature route registry. Each feature adds its routes here (with `nav` for a menu entry);
 * navigation is derived from this list, so entries for not-yet-built features never show up.
 */
export const appRoutes: AppRoute[] = [{ path: '/', element: <HomePage />, nav: { labelKey: 'nav.home' } }];
