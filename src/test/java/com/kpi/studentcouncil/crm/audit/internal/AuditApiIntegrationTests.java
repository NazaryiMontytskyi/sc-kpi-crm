package com.kpi.studentcouncil.crm.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

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

import com.kpi.studentcouncil.crm.TestcontainersConfiguration;
import com.kpi.studentcouncil.crm.access.AccessService;
import com.kpi.studentcouncil.crmtest.SharedAdmin;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuditApiIntegrationTests {

	private static final String PASSWORD = "Sup3r-secret-pw";

	private static final Instant T1 = Instant.parse("2031-03-01T10:00:00Z");

	private static final Instant T2 = Instant.parse("2031-03-05T10:00:00Z");

	private static final Instant T3 = Instant.parse("2031-03-09T10:00:00Z");

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PasswordEncoder encoder;

	@Autowired
	AccessService access;

	MockHttpSession admin;

	String type;

	UUID entity;

	UUID actorA;

	UUID actorB;

	@BeforeEach
	void setUp() throws Exception {
		SharedAdmin.ensure(jdbc, encoder);
		admin = signIn(SharedAdmin.LOGIN, SharedAdmin.PASSWORD);
		type = "FakeThing" + UUID.randomUUID().toString().substring(0, 8);
		entity = UUID.randomUUID();
		actorA = UUID.randomUUID();
		actorB = UUID.randomUUID();
		insert(actorA, "CREATE", type, entity, null, "{\"title\":\"one\"}", T1);
		insert(actorB, "UPDATE", type, entity, "{\"title\":\"one\"}", "{\"title\":\"two\"}", T2);
		insert(actorA, "ARCHIVE", type, UUID.randomUUID(), null, null, T3);
	}

	private void insert(UUID actor, String action, String entityType, UUID entityId, String before, String after,
			Instant at) {
		jdbc.update("insert into audit_log (id, actor_id, action, entity_type, entity_id, before_state, after_state,"
				+ " at, ip) values (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, '203.0.113.5')", UUID.randomUUID(), actor,
				action, entityType, entityId, before, after, Timestamp.from(at));
	}

	private MockHttpSession signIn(String login, String password) throws Exception {
		MvcResult result = mvc.perform(post("/api/v1/auth/login").with(csrf()).with(request -> {
			request.setRemoteAddr("10.9." + (int) (Math.random() * 250) + "." + (1 + (int) (Math.random() * 250)));
			return request;
		}).contentType(MediaType.APPLICATION_JSON)
				.content("{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}")).andReturn();
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private MockHttpSession userWith(String... permissionKeys) throws Exception {
		UUID user = UUID.randomUUID();
		String login = "audit-user-" + user.toString().substring(0, 8);
		jdbc.update("insert into users (id, login, password_hash, status, locale, must_change_password, created_at,"
				+ " updated_at) values (?, ?, ?, 'ACTIVE', 'en', false, now(), now())", user, login,
				encoder.encode(PASSWORD));
		if (permissionKeys.length > 0) {
			UUID role = UUID.randomUUID();
			jdbc.update("insert into role (id, name, is_system, created_at, updated_at) values (?, ?, false, now(),"
					+ " now())", role, "audit-role-" + role);
			for (String key : permissionKeys) {
				jdbc.update("insert into role_permission (role_id, permission_key) values (?, ?)", role, key);
			}
			access.grantRole(user, role);
		}
		return signIn(login, PASSWORD);
	}

	private org.springframework.test.web.servlet.ResultActions list(MockHttpSession s, String query)
			throws Exception {
		return mvc.perform(get("/api/v1/audit?entityType=" + type + query).session(s)
				.header(HttpHeaders.ACCEPT_LANGUAGE, "en"));
	}

	@Test
	void anonymousIsRejected() throws Exception {
		mvc.perform(get("/api/v1/audit")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/audit/entities/" + type + "/" + entity)).andExpect(status().isUnauthorized());
	}

	@Test
	void globalLogIsForbiddenWithoutAuditView() throws Exception {
		list(userWith(), "").andExpect(status().isForbidden());
		list(userWith("members.view"), "").andExpect(status().isForbidden());
	}

	@Test
	void globalLogReturnsNewestFirstWithIpForAuditViewHolder() throws Exception {
		list(userWith("audit.view"), "").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.content[0].action").value("ARCHIVE"))
				.andExpect(jsonPath("$.content[2].action").value("CREATE"))
				.andExpect(jsonPath("$.content[0].ip").value("203.0.113.5"));
	}

	@Test
	void filtersByActor() throws Exception {
		list(admin, "&actorId=" + actorB).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].action").value("UPDATE"))
				.andExpect(jsonPath("$.content[0].before.title").value("one"))
				.andExpect(jsonPath("$.content[0].after.title").value("two"));
		list(admin, "&actorId=" + actorA).andExpect(jsonPath("$.totalElements").value(2));
	}

	@Test
	void filtersByEntityAndAction() throws Exception {
		list(admin, "&entityId=" + entity).andExpect(jsonPath("$.totalElements").value(2));
		list(admin, "&action=archive").andExpect(jsonPath("$.totalElements").value(1));
		list(admin, "&entityId=" + entity + "&action=CREATE").andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void filtersByPeriodFromInclusiveToExclusive() throws Exception {
		list(admin, "&from=2031-03-02T00:00:00Z&to=2031-03-08T00:00:00Z")
				.andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].action").value("UPDATE"));
		list(admin, "&from=2031-03-05").andExpect(jsonPath("$.totalElements").value(2));
		list(admin, "&to=2031-03-05T10:00:00Z").andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void paginates() throws Exception {
		list(admin, "&size=2&page=1").andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.last").value(true));
	}

	@Test
	void invalidFiltersYieldLocalizedProblem() throws Exception {
		list(admin, "&actorId=nope").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_FILTER"));
		list(admin, "&action=EXPLODE").andExpect(status().isUnprocessableContent());
		list(admin, "&from=yesterday").andExpect(status().isUnprocessableContent());
		list(admin, "&from=2031-03-09&to=2031-03-01").andExpect(status().isUnprocessableContent());
		mvc.perform(get("/api/v1/audit?action=EXPLODE").session(admin).header(HttpHeaders.ACCEPT_LANGUAGE, "uk"))
				.andExpect(jsonPath("$.title").value("\u041D\u0435\u043A\u043E\u0440\u0435\u043A\u0442\u043D\u0438\u0439 \u0444\u0456\u043B\u044C\u0442\u0440"));
	}

	@Test
	void objectHistoryIsVisibleToAnyAuthenticatedUserWithoutIp() throws Exception {
		mvc.perform(get("/api/v1/audit/entities/" + type + "/" + entity).session(userWith()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content[0].action").value("UPDATE"))
				.andExpect(jsonPath("$.content[1].action").value("CREATE"))
				.andExpect(jsonPath("$.content[0].ip").doesNotExist());
	}

	@Test
	void objectHistoryOfUnknownObjectIsEmpty() throws Exception {
		mvc.perform(get("/api/v1/audit/entities/" + type + "/" + UUID.randomUUID()).session(admin))
				.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void auditRowsNeverContainPasswordHashes() {
		Long leaks = jdbc.queryForObject("select count(*) from audit_log where before_state::text ilike"
				+ " '%password_hash%' or after_state::text ilike '%password_hash%' or after_state::text like"
				+ " '%$2a$%' or before_state::text like '%$2a$%'", Long.class);
		assertThat(leaks).isZero();
	}

}
