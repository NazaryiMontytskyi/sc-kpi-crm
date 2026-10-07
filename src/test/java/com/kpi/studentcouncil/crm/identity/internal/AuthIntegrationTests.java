package com.kpi.studentcouncil.crm.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.kpi.studentcouncil.crm.TestcontainersConfiguration;
import com.kpi.studentcouncil.crm.audit.ActorProvider;

@SpringBootTest(properties = { "crm.bootstrap.admin.login=First.Admin", "crm.bootstrap.admin.password=Bootstrap-pass-42" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthIntegrationTests {

	private static final String AUTH = "/api/v1/auth";

	private static final String PASSWORD = "Sup3r-secret-pw";

	@Autowired
	MockMvc mvc;

	@Autowired
	UserRepository users;

	@Autowired
	PasswordEncoder encoder;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	ActorProvider actorProvider;

	@Autowired
	UserAccountService accounts;

	@Autowired
	org.springframework.data.domain.AuditorAware<UUID> auditorAware;

	// ---------------------------------------------------------------- helpers

	private User createUser(String loginPrefix, UserStatus status, boolean mustChange) {
		String login = loginPrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
		User user = User.create(login, encoder.encode(PASSWORD), "uk", mustChange);
		if (status == UserStatus.BLOCKED) {
			user.block();
		}
		else if (status == UserStatus.ARCHIVED) {
			user.archive(ActorProvider.SYSTEM_ACTOR, Instant.now());
		}
		return users.save(user);
	}

	private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
		return builder.contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.ACCEPT_LANGUAGE, "en").content(body);
	}

	private MvcResult login(String login, String password, String ip) throws Exception {
		return mvc.perform(json(post(AUTH + "/login").with(csrf()).with(request -> {
			request.setRemoteAddr(ip);
			return request;
		}), "{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}")).andReturn();
	}

	private MockHttpSession signIn(User user) throws Exception {
		MvcResult result = login(user.getLogin(), PASSWORD, "10.0.0." + (1 + (int) (Math.random() * 200)));
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private long auditCount(String action, UUID entityId) {
		return jdbc.queryForObject("select count(*) from audit_log where action = ? and entity_id = ?", Long.class,
				action, entityId);
	}

	// ---------------------------------------------------------------- sign-in

	@Test
	void correctCredentialsSignInAndMeReturnsTheUser() throws Exception {
		User user = createUser("member", UserStatus.ACTIVE, false);

		MvcResult result = login(user.getLogin().toUpperCase(), PASSWORD, "10.1.0.1");

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertThat(result.getResponse().getContentAsString()).doesNotContain(PASSWORD).doesNotContain("$2");
		MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
		assertThat(session).isNotNull();
		mvc.perform(get(AUTH + "/me").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(user.getId().toString()))
				.andExpect(jsonPath("$.login").value(user.getLogin()))
				.andExpect(jsonPath("$.locale").value("uk"))
				.andExpect(jsonPath("$.permissions", hasSize(0)))
				.andExpect(jsonPath("$.mustChangePassword").value(false))
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("password"))));
		assertThat(users.findById(user.getId()).orElseThrow().getLastLoginAt()).isNotNull();
		assertThat(auditCount("LOGIN", user.getId())).isEqualTo(1);
	}

	@Test
	void wrongPasswordAndUnknownLoginGiveTheSameProblemResponse() throws Exception {
		User user = createUser("member", UserStatus.ACTIVE, false);

		MvcResult wrong = login(user.getLogin(), "not-the-password-1", "10.2.0.1");
		MvcResult unknown = login("nobody-" + UUID.randomUUID(), "not-the-password-1", "10.2.0.2");

		assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
		assertThat(unknown.getResponse().getStatus()).isEqualTo(401);
		assertThat(wrong.getResponse().getContentType()).startsWith("application/problem+json");
		assertThat(wrong.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
				.isEqualTo(unknown.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
				.contains("\"code\":\"INVALID_CREDENTIALS\"");
		assertThat(auditCount("LOGIN_FAILED", user.getId())).isEqualTo(1);
	}

	@Test
	void blockedAndArchivedAccountsCannotSignInEvenWithCorrectPassword() throws Exception {
		User blocked = createUser("blocked", UserStatus.BLOCKED, false);
		User archived = createUser("archived", UserStatus.ARCHIVED, false);

		MvcResult b = login(blocked.getLogin(), PASSWORD, "10.3.0.1");
		MvcResult a = login(archived.getLogin(), PASSWORD, "10.3.0.2");
		MvcResult wrong = login(blocked.getLogin(), "not-the-password-1", "10.3.0.3");

		assertThat(b.getResponse().getStatus()).isEqualTo(401);
		assertThat(a.getResponse().getStatus()).isEqualTo(401);
		MockHttpSession failedSession = (MockHttpSession) b.getRequest().getSession(false);
		assertThat(failedSession == null || failedSession.getAttribute("SPRING_SECURITY_CONTEXT") == null).isTrue();
		assertThat(b.getResponse().getContentAsString()).isEqualTo(wrong.getResponse().getContentAsString());
		assertThat(users.findById(blocked.getId()).orElseThrow().getLastLoginAt()).isNull();
		assertThat(auditCount("LOGIN", blocked.getId())).isZero();
	}

	@Test
	void accountBlockedAfterSignInLosesItsSessionImmediately() throws Exception {
		User user = createUser("member", UserStatus.ACTIVE, false);
		MockHttpSession session = signIn(user);
		mvc.perform(get(AUTH + "/me").session(session)).andExpect(status().isOk());

		User loaded = users.findById(user.getId()).orElseThrow();
		loaded.block();
		users.save(loaded);

		mvc.perform(get(AUTH + "/me").session(session)).andExpect(status().isUnauthorized());
	}

	@Test
	void repeatedFailuresLockSignInEvenForTheCorrectPassword() throws Exception {
		User user = createUser("member", UserStatus.ACTIVE, false);
		String ip = "10.4.0.1";
		for (int i = 0; i < 5; i++) {
			assertThat(login(user.getLogin(), "not-the-password-1", ip).getResponse().getStatus()).isEqualTo(401);
		}

		MvcResult locked = login(user.getLogin(), PASSWORD, ip);

		assertThat(locked.getResponse().getStatus()).isEqualTo(429);
		assertThat(locked.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNotNull();
		assertThat(locked.getResponse().getContentAsString()).contains("\"code\":\"LOGIN_LOCKED\"");
		// Another IP is not affected (limit is per login+IP).
		assertThat(login(user.getLogin(), PASSWORD, "10.4.0.2").getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	void lockoutAlsoAppliesToUnknownLoginsSoExistenceIsNotRevealed() throws Exception {
		String ghost = "ghost-" + UUID.randomUUID();
		for (int i = 0; i < 5; i++) {
			login(ghost, "not-the-password-1", "10.5.0.1");
		}

		assertThat(login(ghost, "not-the-password-1", "10.5.0.1").getResponse().getStatus()).isEqualTo(429);
	}

	// ---------------------------------------------------------------- CSRF / unauthenticated

	@Test
	void stateChangingRequestWithoutCsrfTokenIsRejectedAsProblem() throws Exception {
		mvc.perform(json(post(AUTH + "/login"), "{\"login\":\"a\",\"password\":\"b\"}"))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"))
				.andExpect(jsonPath("$.title").value("Invalid security token"));
	}

	@Test
	void unauthenticatedRequestsGetLocalizedProblem401() throws Exception {
		mvc.perform(get("/api/v1/members"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.type").value("urn:crm:problem:unauthorized"))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.title").value("Потрібна автентифікація"));
		mvc.perform(get(AUTH + "/me").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.title").value("Authentication required"));
	}

	@Test
	void savedUserLocaleOverridesAcceptLanguage() throws Exception {
		User user = createUser("member", UserStatus.ACTIVE, false);
		MockHttpSession session = signIn(user);

		mvc.perform(patch(AUTH + "/me/locale").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"locale\":\"en\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.locale").value("en"));

		mvc.perform(post(AUTH + "/change-password").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.ACCEPT_LANGUAGE, "uk")
				.content("{\"currentPassword\":\"wrong-current-1\",\"newPassword\":\"Another-pass-77\"}"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"))
				.andExpect(jsonPath("$.title").value("Current password is incorrect"));
		mvc.perform(patch(AUTH + "/me/locale").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"locale\":\"de\"}"))
				.andExpect(status().isBadRequest());
		assertThat(auditCount("UPDATE", user.getId())).isEqualTo(1);
	}

	// ---------------------------------------------------------------- logout

	@Test
	void logoutEndsTheSessionAndIsAudited() throws Exception {
		User user = createUser("member", UserStatus.ACTIVE, false);
		MockHttpSession session = signIn(user);

		mvc.perform(post(AUTH + "/logout").session(session).with(csrf())).andExpect(status().isNoContent());

		mvc.perform(get(AUTH + "/me").session(session)).andExpect(status().isUnauthorized());
		assertThat(auditCount("LOGOUT", user.getId())).isEqualTo(1);
	}

	// ---------------------------------------------------------------- forced password change

	@Test
	void mustChangePasswordRestrictsEverythingButMeChangePasswordAndLogout() throws Exception {
		User user = createUser("temp", UserStatus.ACTIVE, true);
		MockHttpSession session = signIn(user);

		mvc.perform(get(AUTH + "/me").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mustChangePassword").value(true));
		mvc.perform(get("/api/v1/members").session(session).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
		mvc.perform(patch(AUTH + "/me/locale").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"locale\":\"en\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

		// same password
		changePassword(session, PASSWORD, PASSWORD).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("PASSWORD_UNCHANGED"));
		// policy
		changePassword(session, PASSWORD, "short1").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("PASSWORD_POLICY_VIOLATION"));
		changePassword(session, PASSWORD, "onlyletterspassword").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("PASSWORD_POLICY_VIOLATION"));
		// wrong current
		changePassword(session, "wrong-current-1", "Brand-new-pass-9").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));
		assertThat(users.findById(user.getId()).orElseThrow().isMustChangePassword()).isTrue();

		changePassword(session, PASSWORD, "Brand-new-pass-9").andExpect(status().isOk())
				.andExpect(jsonPath("$.mustChangePassword").value(false))
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Brand-new"))));

		// no longer restricted (reaches routing, hence 404 for the unknown route)
		mvc.perform(get("/api/v1/members").session(session)).andExpect(status().isNotFound());

		User reloaded = users.findById(user.getId()).orElseThrow();
		assertThat(reloaded.isMustChangePassword()).isFalse();
		assertThat(reloaded.getPasswordChangedAt()).isNotNull();
		assertThat(reloaded.getUpdatedBy()).isEqualTo(user.getId()); // JPA auditor is the signed-in user
		assertThat(auditCount("PASSWORD_CHANGE", user.getId())).isEqualTo(1);
		assertThat(jdbc.queryForObject("select actor_id from audit_log where action = 'PASSWORD_CHANGE' and entity_id = ?",
				UUID.class, user.getId())).isEqualTo(user.getId());
		// the old password no longer works, the new one does
		assertThat(login(user.getLogin(), PASSWORD, "10.6.0.1").getResponse().getStatus()).isEqualTo(401);
		assertThat(login(user.getLogin(), "Brand-new-pass-9", "10.6.0.2").getResponse().getStatus()).isEqualTo(200);
	}

	private org.springframework.test.web.servlet.ResultActions changePassword(MockHttpSession session, String current,
			String next) throws Exception {
		return mvc.perform(json(post(AUTH + "/change-password").session(session).with(csrf()),
				"{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"));
	}

	// ---------------------------------------------------------------- passwords never leak

	@Test
	void passwordsNeverAppearInAuditRows() throws Exception {
		User user = createUser("member", UserStatus.ACTIVE, true);
		login(user.getLogin(), "typed-wrong-pass-5", "10.7.0.1");
		MockHttpSession session = signIn(user);
		changePassword(session, PASSWORD, "Audit-leak-check-3").andExpect(status().isOk());

		for (String secret : List.of(PASSWORD, "typed-wrong-pass-5", "Audit-leak-check-3", "Bootstrap-pass-42", "$2a$", "$2b$")) {
			Long hits = jdbc.queryForObject(
					"select count(*) from audit_log where coalesce(before_state::text,'') like ? or coalesce(after_state::text,'') like ?",
					Long.class, "%" + secret + "%", "%" + secret + "%");
			assertThat(hits).as("audit rows containing %s", secret).isZero();
		}
	}

	// ---------------------------------------------------------------- bootstrap / actor wiring

	@Test
	void firstAdminWasBootstrappedFromEnvironmentOnEmptyDatabaseOnly() {
		User admin = users.findByLogin("first.admin").orElseThrow();

		assertThat(admin.isMustChangePassword()).isTrue();
		assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(admin.getPasswordHash()).startsWith("$2").doesNotContain("Bootstrap-pass-42");
		assertThat(auditCount("CREATE", admin.getId())).isEqualTo(1);

		long before = users.count();
		assertThat(accounts.createFirstAdminIfAbsent("another.admin", "Another-admin-pass-1")).isFalse();
		assertThat(users.count()).isEqualTo(before);
		assertThat(users.findByLogin("another.admin")).isEmpty();
	}

	@Test
	void authenticatedUserReplacesTheSystemActorFallback() throws Exception {
		assertThat(actorProvider.getClass().getSimpleName()).isEqualTo("SecurityContextActor");
		assertThat(auditorAware.getCurrentAuditor()).contains(ActorProvider.SYSTEM_ACTOR);
	}

	// ---------------------------------------------------------------- OpenAPI contract

	@Test
	void openApiDocumentsTheAuthEndpoints() throws Exception {
		mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/v1/auth/login'].post").exists())
				.andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post").exists())
				.andExpect(jsonPath("$.paths['/api/v1/auth/me'].get").exists())
				.andExpect(jsonPath("$.paths['/api/v1/auth/change-password'].post").exists())
				.andExpect(jsonPath("$.paths['/api/v1/auth/me/locale'].patch").exists())
				.andExpect(jsonPath("$.components.schemas.MeResponse.properties.permissions").exists());
	}

}
