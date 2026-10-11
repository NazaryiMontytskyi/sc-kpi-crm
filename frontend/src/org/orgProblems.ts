import { ApiProblemError, problemMessage } from '../api/problem';

/**
 * i18n key for a failed org action. A 403 always reads as "no permission" (the backend decides, the UI only
 * reacts gracefully); an unknown problem falls back to the generic message.
 */
export function orgErrorKey(error: unknown): string {
  if (error instanceof ApiProblemError && error.status === 403) return 'problems.FORBIDDEN';
  return problemMessage(error).key;
}
