import type { ReactElement } from 'react';
import { FacultiesPage } from '../members/FacultiesPage';
import { HomePage } from '../pages/HomePage';
import { RoleAssignmentsPage } from '../roles/RoleAssignmentsPage';
import { RolesPage } from '../roles/RolesPage';

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
export const appRoutes: AppRoute[] = [
  { path: '/', element: <HomePage />, nav: { labelKey: 'nav.home' } },
  {
    path: '/settings/faculties',
    element: <FacultiesPage />,
    nav: { labelKey: 'nav.settingsFaculties', permission: 'settings.manage' },
  },
  { path: '/roles', element: <RolesPage />, nav: { labelKey: 'nav.roles', permission: 'roles.manage' } },
  {
    path: '/role-assignments',
    element: <RoleAssignmentsPage />,
    nav: { labelKey: 'nav.roleAssignments', permission: 'roles.assign' },
  },
];
