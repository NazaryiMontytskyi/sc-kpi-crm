package com.kpi.studentcouncil.crm.tasks.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
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
import com.kpi.studentcouncil.crm.tasks.SpaceStatusUsageGuard;
import com.kpi.studentcouncil.crmtest.SharedAdmin;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, SpaceIntegrationTests.UsageConfig.class })
class SpaceIntegrationTests {

	private static final String PASSWORD = "Sup3r-secret-pw";

	/** Stands in for the tasks owned by INC-025: counts usages per status and records reassignments. */
	static class StubUsageGuard implements SpaceStatusUsageGuard {

		final Map<UUID, Long> usages = new ConcurrentHashMap<>();
		final List<UUID[]> reassigned = new ArrayList<>();

		@Override
		public long countUsages(UUID spaceId, UUID statusId) {
			return usages.getOrDefault(statusId, 0L);
		}

		@Override
		public void reassign(UUID spaceId, UUID fromStatusId, UUID toStatusId) {
			reassigned.add(new UUID[] { fromStatusId, toStatusId });
			usages.remove(fromStatusId);
		}

	}

	@TestConfiguration(proxyBeanMethods = false)
	static class UsageConfig {

		@Bean
		StubUsageGuard stubUsageGuard() {
			return new StubUsageGuard();
		}

	}

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PasswordEncoder encoder;

	@Autowired
	AccessService access;

	@Autowired
	StubUsageGuard guard;

	MockHttpSession admin;

	private final List<UUID> spaces = new ArrayList<>();
	private final List<UUID> units = new ArrayList<>();
	private final List<UUID> users = new ArrayList<>();
	private final List<UUID> roles = new ArrayList<>();

	@BeforeEach
	void setUp() throws Exception {
		SharedAdmin.ensure(jdbc, encoder);
		admin = signIn(SharedAdmin.LOGIN, SharedAdmin.PASSWORD);
		guard.usages.clear();
		guard.reassigned.clear();
	}

	/** Removes everything this class created, children first, so other test classes sharing the DB are unaffected. */
	@AfterEach
	void cleanUp() {
		for (UUID space : spaces) {
			jdbc.update("delete from task_custom_field_option where field_id in"
					+ " (select id from task_custom_field where space_id = ?)", space);
			jdbc.update("delete from task_custom_field where space_id = ?", space);
			jdbc.update("delete from task_space_status where space_id = ?", space);
			jdbc.update("delete from task_space where id = ?", space);
		}
		for (UUID unit : units) {
			jdbc.update("delete from org_unit where id = ?", unit);
		}
		for (UUID user : users) {
			jdbc.update("delete from role_assignment where user_id = ?", user);
		}
		for (UUID role : roles) {
			jdbc.update("delete from role_permission where role_id = ?", role);
			jdbc.update("delete from role where id = ?", role);
		}
		for (UUID user : users) {
			jdbc.update("delete from users where id = ?", user);
		}
		spaces.clear();
		units.clear();
		users.clear();
		roles.clear();
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

	private MockHttpSession sessionWith(String... permissionKeys) throws Exception {
		UUID user = UUID.randomUUID();
		users.add(user);
		jdbc.update("insert into users (id, login, password_hash, status, locale, must_change_password, created_at,"
				+ " updated_at) values (?, ?, ?, 'ACTIVE', 'en', false, now(), now())", user,
				"space-user-" + user.toString().substring(0, 8), encoder.encode(PASSWORD));
		if (permissionKeys.length > 0) {
			UUID role = UUID.randomUUID();
			roles.add(role);
			jdbc.update("insert into role (id, name, is_system, created_at, updated_at) values (?, ?, false, now(),"
					+ " now())", role, "space-role-" + role);
			for (String key : permissionKeys) {
				jdbc.update("insert into role_permission (role_id, permission_key) values (?, ?)", role, key);
			}
			access.grantRole(user, role);
		}
		String login = jdbc.queryForObject("select login from users where id = ?", String.class, user);
		return signIn(login, PASSWORD);
	}

	private ResultActions send(MockHttpSession s, String path, String json) throws Exception {
		return mvc.perform(post("/api/v1" + path).with(csrf()).session(s).header(HttpHeaders.ACCEPT_LANGUAGE, "en")
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions putJson(MockHttpSession s, String path, String json) throws Exception {
		return mvc.perform(put("/api/v1" + path).with(csrf()).session(s).header(HttpHeaders.ACCEPT_LANGUAGE, "en")
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions read(MockHttpSession s, String path) throws Exception {
		return mvc.perform(get("/api/v1" + path).session(s).header(HttpHeaders.ACCEPT_LANGUAGE, "en"));
	}

	private static UUID id(ResultActions r) throws Exception {
		return UUID.fromString(JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id"));
	}

	private static String body(ResultActions r) throws Exception {
		return r.andReturn().getResponse().getContentAsString();
	}

	private UUID space(String json) throws Exception {
		UUID id = id(send(admin, "/spaces", json).andExpect(status().isCreated()));
		spaces.add(id);
		return id;
	}

	private UUID space() throws Exception {
		return space("{\"name\":\"Space " + UUID.randomUUID() + "\",\"general\":true}");
	}

	private UUID unit() throws Exception {
		UUID id = id(send(admin, "/org-units", "{\"name\":\"Unit " + UUID.randomUUID() + "\",\"type\":\"WORKING_GROUP\"}")
				.andExpect(status().isCreated()));
		units.add(id);
		return id;
	}

	private UUID statusId(UUID space, String category) throws Exception {
		List<String> ids = JsonPath.read(body(read(admin, "/spaces/" + space + "/statuses")),
				"$[?(@.category=='" + category + "')].id");
		return UUID.fromString(ids.get(0));
	}

	private long audit(String type, UUID id, String action) {
		return jdbc.queryForObject(
				"select count(*) from audit_log where entity_type = ? and entity_id = ? and action = ?", Long.class,
				type, id, action);
	}

	@Test
	void newSpaceGetsDefaultStatusesInOrder() throws Exception {
		UUID space = space();
		read(admin, "/spaces/" + space).andExpect(status().isOk()).andExpect(jsonPath("$.general").value(true))
				.andExpect(jsonPath("$.statuses", hasSize(4)))
				.andExpect(jsonPath("$.statuses[0].category").value("TODO"))
				.andExpect(jsonPath("$.statuses[0].position").value(0))
				.andExpect(jsonPath("$.statuses[1].category").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.statuses[2].category").value("DONE"))
				.andExpect(jsonPath("$.statuses[3].category").value("CANCELED"))
				.andExpect(jsonPath("$.statuses[0].name").value("До виконання"))
				.andExpect(jsonPath("$.customFields", hasSize(0)));
		assertThat(audit("Space", space, "CREATE")).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_type = 'SpaceStatus' and action ="
				+ " 'CREATE' and after_state ->> 'spaceId' = ?", Long.class, space.toString())).isEqualTo(4);
	}

	@Test
	void readsNeedTasksViewAndChangesNeedSpacesManage() throws Exception {
		UUID space = space();
		MockHttpSession plain = sessionWith();
		MockHttpSession viewer = sessionWith("tasks.view");
		MockHttpSession manager = sessionWith("spaces.manage");

		mvc.perform(get("/api/v1/spaces")).andExpect(status().isUnauthorized());
		read(plain, "/spaces").andExpect(status().isForbidden());
		read(plain, "/spaces/" + space).andExpect(status().isForbidden());
		read(manager, "/spaces/" + space + "/statuses").andExpect(status().isForbidden());
		read(viewer, "/spaces").andExpect(status().isOk());
		read(viewer, "/spaces/" + space + "/custom-fields").andExpect(status().isOk());

		String create = "{\"name\":\"Another\",\"general\":true}";
		send(viewer, "/spaces", create).andExpect(status().isForbidden());
		send(plain, "/spaces", create).andExpect(status().isForbidden());
		putJson(viewer, "/spaces/" + space, create).andExpect(status().isForbidden());
		send(viewer, "/spaces/" + space + "/archive", "").andExpect(status().isForbidden());
		send(viewer, "/spaces/" + space + "/statuses", "{\"name\":\"X\",\"category\":\"TODO\"}")
				.andExpect(status().isForbidden());
		putJson(viewer, "/spaces/" + space + "/statuses/order", "{\"statusIds\":[\"" + UUID.randomUUID() + "\"]}")
				.andExpect(status().isForbidden());
		send(viewer, "/spaces/" + space + "/custom-fields", "{\"name\":\"F\",\"type\":\"TEXT\"}")
				.andExpect(status().isForbidden());

		UUID created = id(send(manager, "/spaces", create).andExpect(status().isCreated()));
		spaces.add(created);
	}

	@Test
	void unitBindingIsValidatedThroughOrg() throws Exception {
		UUID unit = unit();
		UUID bound = space("{\"name\":\"Bound " + UUID.randomUUID() + "\",\"orgUnitId\":\"" + unit + "\"}");
		read(admin, "/spaces/" + bound).andExpect(jsonPath("$.orgUnitId").value(unit.toString()))
				.andExpect(jsonPath("$.general").value(false));
		read(admin, "/spaces?orgUnitId=" + unit).andExpect(jsonPath("$.content", hasSize(1)));

		send(admin, "/spaces", "{\"name\":\"X\",\"general\":true,\"orgUnitId\":\"" + unit + "\"}")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("SPACE_GENERAL_HAS_ORG_UNIT"));
		send(admin, "/spaces", "{\"name\":\"X\",\"orgUnitId\":\"" + UUID.randomUUID() + "\"}")
				.andExpect(status().isNotFound());

		send(admin, "/org-units/" + unit + "/archive", "").andExpect(status().isOk());
		send(admin, "/spaces", "{\"name\":\"X\",\"orgUnitId\":\"" + unit + "\"}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SPACE_ORG_UNIT_ARCHIVED"));
	}

	@Test
	void statusesCanBeAddedRenamedAndReorderedAndOrderPersists() throws Exception {
		UUID space = space();
		UUID todo = statusId(space, "TODO");
		UUID progress = statusId(space, "IN_PROGRESS");
		UUID done = statusId(space, "DONE");
		UUID canceled = statusId(space, "CANCELED");
		UUID review = id(send(admin, "/spaces/" + space + "/statuses", "{\"name\":\"Review\",\"category\":\"IN_PROGRESS\"}")
				.andExpect(status().isCreated()).andExpect(jsonPath("$.position").value(4)));

		send(admin, "/spaces/" + space + "/statuses", "{\"name\":\" review \",\"category\":\"TODO\"}")
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SPACE_STATUS_NAME_TAKEN"));
		putJson(admin, "/spaces/" + space + "/statuses/" + review, "{\"name\":\"Code review\",\"category\":\"IN_PROGRESS\"}")
				.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Code review"));

		String order = "{\"statusIds\":[\"" + review + "\",\"" + done + "\",\"" + todo + "\",\"" + canceled + "\",\""
				+ progress + "\"]}";
		putJson(admin, "/spaces/" + space + "/statuses/order", order).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(review.toString()));
		read(admin, "/spaces/" + space + "/statuses").andExpect(jsonPath("$[0].id").value(review.toString()))
				.andExpect(jsonPath("$[1].id").value(done.toString())).andExpect(jsonPath("$[4].id").value(progress.toString()))
				.andExpect(jsonPath("$[4].position").value(4));

		putJson(admin, "/spaces/" + space + "/statuses/order", "{\"statusIds\":[\"" + review + "\",\"" + done + "\"]}")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("SPACE_STATUS_ORDER_MISMATCH"));
		assertThat(audit("Space", space, "UPDATE")).isEqualTo(1);
		assertThat(audit("SpaceStatus", review, "UPDATE")).isEqualTo(1);
	}

	@Test
	void categoriesAreRestrictedToTheFourValues() throws Exception {
		UUID space = space();
		send(admin, "/spaces/" + space + "/statuses", "{\"name\":\"Odd\",\"category\":\"BLOCKED\"}")
				.andExpect(status().isBadRequest());
		send(admin, "/spaces/" + space + "/statuses", "{\"name\":\"Odd\"}").andExpect(status().isBadRequest());
	}

	@Test
	void spaceKeepsATodoAndADoneStatus() throws Exception {
		UUID space = space();
		UUID todo = statusId(space, "TODO");
		UUID done = statusId(space, "DONE");
		send(admin, "/spaces/" + space + "/statuses/" + done + "/archive", "").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("SPACE_STATUS_CATEGORY_REQUIRED"));
		putJson(admin, "/spaces/" + space + "/statuses/" + todo, "{\"name\":\"Backlog\",\"category\":\"CANCELED\"}")
				.andExpect(status().isUnprocessableContent());

		UUID shipped = id(send(admin, "/spaces/" + space + "/statuses", "{\"name\":\"Shipped\",\"category\":\"DONE\"}")
				.andExpect(status().isCreated()));
		send(admin, "/spaces/" + space + "/statuses/" + done + "/archive", "").andExpect(status().isOk())
				.andExpect(jsonPath("$.archived").value(true));
		read(admin, "/spaces/" + space + "/statuses").andExpect(jsonPath("$", hasSize(4)));
		send(admin, "/spaces/" + space + "/statuses/" + shipped + "/archive", "").andExpect(status().isUnprocessableContent());
		assertThat(audit("SpaceStatus", done, "ARCHIVE")).isEqualTo(1);
	}

	@Test
	void statusInUseCannotBeArchivedWithoutMappingToAnother() throws Exception {
		UUID space = space();
		UUID progress = statusId(space, "IN_PROGRESS");
		UUID todo = statusId(space, "TODO");
		UUID otherSpaceStatus = statusId(space(), "TODO");
		guard.usages.put(progress, 2L);

		send(admin, "/spaces/" + space + "/statuses/" + progress + "/archive", "").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SPACE_STATUS_IN_USE"));
		send(admin, "/spaces/" + space + "/statuses/" + progress + "/archive",
				"{\"replacementStatusId\":\"" + otherSpaceStatus + "\"}").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("SPACE_STATUS_REPLACEMENT_INVALID"));
		read(admin, "/spaces/" + space + "/statuses").andExpect(jsonPath("$", hasSize(4)));
		assertThat(guard.reassigned).isEmpty();

		send(admin, "/spaces/" + space + "/statuses/" + progress + "/archive",
				"{\"replacementStatusId\":\"" + todo + "\"}").andExpect(status().isOk());
		assertThat(guard.reassigned).hasSize(1);
		assertThat(guard.reassigned.get(0)).containsExactly(progress, todo);
		read(admin, "/spaces/" + space + "/statuses").andExpect(jsonPath("$", hasSize(3)));
	}

	@Test
	void customFieldTypesAreLimitedAndSelectNeedsOptions() throws Exception {
		UUID space = space();
		String path = "/spaces/" + space + "/custom-fields";
		for (String type : List.of("TEXT", "NUMBER", "DATE", "PERSON")) {
			send(admin, path, "{\"name\":\"" + type + " field\",\"type\":\"" + type + "\",\"required\":true}")
					.andExpect(status().isCreated()).andExpect(jsonPath("$.type").value(type))
					.andExpect(jsonPath("$.required").value(true)).andExpect(jsonPath("$.options", hasSize(0)));
		}
		UUID select = id(send(admin, path, "{\"name\":\"Priority\",\"type\":\"SELECT\",\"options\":[\"Low\",\" High \"]}")
				.andExpect(status().isCreated()).andExpect(jsonPath("$.options[1]").value("High")));

		send(admin, path, "{\"name\":\"Bad\",\"type\":\"SELECT\"}").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("CUSTOM_FIELD_OPTIONS_REQUIRED"));
		send(admin, path, "{\"name\":\"Bad\",\"type\":\"SELECT\",\"options\":[\"A\",\"a\"]}")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("CUSTOM_FIELD_OPTION_DUPLICATE"));
		send(admin, path, "{\"name\":\"Bad\",\"type\":\"TEXT\",\"options\":[\"A\"]}")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("CUSTOM_FIELD_OPTIONS_NOT_ALLOWED"));
		send(admin, path, "{\"name\":\"Bad\",\"type\":\"CHECKBOX\"}").andExpect(status().isBadRequest());
		send(admin, path, "{\"name\":\"priority\",\"type\":\"TEXT\"}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CUSTOM_FIELD_NAME_TAKEN"));

		putJson(admin, path + "/" + select, "{\"name\":\"Priority\",\"options\":[\"P1\",\"P2\",\"P3\"]}")
				.andExpect(status().isOk()).andExpect(jsonPath("$.type").value("SELECT"))
				.andExpect(jsonPath("$.options", hasSize(3)));
		putJson(admin, path + "/" + select, "{\"name\":\"Priority\",\"options\":[]}")
				.andExpect(status().isUnprocessableContent());
		read(admin, path).andExpect(jsonPath("$", hasSize(5)));

		send(admin, path + "/" + select + "/archive", "").andExpect(status().isOk());
		read(admin, path).andExpect(jsonPath("$", hasSize(4)));
		assertThat(audit("CustomFieldDefinition", select, "CREATE")).isEqualTo(1);
		assertThat(audit("CustomFieldDefinition", select, "UPDATE")).isEqualTo(1);
		assertThat(audit("CustomFieldDefinition", select, "ARCHIVE")).isEqualTo(1);
	}

	@Test
	void archivingASpaceIsSoftAndBlocksChanges() throws Exception {
		UUID unit = unit();
		UUID space = space("{\"name\":\"Soft " + UUID.randomUUID() + "\",\"orgUnitId\":\"" + unit + "\"}");
		send(admin, "/spaces/" + space + "/archive", "").andExpect(status().isOk())
				.andExpect(jsonPath("$.archived").value(true));
		send(admin, "/spaces/" + space + "/archive", "").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ALREADY_ARCHIVED"));
		assertThat(jdbc.queryForObject("select count(*) from task_space where id = ?", Long.class, space)).isEqualTo(1);

		send(admin, "/spaces/" + space + "/statuses", "{\"name\":\"X\",\"category\":\"TODO\"}")
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SPACE_ARCHIVED"));
		putJson(admin, "/spaces/" + space, "{\"name\":\"Renamed\",\"orgUnitId\":\"" + unit + "\"}")
				.andExpect(status().isConflict());

		String listed = body(read(admin, "/spaces?orgUnitId=" + unit));
		assertThat((List<?>) JsonPath.read(listed, "$.content[?(@.id=='" + space + "')]")).isEmpty();
		String all = body(read(admin, "/spaces?includeArchived=true&orgUnitId=" + unit));
		assertThat((List<?>) JsonPath.read(all, "$.content[?(@.id=='" + space + "')]")).hasSize(1);

		send(admin, "/spaces/" + space + "/restore", "").andExpect(status().isOk())
				.andExpect(jsonPath("$.archived").value(false));
		putJson(admin, "/spaces/" + space, "{\"name\":\"Renamed\",\"orgUnitId\":\"" + unit + "\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Renamed"));
		assertThat(audit("Space", space, "ARCHIVE")).isEqualTo(1);
		assertThat(audit("Space", space, "RESTORE")).isEqualTo(1);
		assertThat(audit("Space", space, "UPDATE")).isEqualTo(1);
	}

}
