import { useCallback } from 'react';
import { useLocation, useMatch, useNavigate, type To } from 'react-router-dom';
import { trackerSearch } from './taskLogic';

/** Router state set by tracker links: closing the panel then simply goes back in history. */
export interface TrackerLinkState {
  fromTracker: true;
}

export const TASK_PANEL_PATTERN = '/tasks/:id';

function isTrackerState(state: unknown): state is TrackerLinkState {
  return typeof state === 'object' && state !== null && (state as { fromTracker?: unknown }).fromTracker === true;
}

/** Id of the task open in the panel (`/tasks/:id`), if any. */
export function useSelectedTaskId(): string | undefined {
  return useMatch(TASK_PANEL_PATTERN)?.params.id;
}

export interface TaskLinkProps {
  to: To;
  state: TrackerLinkState;
  replace: boolean;
}

/**
 * Link props that open a task in the panel in place (`/tasks/:id`, spec UI-1). While a panel is already open the
 * entry is replaced, so the browser back button always closes the panel instead of walking through tasks.
 */
export function useTaskLink(): (taskId: string) => TaskLinkProps {
  const location = useLocation();
  const open = !!useSelectedTaskId();
  return useCallback(
    (taskId: string) => ({
      to: { pathname: `/tasks/${taskId}`, search: trackerSearch(location.search) },
      state: { fromTracker: true },
      replace: open,
    }),
    [location.search, open],
  );
}

/** Closes the panel: back in history when it was opened from the tracker, otherwise to the plain tracker URL. */
export function useClosePanel(): () => void {
  const location = useLocation();
  const navigate = useNavigate();
  return useCallback(() => {
    if (isTrackerState(location.state)) navigate(-1);
    else navigate({ pathname: '/tasks', search: trackerSearch(location.search) });
  }, [location.state, location.search, navigate]);
}
