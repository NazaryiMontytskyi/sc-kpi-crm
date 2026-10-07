package com.kpi.studentcouncil.crm.files;

import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API of the files module. Other modules store a file id (never a path) and use this service to upload,
 * verify and read files. Files are archived, never hard-deleted; archived files are invisible here.
 * <p>
 * Uploading and reading require an authenticated caller only (no permission key in CONTEXT.md 5.2); callers that
 * expose files to users apply their own permission checks.
 */
public interface FileService {

	/**
	 * Validates and stores a file: sanitizes the name, enforces the size limit, detects the real content type from
	 * the bytes and checks it against the allow-list.
	 *
	 * @param fileName original file name (any path part is dropped)
	 * @param content  the bytes; read to the end but not closed
	 * @throws com.kpi.studentcouncil.crm.shared.error.DomainException with code {@code FILE_EMPTY},
	 *         {@code FILE_TOO_LARGE} or {@code FILE_TYPE_NOT_ALLOWED}
	 */
	FileInfo upload(String fileName, InputStream content);

	/** Metadata of an active (non-archived) file. */
	Optional<FileInfo> find(UUID id);

	/** Metadata of an active file, for attaching it by id; throws {@code NOT_FOUND} (404) if missing or archived. */
	FileInfo require(UUID id);

	/** Opens an active file; throws {@code NOT_FOUND} (404) if missing or archived. */
	FileContent open(UUID id);

	/** Archives a file (soft delete); its bytes are kept. Emits an ARCHIVE audit event. */
	void archive(UUID id);

}
