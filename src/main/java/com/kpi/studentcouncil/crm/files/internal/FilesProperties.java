package com.kpi.studentcouncil.crm.files.internal;

import java.nio.file.Path;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * File storage settings ({@code crm.files.*}).
 *
 * @param root             directory of the local storage (mount a Docker volume here)
 * @param maxSize          maximum size of one file; Spring multipart limits are derived from it
 * @param allowedMimeTypes content types accepted after content sniffing (extend/shrink per environment)
 */
@ConfigurationProperties("crm.files")
public record FilesProperties(@DefaultValue("./data/files") Path root, @DefaultValue("20MB") DataSize maxSize,
		@DefaultValue({ "application/pdf", "image/png", "image/jpeg", "image/gif", "image/webp", "text/plain",
				"text/csv", "application/msword",
				"application/vnd.openxmlformats-officedocument.wordprocessingml.document",
				"application/vnd.ms-excel",
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
				"application/vnd.ms-powerpoint",
				"application/vnd.openxmlformats-officedocument.presentationml.presentation",
				"application/vnd.oasis.opendocument.text", "application/vnd.oasis.opendocument.spreadsheet",
				"application/vnd.oasis.opendocument.presentation", "application/zip" }) List<String> allowedMimeTypes) {
}
