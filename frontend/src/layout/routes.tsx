import type { ReactElement } from 'react';
import { AuditPage } from '../audit/AuditPage';
import { FacultiesPage } from '../members/FacultiesPage';
import { MemberProfilePage, MyProfileRedirect } from '../members/MemberProfilePage';
import { MembersPage } from '../members/MembersPage';
import { OrgPage } from '../org/OrgPage';
import { OrgUnitPage } from '../org/OrgUnitPage';
import { PositionPage } from '../org/PositionPage';
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
  { path: '/members', element: <MembersPage />, nav: { labelKey: 'nav.members' } },
  { path: '/members/:id', element: <MemberProfilePage /> },
  { path: '/profile', element: <MyProfileRedirect /> },
  { path: '/org', element: <OrgPage />, nav: { labelKey: 'nav.org' } },
  { path: '/org/positions/:id', element: <PositionPage /> },
  { path: '/org/:id', element: <OrgUnitPage /> },
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
  { path: '/audit', element: <AuditPage />, nav: { labelKey: 'nav.audit', permission: 'audit.view' } },
];
