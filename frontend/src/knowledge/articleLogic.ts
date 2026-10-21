import { ApiProblemError, problemMessage } from '../api/problem';
import { collectFileIds } from './editor/blocks';
import type { Article, ArticleInput, RichDoc } from './knowledgeApi';

export const MAX_TITLE_LENGTH = 300;
export const MAX_TAG_LENGTH = 50;
export const MAX_TAGS = 20;

export function isVersionConflict(error: unknown): boolean {
  return error instanceof ApiProblemError && error.status === 409 && error.code === 'ARTICLE_VERSION_CONFLICT';
}

/** i18n key for a failed knowledge action. */
export function articleErrorKey(error: unknown): string {
  if (error instanceof ApiProblemError && error.status === 403 && !error.code) return 'problems.FORBIDDEN';
  return problemMessage(error).key;
}

/** Lower-cased, trimmed, de-duplicated tags (the backend normalizes the same way); blank ones are dropped. */
export function normalizeTags(tags: readonly string[]): string[] {
  const out: string[] = [];
  for (const raw of tags) {
    const tag = raw.trim().toLowerCase();
    if (tag !== '' && !out.includes(tag)) out.push(tag);
  }
  return out;
}

export function validateTitle(title: string): boolean {
  const t = title.trim();
  return t.length > 0 && t.length <= MAX_TITLE_LENGTH;
}

export interface ArticleFormState {
  title: string;
  content: RichDoc;
  tags: string[];
  restricted: boolean;
}

/** Request body of create/update; attachments are derived from the files used in the content. */
export function toArticleInput(form: ArticleFormState): ArticleInput {
  return {
    title: form.title.trim(),
    content: form.content,
    tags: normalizeTags(form.tags),
    restricted: form.restricted,
    attachmentIds: collectFileIds(form.content),
  };
}

export function formFromArticle(a: Pick<Article, 'title' | 'content' | 'tags' | 'restricted'>): ArticleFormState {
  return { title: a.title, content: a.content, tags: [...a.tags], restricted: a.restricted };
}

// ---- local drafts: unsaved work survives a reload or a failed save -------------------------

export interface Draft {
  baseVersion: number;
  form: ArticleFormState;
  savedAt: number;
}

export const draftKey = (articleId: string) => `kb-draft:${articleId}`;

export function saveDraft(storage: Pick<Storage, 'setItem'>, articleId: string, draft: Draft): void {
  try {
    storage.setItem(draftKey(articleId), JSON.stringify(draft));
  } catch {
    // Storage full or unavailable: drafts are a convenience, never block editing.
  }
}

export function loadDraft(storage: Pick<Storage, 'getItem'>, articleId: string): Draft | null {
  try {
    const raw = storage.getItem(draftKey(articleId));
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<Draft>;
    if (typeof parsed.baseVersion !== 'number' || !parsed.form || typeof parsed.form.title !== 'string') return null;
    return parsed as Draft;
  } catch {
    return null;
  }
}

export function clearDraft(storage: Pick<Storage, 'removeItem'>, articleId: string): void {
  try {
    storage.removeItem(draftKey(articleId));
  } catch {
    // ignore
  }
}

/** A draft is offered back only when it was started from the version now loaded and differs from it. */
export function draftIsUsable(draft: Draft | null, article: Pick<Article, 'version' | 'title' | 'content' | 'tags' | 'restricted'>): draft is Draft {
  if (!draft || draft.baseVersion !== article.version) return false;
  return JSON.stringify(draft.form) !== JSON.stringify(formFromArticle(article));
}
