package com.kpi.studentcouncil.crm.files.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import com.kpi.studentcouncil.crm.files.FileContent;
import com.kpi.studentcouncil.crm.files.FileInfo;
import com.kpi.studentcouncil.crm.files.FileService;
import com.kpi.studentcouncil.crm.shared.web.ApiV1;

/**
 * Upload and download of files. Both need an authenticated user and nothing more (CONTEXT.md 5.2 defines no file
 * permission); there is deliberately no delete endpoint: files are archived through {@link FileService} only.
 */
@ApiV1
@RequestMapping("/files")
@Tag(name = "Files")
class FilesController {

	private final FileService files;

	FilesController(FileService files) {
		this.files = files;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(summary = "Upload a file",
			description = "Multipart part `file`. The type is detected from the bytes and checked against the "
					+ "allow-list; the size is limited (default 20 MB).")
	@ApiResponse(responseCode = "201", description = "File stored")
	@ApiResponse(responseCode = "413", description = "FILE_TOO_LARGE",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "415", description = "FILE_TYPE_NOT_ALLOWED",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "422", description = "FILE_EMPTY",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	ResponseEntity<FileResponse> upload(@RequestPart("file") MultipartFile file) {
		FileInfo info;
		try (InputStream in = file.getInputStream()) {
			info = files.upload(file.getOriginalFilename(), in);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return ResponseEntity.status(HttpStatus.CREATED).location(URI.create("/api/v1/files/" + info.id()))
				.body(FileResponse.of(info));
	}

	@GetMapping("/{id}")
	@Operation(summary = "Download a file",
			description = "Streams the bytes. PDF and images are served inline, everything else as an attachment; "
					+ "archived files return 404.")
	@ApiResponse(responseCode = "200", description = "The file bytes",
			content = @Content(mediaType = "application/octet-stream", schema = @Schema(type = "string", format = "binary")))
	@ApiResponse(responseCode = "404", description = "NOT_FOUND (unknown or archived)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	ResponseEntity<Resource> download(@PathVariable UUID id) {
		FileContent content = files.open(id);
		FileInfo info = content.info();
		boolean inline = ContentDispositions.isInline(info.mimeType());
		Resource body = new SizedStreamResource(content.stream(), info.size(), info.fileName());

		ResponseEntity.BodyBuilder response = ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(info.mimeType()))
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDispositions.build(inline, info.fileName()))
				.header("X-Content-Type-Options", "nosniff")
				.cacheControl(CacheControl.noCache().cachePrivate());
		return response.body(body);
	}

	/** Stream with a known length so the response carries Content-Length. */
	private static final class SizedStreamResource extends InputStreamResource {

		private final long size;
		private final String name;

		SizedStreamResource(InputStream stream, long size, String name) {
			super(stream);
			this.size = size;
			this.name = name;
		}

		@Override
		public long contentLength() {
			return size;
		}

		@Override
		public String getFilename() {
			return name;
		}

	}

}
