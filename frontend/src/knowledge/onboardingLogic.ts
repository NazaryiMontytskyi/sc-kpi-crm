import { ApiProblemError, problemMessage } from '../api/problem';
import type { OnboardingItem } from './onboardingApi';

/** i18n key for a failed onboarding action. */
export function onboardingErrorKey(error: unknown): string {
  if (error instanceof ApiProblemError && error.status === 403 && !error.code) return 'problems.FORBIDDEN';
  return problemMessage(error).key;
}

/** Moves the element at `from` to `to`; out-of-range requests return an unchanged copy. */
export function moveItem<T>(items: readonly T[], from: number, to: number): T[] {
  const next = [...items];
  if (from < 0 || from >= next.length || to < 0 || to >= next.length || from === to) return next;
  const [moved] = next.splice(from, 1);
  next.splice(to, 0, moved);
  return next;
}

/** Appends an id unless it is already in the list. */
export function appendUnique(ids: readonly string[], id: string): string[] {
  return ids.includes(id) ? [...ids] : [...ids, id];
}

export function removeAt<T>(items: readonly T[], index: number): T[] {
  return items.filter((_, i) => i !== index);
}

export function readProgress(items: readonly OnboardingItem[]): { read: number; total: number } {
  return { read: items.filter((i) => i.read).length, total: items.length };
}

export function sameIds(a: readonly string[], b: readonly string[]): boolean {
  return a.length === b.length && a.every((x, i) => x === b[i]);
}
