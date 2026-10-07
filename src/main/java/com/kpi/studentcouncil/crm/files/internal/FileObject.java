package com.kpi.studentcouncil.crm.files.internal;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableEntity;

/**
 * Metadata of an uploaded file; the bytes live in the {@link FileStorage} under {@code storageKey}. Never hard-deleted:
 * archiving hides it from default reads and downloads while the bytes are kept.
 */
@Entity
@Table(name = "file_object")
class FileObject extends ArchivableEntity {

	@Column(name = "storage_key", nullable = false, updatable = false, length = 100)
	private String storageKey;

	@Column(name = "file_name", nullable = false, length = 255)
	private String fileName;

	@Column(name = "mime_type", nullable = false, length = 150)
	private String mimeType;

	@Column(name = "size_bytes", nullable = false)
	private long size;

	@Column(name = "uploaded_by", nullable = false, updatable = false)
	private UUID uploadedBy;

	protected FileObject() {
	}

	FileObject(String storageKey, String fileName, String mimeType, long size, UUID uploadedBy) {
		this.storageKey = storageKey;
		this.fileName = fileName;
		this.mimeType = mimeType;
		this.size = size;
		this.uploadedBy = uploadedBy;
	}

	String getStorageKey() {
		return storageKey;
	}

	String getFileName() {
		return fileName;
	}

	String getMimeType() {
		return mimeType;
	}

	long getSize() {
		return size;
	}

	UUID getUploadedBy() {
		return uploadedBy;
	}

}
