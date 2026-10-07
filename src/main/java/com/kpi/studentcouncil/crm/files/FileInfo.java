package com.kpi.studentcouncil.crm.files;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata of a stored file, safe to hand to other modules (never exposes the storage key).
 *
 * @param id         file id, the value other modules store to attach the file
 * @param fileName   sanitized original file name (display only, never used as a storage path)
 * @param mimeType   content type detected from the bytes
 * @param size       size in bytes
 * @param uploadedBy id of the uploading user
 * @param createdAt  upload time (UTC)
 */
public record FileInfo(UUID id, String fileName, String mimeType, long size, UUID uploadedBy, Instant createdAt) {
}
