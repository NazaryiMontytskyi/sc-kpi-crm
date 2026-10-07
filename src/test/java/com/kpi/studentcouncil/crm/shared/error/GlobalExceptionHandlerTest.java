package com.kpi.studentcouncil.crm.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler(messageSource());

	private static ResourceBundleMessageSource messageSource() {
		ResourceBundleMessageSource source = new ResourceBundleMessageSource();
		source.setBasename("messages");
		source.setDefaultEncoding(StandardCharsets.UTF_8.name());
		source.setFallbackToSystemLocale(false);
		return source;
	}

	@AfterEach
	void resetLocale() {
		LocaleContextHolder.resetLocaleContext();
	}

	@Test
	void mapsDomainExceptionToLocalizedProblemWithStableCode() {
		LocaleContextHolder.setLocale(Locale.of("uk"));

		ResponseEntity<Object> response = handler.handleDomain(new ConflictException("ALREADY_ARCHIVED"));
		ProblemDetail problem = (ProblemDetail) response.getBody();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(problem.getStatus()).isEqualTo(409);
		assertThat(problem.getProperties()).containsEntry("code", "ALREADY_ARCHIVED");
		assertThat(problem.getType().toString()).isEqualTo("urn:crm:problem:already-archived");
		assertThat(problem.getTitle()).isEqualTo("Уже в архіві");
		assertThat(problem.getDetail()).isEqualTo("Елемент уже архівований.");
	}

	@Test
	void sameCodeIsLocalizedToEnglish() {
		LocaleContextHolder.setLocale(Locale.of("en"));

		ProblemDetail problem = (ProblemDetail) handler.handleDomain(new ConflictException("ALREADY_ARCHIVED")).getBody();

		assertThat(problem.getTitle()).isEqualTo("Already archived");
		assertThat(problem.getProperties()).containsEntry("code", "ALREADY_ARCHIVED");
	}

	@Test
	void notFoundUsesStatus404() {
		LocaleContextHolder.setLocale(Locale.of("en"));

		ResponseEntity<Object> response = handler.handleDomain(new NotFoundException("Role", "x"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(((ProblemDetail) response.getBody()).getProperties()).containsEntry("code", "NOT_FOUND");
	}

	@Test
	void businessRuleIs422AndFormatsArguments() {
		LocaleContextHolder.setLocale(Locale.of("en"));

		ResponseEntity<Object> response = handler.handleDomain(new BusinessRuleException("INVALID_SORT_FIELD", "title"));

		assertThat(response.getStatusCode().value()).isEqualTo(422);
		assertThat(((ProblemDetail) response.getBody()).getDetail()).isEqualTo("Sorting by title is not supported.");
	}

	@Test
	void unknownCodeFallsBackToStatusReasonWithoutFailing() {
		LocaleContextHolder.setLocale(Locale.of("en"));

		ProblemDetail problem = handler.build(HttpStatus.CONFLICT, "SOME_UNTRANSLATED_CODE", null);

		assertThat(problem.getTitle()).isEqualTo("Conflict");
		assertThat(problem.getProperties()).containsEntry("code", "SOME_UNTRANSLATED_CODE");
	}

	@Test
	void unexpectedExceptionDoesNotLeakDetails() {
		LocaleContextHolder.setLocale(Locale.of("en"));

		ProblemDetail problem = (ProblemDetail) handler.handleUnexpected(new IllegalStateException("secret")).getBody();

		assertThat(problem.getStatus()).isEqualTo(500);
		assertThat(problem.getDetail()).doesNotContain("secret");
		assertThat(problem.getProperties()).containsEntry("code", "INTERNAL_ERROR");
	}

}
