import { ApiProblemError, problemMessage } from '../api/problem';
import { hasPermission, type CurrentUser } from '../auth/CurrentUser';
import { isRealDate } from './taskLogic';
import type { Resolution, ResolutionState } from './resolutionsApi';

export const RESOLUTIONS_CREATE = 'resolutions.create';
export const RESOLUTIONS_REDIRECT = 'resolutions.redirect';

export type ResolutionAction = 'start' | 'resume' | 'submit' | 'redirect' | 'accept' | 'rework' | 'cancel';

const FINAL_STATES: readonly ResolutionState[] = ['ACCEPTED', 'REDIRECTED', 'CANCELED'];

export function isFinalState(state: ResolutionState): boolean {
  return FINAL_STATES.includes(state);
}

/**
 * Actions to offer the current user on a resolution, mirroring the state machine (CONTEXT.md §5.3):
 * executor: NEW -> start, IN_PROGRESS -> submit / redirect (needs resolutions.redirect), REWORK -> resume;
 * author: SUBMITTED -> accept / rework, any non-final state -> cancel.
 * Convenience only; the backend decides and rejections are surfaced.
 */
export function availableActions(
  user: CurrentUser | null,
  resolution: Pick<Resolution, 'authorId' | 'executorId' | 'state'>,
): ResolutionAction[] {
  if (!user) return [];
  const actions: ResolutionAction[] = [];
  if (user.id === resolution.executorId) {
    if (resolution.state === 'NEW') actions.push('start');
    if (resolution.state === 'REWORK') actions.push('resume');
    if (resolution.state === 'IN_PROGRESS') {
      actions.push('submit');
      if (hasPermission(user, RESOLUTIONS_REDIRECT)) actions.push('redirect');
    }
  }
  if (user.id === resolution.authorId) {
    if (resolution.state === 'SUBMITTED') actions.push('accept', 'rework');
    if (!isFinalState(resolution.state)) actions.push('cancel');
  }
  return actions;
}

/** Whether to offer "issue resolution": permission held and the task still active. */
export function canIssueResolution(user: CurrentUser | null, taskArchived: boolean): boolean {
  return !taskArchived && hasPermission(user, RESOLUTIONS_CREATE);
}

export interface ResolutionFormValues {
  executorId: string | null;
  instruction: string;
  dueDate: string;
}

export type ResolutionFormErrors = Partial<Record<'executorId' | 'instruction' | 'dueDate', string>>;

/** Returns i18n keys per invalid field (issue and redirect forms). `today` is `YYYY-MM-DD` in Kyiv. */
export function validateResolutionForm(v: ResolutionFormValues, today: string): ResolutionFormErrors {
  const errors: ResolutionFormErrors = {};
  if (!v.executorId) errors.executorId = 'tasks.resolutions.validation.executorRequired';
  if (v.instruction.trim() === '') errors.instruction = 'tasks.resolutions.validation.instructionRequired';
  if (v.dueDate === '') errors.dueDate = 'tasks.resolutions.validation.dueRequired';
  else if (!isRealDate(v.dueDate)) errors.dueDate = 'tasks.resolutions.validation.dueInvalid';
  else if (v.dueDate < today) errors.dueDate = 'tasks.resolutions.validation.dueInPast';
  return errors;
}

/** Required free-text of submit (completion note) and rework (comment). */
export function isNoteValid(text: string): boolean {
  return text.trim() !== '';
}

export const STATE_COLORS: Record<ResolutionState, string> = {
  NEW: 'gray',
  IN_PROGRESS: 'cyan',
  SUBMITTED: 'yellow',
  REWORK: 'orange',
  ACCEPTED: 'green',
  REDIRECTED: 'grape',
  CANCELED: 'dark',
};

/** i18n key for a failed resolution action; a bare 403 reads as "no permission". */
export function resolutionErrorKey(error: unknown): string {
  if (error instanceof ApiProblemError && error.status === 403 && !error.code) return 'problems.FORBIDDEN';
  return problemMessage(error).key;
}

/** i18n key of a soft warning returned by redirect, or null for unknown ones. */
export function resolutionWarningKey(code: string): string | null {
  return code === 'RESOLUTION_CHILD_DUE_AFTER_PARENT' ? `problems.${code}` : null;
}
