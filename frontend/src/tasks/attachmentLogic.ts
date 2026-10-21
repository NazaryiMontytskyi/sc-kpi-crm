import { ApiProblemError, problemMessage } from '../api/problem';

/** Backend limit of attachments on one task (TASK_TOO_MANY_ATTACHMENTS). */
export const MAX_TASK_ATTACHMENTS = 20;

/** Hint for the file picker; images include the camera on phones. The backend sniffs the real type and decides. */
export const ATTACH_ACCEPT =
  'image/*,application/pdf,text/plain,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.odt,.ods,.odp,.zip';

export interface AttachmentInfo {
  id: string;
  fileName: string;
  mimeType: string;
  size: number;
}

/** Thumbnails are shown only for formats a browser renders everywhere. */
const THUMBNAIL_TYPES = ['image/png', 'image/jpeg', 'image/gif', 'image/webp'];

export function isThumbnailable(mimeType: string): boolean {
  return THUMBNAIL_TYPES.includes(mimeType.toLowerCase());
}

/** Human-readable size with binary units, e.g. `1.5 MB`. Unit labels are language-neutral abbreviations. */
export function formatFileSize(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes < 0) return '';
  if (bytes < 1024) return `${bytes} B`;
  const units = ['KB', 'MB', 'GB'];
  let value = bytes / 1024;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${value >= 10 ? Math.round(value) : Math.round(value * 10) / 10} ${units[unit]}`;
}

/**
 * i18n key for a failed upload or attach. File rejections get attachment-specific wording (the shared message
 * for an unsupported type talks about avatars); everything else uses the generic problem messages.
 */
export function attachmentErrorKey(error: unknown): string {
  if (error instanceof ApiProblemError) {
    if (error.code === 'FILE_TYPE_NOT_ALLOWED') return 'tasks.attachments.typeNotAllowed';
    if (error.status === 403) return 'problems.FORBIDDEN';
  }
  return problemMessage(error).key;
}

/** Ids of the current attachments plus new ones, without duplicates. */
export function addAttachmentIds(current: readonly string[], added: readonly string[]): string[] {
  return [...new Set([...current, ...added])];
}

/** How many more files may still be attached. */
export function remainingSlots(current: number, limit: number): number {
  return Math.max(0, limit - current);
}
