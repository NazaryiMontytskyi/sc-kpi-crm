import type { ReactNode } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { ForbiddenPage } from '../pages/ErrorPages';
import { hasPermission, useAuth } from './CurrentUser';

/** Redirects anonymous visitors to the sign-in page (route added in INC-008). Renders nothing while loading. */
export function RequireAuth({ children }: { children?: ReactNode }) {
  const { status } = useAuth();
  const location = useLocation();
  if (status === 'loading') return null;
  if (status === 'anonymous') return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return <>{children ?? <Outlet />}</>;
}

/**
 * Shows the 403 page when the current user lacks `permission`.
 * CONVENIENCE ONLY: this hides UI, it is not security. The backend enforces every permission
 * (CONTEXT.md §5.2) and stays authoritative.
 */
export function RequirePermission({ permission, children }: { permission: string; children?: ReactNode }) {
  const { user } = useAuth();
  if (!hasPermission(user, permission)) return <ForbiddenPage />;
  return <>{children ?? <Outlet />}</>;
}

/** Hides children without the permission (for buttons/menu items). Convenience only, see RequirePermission. */
export function IfPermission({ permission, children }: { permission: string; children: ReactNode }) {
  const { user } = useAuth();
  return hasPermission(user, permission) ? <>{children}</> : null;
}
