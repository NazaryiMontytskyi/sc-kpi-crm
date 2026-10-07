package com.kpi.studentcouncil.crm.shared.error;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps every exception to an RFC 7807 {@link ProblemDetail} ({@code application/problem+json}) with
 * {@code type}, {@code title}, {@code status}, {@code detail} and a stable {@code code} property.
 * Title and detail are localized from {@code messages_*.properties} using the request locale.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	static final String CODE = "code";
	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private final MessageSource messages;

	public GlobalExceptionHandler(MessageSource messages) {
		this.messages = messages;
	}

	@ExceptionHandler(DomainException.class)
	ResponseEntity<Object> handleDomain(DomainException ex) {
		return respond(build(ex.getStatus(), ex.getCode(), ex.getArgs()), null);
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex) {
		return respond(build(HttpStatus.FORBIDDEN, "FORBIDDEN", null), null);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex) {
		List<Map<String, String>> errors = ex.getConstraintViolations().stream()
				.map(v -> Map.of("field", v.getPropertyPath().toString(), "message", v.getMessage()))
				.toList();
		return respond(validationProblem(errors), null);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<Object> handleDataIntegrity(DataIntegrityViolationException ex) {
		log.warn("Data integrity violation", ex);
		return respond(build(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION", null), null);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<Object> handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		return respond(build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", null), null);
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<Map<String, String>> errors = new ArrayList<>();
		ex.getBindingResult().getGlobalErrors()
				.forEach(e -> errors.add(Map.of("field", e.getObjectName(), "message", text(e.getDefaultMessage()))));
		for (FieldError e : ex.getBindingResult().getFieldErrors()) {
			errors.add(Map.of("field", e.getField(), "message", text(e.getDefaultMessage())));
		}
		return respond(validationProblem(errors), headers);
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Locale locale = LocaleContextHolder.getLocale();
		List<Map<String, String>> errors = new ArrayList<>();
		ex.getParameterValidationResults().forEach(result -> {
			String field = result.getMethodParameter().getParameterName();
			result.getResolvableErrors().forEach(error -> errors.add(
					Map.of("field", field == null ? "" : field, "message", messages.getMessage(error, locale))));
		});
		return respond(validationProblem(errors), headers);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return respond(build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", null), headers);
	}

	/** Fallback for all remaining Spring MVC exceptions (404, 405, 415, type mismatch, ...). */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		if (body instanceof ProblemDetail pd && pd.getProperties() != null && pd.getProperties().containsKey(CODE)) {
			return super.handleExceptionInternal(ex, body, headers, statusCode, request);
		}
		return respond(build(statusCode, codeFor(statusCode), null), headers);
	}

	private ResponseEntity<Object> respond(ProblemDetail problem, HttpHeaders headers) {
		ResponseEntity.BodyBuilder builder = ResponseEntity.status(problem.getStatus());
		if (headers != null) {
			builder.headers(headers);
		}
		return builder.body(problem);
	}

	private ProblemDetail validationProblem(List<Map<String, String>> errors) {
		ProblemDetail problem = build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", null);
		problem.setProperty("errors", errors);
		return problem;
	}

	/** Builds the problem for a stable code; title/detail keys are {@code problem.<code>.title|detail}. */
	ProblemDetail build(HttpStatusCode status, String code, Object[] args) {
		Locale locale = LocaleContextHolder.getLocale();
		String fallbackTitle = status instanceof HttpStatus hs ? hs.getReasonPhrase() : code;
		String title = messages.getMessage("problem." + code + ".title", null, fallbackTitle, locale);
		String detail = messages.getMessage("problem." + code + ".detail", args, title, locale);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setType(URI.create("urn:crm:problem:" + code.toLowerCase(Locale.ROOT).replace('_', '-')));
		problem.setTitle(title);
		problem.setProperty(CODE, code);
		return problem;
	}

	static String codeFor(HttpStatusCode status) {
		return switch (status.value()) {
			case 400 -> "BAD_REQUEST";
			case 401 -> "UNAUTHORIZED";
			case 403 -> "FORBIDDEN";
			case 404 -> "NOT_FOUND";
			case 405 -> "METHOD_NOT_ALLOWED";
			case 406 -> "NOT_ACCEPTABLE";
			case 409 -> "CONFLICT";
			case 415 -> "UNSUPPORTED_MEDIA_TYPE";
			case 500 -> "INTERNAL_ERROR";
			default -> status.is4xxClientError() ? "BAD_REQUEST" : "INTERNAL_ERROR";
		};
	}

	private static String text(String value) {
		return value == null ? "" : value;
	}

}
