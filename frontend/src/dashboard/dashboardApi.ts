import { api } from '../api/client';
import { toProblemError } from '../api/problem';
import type { ResolutionState } from '../tasks/resolutionsApi';

export const DASHBOARD_VIEW = 'dashboard.view';

export interface UpcomingResolution {
  resolutionId: string;
  taskId: string;
  taskTitle: string;
  dueDate: string;
  state: ResolutionState;
  overdue: boolean;
}

/** Personal resolution summary of the signed-in user (GET /dashboard/me). */
export interface MyResolutionsSummary {
  active: number;
  overdue: number;
  awaitingAcceptance: number;
  upcoming: UpcomingResolution[];
}

export async function getMyDashboard(): Promise<MyResolutionsSummary> {
  const { data, error, response } = await api.GET('/api/v1/dashboard/me');
  if (!data) throw toProblemError(response, error);
  const r = data.resolutions;
  return {
    active: r?.active ?? 0,
    overdue: r?.overdue ?? 0,
    awaitingAcceptance: r?.awaitingAcceptance ?? 0,
    upcoming: (r?.upcoming ?? []).map((u) => ({
      resolutionId: u.resolutionId ?? '',
      taskId: u.taskId ?? '',
      taskTitle: u.taskTitle ?? '',
      dueDate: u.dueDate ?? '',
      state: (u.state ?? 'NEW') as ResolutionState,
      overdue: u.overdue === true,
    })),
  };
}
