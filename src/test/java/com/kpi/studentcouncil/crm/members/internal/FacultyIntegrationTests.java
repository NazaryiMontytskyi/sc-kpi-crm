package com.kpi.studentcouncil.crm.members.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.ResultActions;

import com.kpi.studentcouncil.crm.TestcontainersConfiguration;
import com.kpi.studentcouncil.crm.access.AccessService;
import com.kpi.studentcouncil.crm.members.FacultyLookup;

@SpringBootTest(properties = { "crm.bootstrap.admin.login=Fac.Admin", "crm.bootstrap.admin.password=Bootstrap-pass-42" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FacultyIntegrationTests {

	private static final String PASSWORD = "Sup3r-secret-pw";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PasswordEncoder encoder;

	@Autowired
	AccessService access;

	@Autowired
	FacultyLookup lookup;

	@Autowired
	FacultySeeder seeder;

	MockHttpSession admin;

	@BeforeEach
	void setUp() throws Exception {
		jdbc.update("update users set must_change_password = false where login = 'fac.admin'");
		admin = signIn("fac.admin", "Bootstrap-pass-42");
	}

	private MockHttpSession signIn(String login, String password) throws Exception {
		MvcResult result = mvc.perform(post("/api/v1/auth/login").with(csrf()).with(request -> {
			request.setRemoteAddr("10.8." + (int) (Math.random() * 250) + "." + (1 + (int) (Math.random() * 250)));
			return request;
		}).contentType(MediaType.APPLICATION_JSON)
				.content("{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}")).andReturn();
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private MockHttpSession userWith(String... permissionKeys) throws Exception {
		UUID user = UUID.randomUUID();
		String login = "fac-user-" + user.toString().substring(0, 8);
		jdbc.update("insert into users (id, login, password_hash, status, locale, must_change_password, created_at,"
				+ " updated_at) values (?, ?, ?, 'ACTIVE', 'en', false, now(), now())", user, login,
				encoder.encode(PASSWORD));
		if (permissionKeys.length > 0) {
			UUID role = UUID.randomUUID();
			jdbc.update("insert into role (id, name, is_system, created_at, updated_at) values (?, ?, false, now(),"
					+ " now())", role, "fac-role-" + role);
			for (String key : permissionKeys) {
				jdbc.update("insert into role_permission (role_id, permission_key) values (?, ?)", role, key);
			}
			access.grantRole(user, role);
		}
		return signIn(login, PASSWORD);
	}

	private ResultActions send(MockHttpSession s, String path, String json) throws Exception {
		return mvc.perform(post("/api/v1/faculties" + path).with(csrf()).session(s)
				.header(HttpHeaders.ACCEPT_LANGUAGE, "en").contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private static String body(String code, String uk, String en) {
		return "{\"code\":\"" + code + "\",\"nameUk\":\"" + uk + "\",\"nameEn\":\"" + en + "\"}";
	}

	private UUID create(String code, String uk, String en) throws Exception {
		MvcResult r = send(admin, "", body(code, uk, en)).andExpect(status().isCreated()).andReturn();
		return UUID.fromString(JsonPath.read(r.getResponse().getContentAsString(), "$.id"));
	}

	private long audit(UUID id, String action) {
		return jdbc.queryForObject(
				"select count(*) from audit_log where entity_type = 'Faculty' and entity_id = ? and action = ?",
				Long.class, id, action);
	}

	@Test
	void anonymousIsRejected() throws Exception {
		mvc.perform(get("/api/v1/faculties")).andExpect(status().isUnauthorized());
	}

	@Test
	void permissionMatrix() throws Exception {
		UUID id = create("PM-1", "Матриця прав", "Permission matrix");
		MockHttpSession plain = userWith();
		MockHttpSession manager = userWith("settings.manage");

		mvc.perform(get("/api/v1/faculties").session(plain)).andExpect(status().isOk());
		mvc.perform(get("/api/v1/faculties/" + id).session(plain)).andExpect(status().isOk());

		send(plain, "", body("PM-2", "а", "a")).andExpect(status().isForbidden());
		mvc.perform(put("/api/v1/faculties/" + id).with(csrf()).session(plain).contentType(MediaType.APPLICATION_JSON)
				.content(body("PM-1", "б", "b"))).andExpect(status().isForbidden());
		send(plain, "/" + id + "/archive", "").andExpect(status().isForbidden());
		send(plain, "/" + id + "/restore", "").andExpect(status().isForbidden());

		send(manager, "", body("PM-3", "Менеджерський", "Managed")).andExpect(status().isCreated());
	}

	@Test
	void validationAndUniquenessWithLocalizedProblems() throws Exception {
		create("UQ-1", "Унікальний", "Unique");
		send(admin, "", "{\"code\":\"UQ-2\",\"nameUk\":\"Лише укр\"}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		send(admin, "", body("UQ-3", " ", "x")).andExpect(status().isBadRequest());
		send(admin, "", body("uq-1", "Інша", "Other")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("FACULTY_CODE_TAKEN"));
		send(admin, "", body("UQ-4", "УНІКАЛЬНИЙ", "Other2")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("FACULTY_NAME_TAKEN"));
		send(admin, "", body("UQ-5", "Інша2", "unique")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("FACULTY_NAME_TAKEN"));
		// admin profile locale is uk, an English user gets English
		send(admin, "", body("UQ-1", "x1", "x2"))
				.andExpect(jsonPath("$.title").value("Код факультету вже використовується"));
		send(userWith("settings.manage"), "", body("UQ-1", "x1", "x2"))
				.andExpect(jsonPath("$.title").value("Faculty code already used"));
	}

	@Test
	void archiveRestoreLookupAuditAndListVisibility() throws Exception {
		UUID id = create("AR-1", "Архівний", "Archivable");
		assertThat(lookup.isSelectable(id)).isTrue();
		assertThat(lookup.listActive()).anyMatch(f -> f.id().equals(id));

		mvc.perform(put("/api/v1/faculties/" + id).with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
				.content(body("AR-1", "Архівний 2", "Archivable 2"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.nameEn").value("Archivable 2"));

		send(admin, "/" + id + "/archive", "").andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(true));
		assertThat(lookup.isSelectable(id)).isFalse();
		assertThat(lookup.resolve(id)).get().satisfies(f -> assertThat(f.active()).isFalse());
		assertThat(lookup.listActive()).noneMatch(f -> f.id().equals(id));
		send(admin, "/" + id + "/archive", "").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ALREADY_ARCHIVED"));

		MockHttpSession plain = userWith();
		mvc.perform(get("/api/v1/faculties?includeArchived=true&size=100").session(plain))
				.andExpect(jsonPath("$.content[?(@.id=='" + id + "')]").isEmpty());
		mvc.perform(get("/api/v1/faculties?includeArchived=true&size=100").session(admin))
				.andExpect(jsonPath("$.content[?(@.id=='" + id + "')]").isNotEmpty());
		mvc.perform(get("/api/v1/faculties/" + id).session(plain)).andExpect(status().isOk());

		// the name is free while archived: another faculty takes it, so restore conflicts
		UUID other = create("AR-2", "Архівний 2", "Archivable 2");
		send(admin, "/" + id + "/restore", "").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("FACULTY_NAME_TAKEN"));
		send(admin, "/" + other + "/archive", "").andExpect(status().isOk());
		send(admin, "/" + id + "/restore", "").andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(false));
		assertThat(lookup.isSelectable(id)).isTrue();

		assertThat(audit(id, "CREATE")).isEqualTo(1);
		assertThat(audit(id, "UPDATE")).isEqualTo(1);
		assertThat(audit(id, "ARCHIVE")).isEqualTo(1);
		assertThat(audit(id, "RESTORE")).isEqualTo(1);
		assertThat(lookup.isSelectable(UUID.randomUUID())).isFalse();
		assertThat(lookup.isSelectable(null)).isFalse();
	}

	@Test
	void seederIsIdempotentAndReferenceDataIsPresent() {
		long before = jdbc.queryForObject("select count(*) from faculty where code like 'FAC-%'", Long.class);
		assertThat(before).isEqualTo(FacultySeeder.COUNT);
		seeder.run();
		assertThat(jdbc.queryForObject("select count(*) from faculty where code like 'FAC-%'", Long.class))
				.isEqualTo(before);
	}

}
