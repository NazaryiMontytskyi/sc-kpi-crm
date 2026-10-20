import type { MemberResolutionQuery, MemberResolutionRole } from './memberResolutionsApi';
import { RESOLUTION_STATES, type ResolutionState } from './resolutionsApi';

/** "Active" keeps the states that still need work, "ALL" shows every state, anything else is one state. */
export type StateFilter = 'ACTIVE' | 'ALL' | ResolutionState;

export const STATE_FILTERS: readonly StateFilter[] = ['ACTIVE', 'ALL', ...RESOLUTION_STATES];

export function isStateFilter(value: string | null): value is StateFilter {
  return value !== null && (STATE_FILTERS as readonly string[]).includes(value);
}

/** i18n key of a state filter chip. */
export function stateFilterLabelKey(filter: StateFilter): string {
  return filter === 'ACTIVE' || filter === 'ALL'
    ? `memberResolutions.filter.${filter}`
    : `tasks.resolutions.state.${filter}`;
}

/** Backend query (without the page) for a role and a state filter. */
export function toMemberResolutionQuery(role: MemberResolutionRole, filter: StateFilter): Omit<MemberResolutionQuery, 'page'> {
  if (filter === 'ACTIVE') return { role, active: true };
  if (filter === 'ALL') return { role };
  return { role, state: filter };
}

/** Whether the profile shows a highlighted (red) counter: only the overdue one, and only when it is not zero. */
export function isOverdueHighlighted(overdue: number): boolean {
  return overdue > 0;
}
