import type { ReactElement } from 'react';
import { AuditPage } from '../audit/AuditPage';
import { ArticleEditPage } from '../knowledge/ArticleEditPage';
import { ArticlePage } from '../knowledge/ArticlePage';
import { KnowledgeHomePage } from '../knowledge/KnowledgeHomePage';
import { OnboardingManagePage } from '../knowledge/OnboardingManagePage';
import { OnboardingPage } from '../knowledge/OnboardingPage';
import { FacultiesPage } from '../members/FacultiesPage';
import { MemberProfilePage, MyProfileRedirect } from '../members/MemberProfilePage';
import { MembersPage } from '../members/MembersPage';
import { OrgPage } from '../org/OrgPage';
import { OrgUnitPage } from '../org/OrgUnitPage';
import { PositionPage } from '../org/PositionPage';
import { NotificationsPage } from '../notifications/NotificationsPage';
import { ResolutionRedirectPage } from '../notifications/ResolutionRedirectPage';
import { BoardPage } from '../tasks/BoardPage';
import { IssuedByMePage, MyResolutionsPage } from '../tasks/PersonalResolutionsPage';
import { SpaceSettingsPage } from '../tasks/SpaceSettingsPage';
import { SpacesPage } from '../tasks/SpacesPage';
import { TaskDetailPage } from '../tasks/TaskDetailPage';
import { TasksPage } from '../tasks/TasksPage';
import { SearchPage } from '../search/SearchPage';
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
  { path: '/tasks', element: <TasksPage />, nav: { labelKey: 'nav.tasks', permission: 'tasks.view' } },
  { path: '/tasks/board', element: <BoardPage /> },
  { path: '/resolutions/mine', element: <MyResolutionsPage />, nav: { labelKey: 'nav.myResolutions', permission: 'tasks.view' } },
  { path: '/resolutions/issued', element: <IssuedByMePage />, nav: { labelKey: 'nav.issuedByMe', permission: 'tasks.view' } },
  { path: '/resolutions/:id', element: <ResolutionRedirectPage /> },
  { path: '/search', element: <SearchPage /> },
  { path: '/notifications', element: <NotificationsPage />, nav: { labelKey: 'nav.notifications' } },
  { path: '/tasks/:id', element: <TaskDetailPage /> },
  { path: '/spaces', element: <SpacesPage />, nav: { labelKey: 'nav.spaces', permission: 'tasks.view' } },
  { path: '/spaces/:id', element: <SpaceSettingsPage /> },
  { path: '/knowledge', element: <KnowledgeHomePage />, nav: { labelKey: 'nav.knowledge', permission: 'kb.view' } },
  { path: '/knowledge/new', element: <ArticleEditPage /> },
  { path: '/knowledge/onboarding', element: <OnboardingPage /> },
  { path: '/knowledge/onboarding/manage', element: <OnboardingManagePage /> },
  { path: '/knowledge/:id', element: <ArticlePage /> },
  { path: '/knowledge/:id/edit', element: <ArticleEditPage /> },
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
