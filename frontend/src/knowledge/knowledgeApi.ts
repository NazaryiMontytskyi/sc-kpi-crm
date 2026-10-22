import { api, type components } from '../api/client';
import { toProblemError } from '../api/problem';

type Schemas = components['schemas'];

export type RichDoc = Record<string, unknown>;

export const KB_VIEW = 'kb.view';
export const KB_EDIT = 'kb.edit';
export const KB_ARCHIVE = 'kb.archive';
export const KB_RESTRICTED_VIEW = 'kb.restricted.view';

export interface ArticleAttachment {
  id: string;
  fileName: string;
  mimeType: string;
  size: number;
}

export interface ArticleSummary {
  id: string;
  title: string;
  parentArticleId: string | null;
  restricted: boolean;
  version: number;
  tags: string[];
  archived: boolean;
  updatedAt: string;
}

export interface Article extends ArticleSummary {
  content: RichDoc;
  attachments: ArticleAttachment[];
  ancestors: { id: string; title: string }[];
}

export interface TreeNode {
  id: string;
  title: string;
  restricted: boolean;
  children: TreeNode[];
}

export interface TagCount {
  tag: string;
  count: number;
}

export interface ArticleInput {
  title: string;
  content: RichDoc;
  tags: string[];
  restricted: boolean;
  attachmentIds: string[];
}

export const EMPTY_DOC: RichDoc = { type: 'doc', content: [{ type: 'paragraph' }] };

function toSummary(a: Schemas['ArticleSummaryResponse']): ArticleSummary {
  return {
    id: a.id ?? '',
    title: a.title ?? '',
    parentArticleId: a.parentArticleId ?? null,
    restricted: a.restricted === true,
    version: a.version ?? 1,
    tags: a.tags ?? [],
    archived: a.archived === true,
    updatedAt: a.updatedAt ?? '',
  };
}

function toArticle(a: Schemas['ArticleResponse']): Article {
  return {
    ...toSummary(a),
    content: (a.content as RichDoc | undefined) ?? EMPTY_DOC,
    attachments: (a.attachments ?? []).map((f) => ({
      id: f.id ?? '',
      fileName: f.fileName ?? '',
      mimeType: f.mimeType ?? '',
      size: f.size ?? 0,
    })),
    ancestors: (a.ancestors ?? []).map((x) => ({ id: x.id ?? '', title: x.title ?? '' })),
  };
}

function toNode(n: Schemas['TreeNodeResponse']): TreeNode {
  return { id: n.id ?? '', title: n.title ?? '', restricted: n.restricted === true, children: (n.children ?? []).map(toNode) };
}

export async function getArticleTree(): Promise<TreeNode[]> {
  const { data, error, response } = await api.GET('/api/v1/articles/tree');
  if (!data) throw toProblemError(response, error);
  return data.map(toNode);
}

export async function getTags(): Promise<TagCount[]> {
  const { data, error, response } = await api.GET('/api/v1/articles/tags');
  if (!data) throw toProblemError(response, error);
  return data.map((t) => ({ tag: t.tag ?? '', count: t.count ?? 0 }));
}

export async function listArticles(tag: string | undefined, includeArchived: boolean): Promise<ArticleSummary[]> {
  const { data, error, response } = await api.GET('/api/v1/articles', {
    params: { query: { page: 0, size: 100, sort: 'title,asc', tag, includeArchived: includeArchived || undefined } },
  });
  if (!data) throw toProblemError(response, error);
  return (data.content ?? []).map(toSummary);
}

export async function getArticle(id: string): Promise<Article> {
  const { data, error, response } = await api.GET('/api/v1/articles/{id}', { params: { path: { id } } });
  if (!data) throw toProblemError(response, error);
  return toArticle(data);
}

export async function createArticle(input: ArticleInput, parentArticleId: string | null): Promise<Article> {
  const { data, error, response } = await api.POST('/api/v1/articles', {
    body: { ...input, parentArticleId: parentArticleId ?? undefined },
  });
  if (!data) throw toProblemError(response, error);
  return toArticle(data);
}

export async function updateArticle(id: string, expectedVersion: number, input: ArticleInput): Promise<Article> {
  const { data, error, response } = await api.PUT('/api/v1/articles/{id}', {
    params: { path: { id } },
    body: { expectedVersion, ...input },
  });
  if (!data) throw toProblemError(response, error);
  return toArticle(data);
}

export async function moveArticle(id: string, parentArticleId: string | null): Promise<Article> {
  const { data, error, response } = await api.POST('/api/v1/articles/{id}/move', {
    params: { path: { id } },
    body: { parentArticleId: parentArticleId ?? undefined },
  });
  if (!data) throw toProblemError(response, error);
  return toArticle(data);
}

export async function archiveArticle(id: string): Promise<Article> {
  const { data, error, response } = await api.POST('/api/v1/articles/{id}/archive', { params: { path: { id } } });
  if (!data) throw toProblemError(response, error);
  return toArticle(data);
}

export async function restoreArticle(id: string): Promise<Article> {
  const { data, error, response } = await api.POST('/api/v1/articles/{id}/restore', { params: { path: { id } } });
  if (!data) throw toProblemError(response, error);
  return toArticle(data);
}

/** Uploads a file through the files module (multipart part `file`). */
export async function uploadArticleFile(file: File): Promise<ArticleAttachment> {
  const { data, error, response } = await api.POST('/api/v1/files', {
    // The generated type models the binary part as a string; the serializer sends the real File.
    body: { file: file as unknown as string },
    bodySerializer: () => {
      const form = new FormData();
      form.append('file', file);
      return form;
    },
  });
  if (!data?.id) throw toProblemError(response, error);
  return { id: data.id, fileName: data.fileName ?? file.name, mimeType: data.mimeType ?? file.type, size: data.size ?? file.size };
}

export interface ArticleVersionSummary {
  versionNumber: number;
  title: string;
  editedAt: string;
}

export interface ArticleVersion extends ArticleVersionSummary {
  content: RichDoc;
}

export async function listArticleVersions(id: string): Promise<ArticleVersionSummary[]> {
  const { data, error, response } = await api.GET('/api/v1/articles/{id}/versions', {
    params: { path: { id }, query: { page: 0, size: 100, sort: 'versionNumber,desc' } },
  });
  if (!data) throw toProblemError(response, error);
  return (data.content ?? []).map((v) => ({ versionNumber: v.versionNumber ?? 0, title: v.title ?? '', editedAt: v.editedAt ?? '' }));
}

export async function getArticleVersion(id: string, versionNumber: number): Promise<ArticleVersion> {
  const { data, error, response } = await api.GET('/api/v1/articles/{id}/versions/{versionNumber}', {
    params: { path: { id, versionNumber } },
  });
  if (!data) throw toProblemError(response, error);
  return {
    versionNumber: data.versionNumber ?? versionNumber,
    title: data.title ?? '',
    editedAt: data.editedAt ?? '',
    content: (data.content as RichDoc | undefined) ?? EMPTY_DOC,
  };
}

/** Makes an old version the current one (a new version is created); tags, attachments and the restricted flag stay. */
export async function restoreArticleVersion(id: string, versionNumber: number, expectedVersion?: number): Promise<Article> {
  const { data, error, response } = await api.POST('/api/v1/articles/{id}/versions/{versionNumber}/restore', {
    params: { path: { id, versionNumber } },
    body: expectedVersion === undefined ? undefined : { expectedVersion },
  });
  if (!data) throw toProblemError(response, error);
  return toArticle(data);
}
