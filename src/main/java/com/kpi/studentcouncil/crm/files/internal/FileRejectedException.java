package com.kpi.studentcouncil.crm.files.internal;

import org.springframework.http.HttpStatus;

import com.kpi.studentcouncil.crm.shared.error.DomainException;

/** An upload was rejected by a configured rule: too large (413) or content type not allowed (415). */
class FileRejectedException extends DomainException {

	private FileRejectedException(String code, HttpStatus status, Object... args) {
		super(code, status, args);
	}

	/** Problem code {@code FILE_TOO_LARGE}; the detail message receives the limit in megabytes (rounded up). */
	static FileRejectedException tooLarge(long maxBytes) {
		return new FileRejectedException("FILE_TOO_LARGE", HttpStatus.CONTENT_TOO_LARGE,
				Math.max(1, (maxBytes + 1024 * 1024 - 1) / (1024 * 1024)));
	}

	/** Problem code {@code FILE_TYPE_NOT_ALLOWED}; the detail message receives the detected type. */
	static FileRejectedException typeNotAllowed(String detectedType) {
		return new FileRejectedException("FILE_TYPE_NOT_ALLOWED", HttpStatus.UNSUPPORTED_MEDIA_TYPE, detectedType);
	}

}
