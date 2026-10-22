import { api, type components } from '../api/client';
import { toProblemError } from '../api/problem';
import type { RichDoc } from '../shared/RichTextField';
import { uploadAttachment } from './attachmentsApi';

type Schemas = components['schemas'];

export type CommentTargetType = NonNullable<Schemas['CommentResponse']['targetType']>;

export interface CommentAttachment {
  id: string;
  fileName: string;
  mimeType: string;
  size: number;
}

/** The generated types mark every field optional; this is the shape the UI relies on. */
export interface Comment {
  id: string;
  targetType: CommentTargetType;
  targetId: string;
  authorId: string;
  body: RichDoc;
  mentionUserIds: string[];
  attachments: CommentAttachment[];
  edited: boolean;
  createdAt: string;
}

export interface CommentInput {
  body: RichDoc;
  mentionUserIds: string[];
  attachmentIds: string[];
}

export interface CommentPage {
  items: Comment[];
  page: number;
  totalPages: number;
  totalElements: number;
}

export const COMMENTS_PAGE_SIZE = 20;

function toComment(c: Schemas['CommentResponse']): Comment {
  return {
    id: c.id ?? '',
    targetType: c.targetType ?? 'TASK',
    targetId: c.targetId ?? '',
    authorId: c.authorId ?? '',
    body: (c.body as RichDoc | undefined) ?? { type: 'doc' },
    mentionUserIds: c.mentionUserIds ?? [],
    attachments: (c.attachments ?? []).map((a) => ({
      id: a.id ?? '',
      fileName: a.fileName ?? '',
      mimeType: a.mimeType ?? '',
      size: a.size ?? 0,
    })),
    edited: c.edited === true,
    createdAt: c.createdAt ?? '',
  };
}

/** One page of comments, oldest first; archived comments are hidden by the backend. */
export async function listComments(targetType: CommentTargetType, targetId: string, page: number): Promise<CommentPage> {
  const { data, error, response } = await api.GET('/api/v1/comments', {
    params: { query: { page, size: COMMENTS_PAGE_SIZE, targetType, targetId } },
  });
  if (!data) throw toProblemError(response, error);
  return {
    items: (data.content ?? []).map(toComment),
    page: data.page ?? 0,
    totalPages: data.totalPages ?? 0,
    totalElements: data.totalElements ?? 0,
  };
}

export async function createComment(targetType: CommentTargetType, targetId: string, input: CommentInput): Promise<Comment> {
  const { data, error, response } = await api.POST('/api/v1/comments', { body: { targetType, targetId, ...input } });
  if (!data) throw toProblemError(response, error);
  return toComment(data);
}

export async function editComment(id: string, input: CommentInput): Promise<Comment> {
  const { data, error, response } = await api.PUT('/api/v1/comments/{id}', { params: { path: { id } }, body: input });
  if (!data) throw toProblemError(response, error);
  return toComment(data);
}

export async function archiveComment(id: string): Promise<Comment> {
  const { data, error, response } = await api.POST('/api/v1/comments/{id}/archive', { params: { path: { id } } });
  if (!data) throw toProblemError(response, error);
  return toComment(data);
}

/** Uploads a file through the files module and returns its id and metadata. */
export function uploadCommentFile(file: File, onProgress?: (fraction: number) => void): Promise<CommentAttachment> {
  return uploadAttachment(file, onProgress);
}
