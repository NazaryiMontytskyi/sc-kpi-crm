import { useQuery } from '@tanstack/react-query';
import { RESOLUTIONS_KEY } from '../tasks/useResolutions';
import { getMyDashboard } from './dashboardApi';

/** Invalidated together with every resolution change through RESOLUTIONS_KEY. */
export function useMyDashboard(enabled = true) {
  return useQuery({ queryKey: [...RESOLUTIONS_KEY, 'dashboard-me'], queryFn: getMyDashboard, enabled });
}
