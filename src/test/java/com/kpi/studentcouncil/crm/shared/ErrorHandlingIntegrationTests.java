package com.kpi.studentcouncil.crm.shared;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.kpi.studentcouncil.crm.TestcontainersConfiguration;
import com.kpi.studentcouncil.crmtest.shared.KernelTestConfig;

@SpringBootTest(properties = { "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, KernelTestConfig.class })
@WithMockUser
class ErrorHandlingIntegrationTests {

	private static final String BASE = "/api/v1/test-kernel";

	@Autowired
	MockMvc mvc;

	@Test
	void notFoundIsProblemJsonLocalizedUkrainianByDefault() throws Exception {
		mvc.perform(get(BASE + "/not-found"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value("urn:crm:problem:not-found"))
				.andExpect(jsonPath("$.title").value("Не знайдено"))
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.detail").value("Запитаний ресурс не знайдено."))
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void acceptLanguageEnSwitchesLocale() throws Exception {
		mvc.perform(get(BASE + "/not-found").header(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Not found"))
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void unsupportedLanguageFallsBackToUkrainian() throws Exception {
		mvc.perform(get(BASE + "/not-found").header(HttpHeaders.ACCEPT_LANGUAGE, "de"))
				.andExpect(jsonPath("$.title").value("Не знайдено"));
	}

	@Test
	void conflictAndBusinessRule() throws Exception {
		mvc.perform(get(BASE + "/conflict").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("ALREADY_ARCHIVED"));
		mvc.perform(get(BASE + "/rule").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_SORT_FIELD"))
				.andExpect(jsonPath("$.detail").value("Sorting by title is not supported."));
	}

	@Test
	void missingPermissionIsForbiddenProblem() throws Exception {
		mvc.perform(get(BASE + "/forbidden").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("FORBIDDEN"))
				.andExpect(jsonPath("$.title").value("Access denied"));
	}

	@Test
	void bodyValidationListsFieldErrors() throws Exception {
		mvc.perform(post(BASE + "/validate").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.ACCEPT_LANGUAGE, "en").content("{\"name\":\" \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.errors[0].field").value("name"))
				.andExpect(jsonPath("$.errors[0].message").value("must not be blank"));
	}

	@Test
	void validationMessagesAreLocalized() throws Exception {
		mvc.perform(post(BASE + "/validate").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"\"}"))
				.andExpect(jsonPath("$.title").value("Помилка валідації"))
				.andExpect(jsonPath("$.errors[0].message").value("не може бути порожнім"));
	}

	@Test
	void malformedBodyIsBadRequestProblem() throws Exception {
		mvc.perform(post(BASE + "/validate").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{oops"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
	}

	@Test
	void unknownRouteIsNotFoundProblem() throws Exception {
		mvc.perform(get("/api/v1/does-not-exist"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void controllerWithoutPrefixIsNotExposedOutsideApiV1() throws Exception {
		mvc.perform(get("/test-kernel/not-found")).andExpect(status().isNotFound());
	}

	@Test
	void unexpectedErrorIs500WithoutLeakingDetails() throws Exception {
		mvc.perform(get(BASE + "/boom"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
	}

	@Test
	void paginatedEndpointsShareOneShape() throws Exception {
		mvc.perform(get(BASE + "/page").param("page", "1").param("size", "2").param("sort", "name,desc"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.page").value(1))
				.andExpect(jsonPath("$.size").value(2))
				.andExpect(jsonPath("$.totalElements").value(42))
				.andExpect(jsonPath("$.totalPages").value(21))
				.andExpect(jsonPath("$.first").value(false))
				.andExpect(jsonPath("$.last").value(false));
	}

	@Test
	void invalidPageSizeIsValidationProblem() throws Exception {
		mvc.perform(get(BASE + "/page").param("size", "1000"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

}
