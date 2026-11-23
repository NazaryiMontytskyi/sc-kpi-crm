import { api } from '../api/client';
import { toProblemError } from '../api/problem';

export const DASHBOARD_VIEW = 'dashboard.view';

/**
 * "My metrics" of the signed-in user (GET /dashboard/me, `tasks` section): open tasks I am an active assignee of and
 * the overdue ones among them (Europe/Kyiv days); archived tasks and DONE/CANCELED status categories are excluded.
 */
export interface MyTaskMetrics {
  active: number;
  overdue: number;
}

export async function getMyDashboard(): Promise<MyTaskMetrics> {
  const { data, error, response } = await api.GET('/api/v1/dashboard/me');
  if (!data) throw toProblemError(response, error);
  return { active: data.tasks?.active ?? 0, overdue: data.tasks?.overdue ?? 0 };
}
