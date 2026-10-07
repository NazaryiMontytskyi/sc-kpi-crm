package com.kpi.studentcouncil.crm.identity.internal;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.LocaleResolver;

import tools.jackson.databind.json.JsonMapper;

/**
 * Writes RFC 7807 problems for responses produced inside the servlet filter chain (401, 403), where
 * {@code GlobalExceptionHandler} is not involved. Follows the kernel conventions: {@code type =
 * urn:crm:problem:<code>}, localized {@code title}/{@code detail} from {@code problem.<code>.title|detail},
 * stable {@code code} property; locale from the kernel locale resolver (saved user locale, Accept-Language, uk).
 */
class SecurityProblemWriter {

	private final MessageSource messages;

	private final LocaleResolver localeResolver;

	private final JsonMapper jsonMapper;

	SecurityProblemWriter(MessageSource messages, LocaleResolver localeResolver, JsonMapper jsonMapper) {
		this.messages = messages;
		this.localeResolver = localeResolver;
		this.jsonMapper = jsonMapper;
	}

	void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code)
			throws IOException {
		Locale locale = localeResolver.resolveLocale(request);
		String title = messages.getMessage("problem." + code + ".title", null, status.getReasonPhrase(), locale);
		String detail = messages.getMessage("problem." + code + ".detail", null, title, locale);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("type", URI.create("urn:crm:problem:" + code.toLowerCase(Locale.ROOT).replace('_', '-')).toString());
		body.put("title", title);
		body.put("status", status.value());
		body.put("detail", detail);
		body.put("code", code);
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.setHeader("Content-Language", locale.toLanguageTag());
		jsonMapper.writeValue(response.getOutputStream(), body);
	}

}
