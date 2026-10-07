package com.kpi.studentcouncil.crm.files.internal;

import java.net.URI;
import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Maps the container's multipart size error to the same {@code FILE_TOO_LARGE} problem the service produces, so
 * clients never see a raw container error. Runs before the shared handler.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class FilesExceptionHandler {

	private final MessageSource messages;
	private final FilesProperties properties;

	FilesExceptionHandler(MessageSource messages, FilesProperties properties) {
		this.messages = messages;
		this.properties = properties;
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ProblemDetail> handleTooLarge(MaxUploadSizeExceededException ex) {
		Locale locale = LocaleContextHolder.getLocale();
		String code = "FILE_TOO_LARGE";
		long megabytes = Math.max(1, (properties.maxSize().toBytes() + 1024 * 1024 - 1) / (1024 * 1024));
		String title = messages.getMessage("problem." + code + ".title", null, "Content Too Large", locale);
		String detail = messages.getMessage("problem." + code + ".detail", new Object[] { megabytes }, title, locale);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, detail);
		problem.setType(URI.create("urn:crm:problem:file-too-large"));
		problem.setTitle(title);
		problem.setProperty("code", code);
		return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.body(problem);
	}

}
