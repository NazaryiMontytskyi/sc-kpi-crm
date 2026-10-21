import { CSRF_COOKIE, CSRF_HEADER, readCookie } from '../api/csrf';
import { ApiProblemError, fieldErrorsOf, problemCodeOf } from '../api/problem';
import { emitSessionEvent } from '../api/sessionEvents';
import { env } from '../config/env';
import type { AttachmentInfo } from './attachmentLogic';

/**
 * Uploads a file to the files module with progress reporting (fetch cannot report upload progress, so XHR is used).
 * Rejections are thrown as {@link ApiProblemError} carrying the problem code, like every other API call.
 */
export function uploadAttachment(file: File, onProgress?: (fraction: number) => void): Promise<AttachmentInfo> {
  return new Promise<AttachmentInfo>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', `${env.apiBaseUrl}/api/v1/files`);
    xhr.withCredentials = true;
    xhr.setRequestHeader('Accept', 'application/json');
    const token = readCookie(CSRF_COOKIE);
    if (token) xhr.setRequestHeader(CSRF_HEADER, token);

    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable && e.total > 0) onProgress?.(Math.min(1, e.loaded / e.total));
    };
    xhr.onerror = () => reject(new ApiProblemError(0, undefined));
    xhr.onabort = () => reject(new ApiProblemError(0, undefined));
    xhr.onload = () => {
      let body: unknown;
      try {
        body = JSON.parse(xhr.responseText);
      } catch {
        body = undefined;
      }
      if (xhr.status >= 200 && xhr.status < 300 && typeof body === 'object' && body !== null && 'id' in body) {
        const f = body as { id: string; fileName?: string; mimeType?: string; size?: number };
        onProgress?.(1);
        resolve({ id: f.id, fileName: f.fileName ?? file.name, mimeType: f.mimeType ?? file.type, size: f.size ?? file.size });
        return;
      }
      if (xhr.status === 401) emitSessionEvent('unauthorized');
      reject(new ApiProblemError(xhr.status, problemCodeOf(body), undefined, fieldErrorsOf(body)));
    };

    const form = new FormData();
    form.append('file', file);
    xhr.send(form);
  });
}
