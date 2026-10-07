package com.kpi.studentcouncil.crm.files.internal;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.kpi.studentcouncil.crm.files.FileInfo;

/** API representation of an uploaded file. */
@Schema(name = "FileResponse")
public record FileResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Sanitized original file name") String fileName,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Content type detected from the bytes") String mimeType,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Size in bytes") long size,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID uploadedBy,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Upload time (UTC)") Instant createdAt) {

	static FileResponse of(FileInfo info) {
		return new FileResponse(info.id(), info.fileName(), info.mimeType(), info.size(), info.uploadedBy(),
				info.createdAt());
	}

}
