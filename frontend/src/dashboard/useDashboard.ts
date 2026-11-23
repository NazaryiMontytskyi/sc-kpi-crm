import { useQuery } from '@tanstack/react-query';
import { TASKS_KEY } from '../tasks/useTasks';
import { getMyDashboard } from './dashboardApi';

/** Lives under TASKS_KEY, so every task change (status, deadline, assignees) refreshes the counters too. */
export function useMyDashboard(enabled = true) {
  return useQuery({ queryKey: [...TASKS_KEY, 'dashboard-me'], queryFn: getMyDashboard, enabled });
}
