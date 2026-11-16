package com.kpi.studentcouncil.crm.tasks.internal;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Builds the RFC 7807 problem of {@link SubtaskDeadlineConflictException} like the shared handler does (same
 * {@code type}, localized {@code title}/{@code detail}, {@code code}) and adds the machine-readable list
 * {@code conflictingSubtasks} ({@code id}, {@code title}, {@code deadline}) so the client can name them. Runs before the
 * shared handler.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class TasksExceptionHandler {

	private final MessageSource messages;

	TasksExceptionHandler(MessageSource messages) {
		this.messages = messages;
	}

	@ExceptionHandler(SubtaskDeadlineConflictException.class)
	ResponseEntity<ProblemDetail> handleDeadlineConflict(SubtaskDeadlineConflictException ex) {
		Locale locale = LocaleContextHolder.getLocale();
		String code = ex.getCode();
		String title = messages.getMessage("problem." + code + ".title", null, ex.getStatus().getReasonPhrase(),
				locale);
		String detail = messages.getMessage("problem." + code + ".detail", ex.getArgs(), title, locale);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), detail);
		problem.setType(URI.create("urn:crm:problem:" + code.toLowerCase(Locale.ROOT).replace('_', '-')));
		problem.setTitle(title);
		problem.setProperty("code", code);
		List<Map<String, Object>> subtasks = ex.getConflicting().stream().map(s -> {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("id", s.id().toString());
			item.put("title", s.title());
			item.put("deadline", s.deadline().toString());
			return item;
		}).toList();
		problem.setProperty("conflictingSubtasks", subtasks);
		return ResponseEntity.status(ex.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
	}

}
