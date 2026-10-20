import { ApiProblemError, problemMessage } from '../api/problem';
import type { CurrentUser } from '../auth/CurrentUser';
import type { RichDoc } from '../shared/RichTextField';

export const MAX_COMMENT_ATTACHMENTS = 10;

/** Ids of the `mention` nodes of a TipTap document, in document order, without duplicates. */
export function extractMentionIds(doc: RichDoc | null | undefined): string[] {
  const ids: string[] = [];
  const walk = (node: unknown): void => {
    if (typeof node !== 'object' || node === null) return;
    const n = node as { type?: string; attrs?: { id?: unknown }; content?: unknown[] };
    if (n.type === 'mention' && typeof n.attrs?.id === 'string' && !ids.includes(n.attrs.id)) ids.push(n.attrs.id);
    (n.content ?? []).forEach(walk);
  };
  walk(doc);
  return ids;
}

/**
 * Whether to offer edit and delete: the author or an Admin. Convenience only; the backend `CommentPolicy` decides
 * and a 403 is shown gracefully.
 */
export function canModifyComment(user: CurrentUser | null, comment: { authorId: string }): boolean {
  return !!user && (user.isAdmin || user.id === comment.authorId);
}

/** A comment can be sent with some text or mention, or with at least one attachment. */
export function canSubmitComment(empty: boolean, attachmentCount: number): boolean {
  return !empty || attachmentCount > 0;
}

/** Members matching a search text (name or telegram), at most `limit`, for the mention picker. */
export function filterMentionCandidates<T extends { lastName: string; firstName: string; middleName: string; telegram: string }>(
  members: readonly T[],
  query: string,
  limit = 8,
): T[] {
  const q = query.trim().toLowerCase();
  const matches = q === '' ? members : members.filter((m) => `${m.lastName} ${m.firstName} ${m.middleName} ${m.telegram}`.toLowerCase().includes(q));
  return matches.slice(0, limit);
}

/** i18n key for a failed comment action; a 403 reads as "no permission" (the backend stays authoritative). */
export function commentErrorKey(error: unknown): string {
  if (error instanceof ApiProblemError && error.status === 403 && error.code === 'COMMENT_EDIT_FORBIDDEN') {
    return 'problems.COMMENT_EDIT_FORBIDDEN';
  }
  if (error instanceof ApiProblemError && error.status === 403) return 'problems.FORBIDDEN';
  return problemMessage(error).key;
}
