package com.kpi.studentcouncil.crm.org.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.kpi.studentcouncil.crm.org.OrgQueryService;
import com.kpi.studentcouncil.crm.org.OrgService;
import com.kpi.studentcouncil.crmtest.SharedAdmin;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class MembershipIntegrationTests {

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
	OrgQueryService query;

	@Autowired
	OrgService orgService;

	MockHttpSession admin;

	@BeforeEach
	void setUp() throws Exception {
		SharedAdmin.ensure(jdbc, encoder);
		admin = signIn(SharedAdmin.LOGIN, SharedAdmin.PASSWORD);
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

	private UUID newUser() {
		UUID user = UUID.randomUUID();
		jdbc.update("insert into users (id, login, password_hash, status, locale, must_change_password, created_at,"
				+ " updated_at) values (?, ?, ?, 'ACTIVE', 'en', false, now(), now())", user,
				"ms-user-" + user.toString().substring(0, 8), encoder.encode(PASSWORD));
		return user;
	}

	private MockHttpSession sessionOf(UUID user, String... permissionKeys) throws Exception {
		if (permissionKeys.length > 0) {
			UUID role = UUID.randomUUID();
			jdbc.update("insert into role (id, name, is_system, created_at, updated_at) values (?, ?, false, now(),"
					+ " now())", role, "ms-role-" + role);
			for (String key : permissionKeys) {
				jdbc.update("insert into role_permission (role_id, permission_key) values (?, ?)", role, key);
			}
			access.grantRole(user, role);
		}
		String login = jdbc.queryForObject("select login from users where id = ?", String.class, user);
		return signIn(login, PASSWORD);
	}

	private UUID unit(String name) throws Exception {
		MvcResult r = mvc.perform(post("/api/v1/org-units").with(csrf()).session(admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + " " + UUID.randomUUID() + "\",\"type\":\"WORKING_GROUP\"}"))
				.andExpect(status().isCreated()).andReturn();
		return UUID.fromString(JsonPath.read(r.getResponse().getContentAsString(), "$.id"));
	}

	private ResultActions send(MockHttpSession s, String path, String json) throws Exception {
		return mvc.perform(post("/api/v1" + path).with(csrf()).session(s).header(HttpHeaders.ACCEPT_LANGUAGE, "en")
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private UUID join(UUID unit, UUID user) throws Exception {
		MvcResult r = send(admin, "/org-units/" + unit + "/memberships", "{\"userId\":\"" + user + "\"}")
				.andExpect(status().isCreated()).andReturn();
		return UUID.fromString(JsonPath.read(r.getResponse().getContentAsString(), "$.id"));
	}

	private long audit(UUID id, String action) {
		return jdbc.queryForObject(
				"select count(*) from audit_log where entity_type = 'Membership' and entity_id = ? and action = ?",
				Long.class, id, action);
	}

	@Test
	void mutationsNeedOrgManageAndReadsNeedAuthenticationOnly() throws Exception {
		UUID unit = unit("Perm");
		UUID target = newUser();
		UUID membership = join(unit, newUser());
		MockHttpSession plain = sessionOf(newUser());
		MockHttpSession viewer = sessionOf(newUser(), "org.view");
		MockHttpSession manager = sessionOf(newUser(), "org.manage");
		String body = "{\"userId\":\"" + target + "\"}";

		mvc.perform(get("/api/v1/org-units/" + unit + "/memberships")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/org-units/" + unit + "/memberships").session(plain)).andExpect(status().isOk());
		mvc.perform(get("/api/v1/org-memberships/" + membership).session(plain)).andExpect(status().isOk());

		send(plain, "/org-units/" + unit + "/memberships", body).andExpect(status().isForbidden());
		send(viewer, "/org-units/" + unit + "/memberships", body).andExpect(status().isForbidden());
		send(viewer, "/org-memberships/" + membership + "/end", "{}").andExpect(status().isForbidden());
		send(viewer, "/org-memberships/" + membership + "/move", "{\"targetOrgUnitId\":\"" + unit + "\"}")
				.andExpect(status().isForbidden());
		send(manager, "/org-units/" + unit + "/memberships", body).andExpect(status().isCreated());
	}

	@Test
	void personMayBelongToSeveralUnitsButNotTwiceToTheSameOne() throws Exception {
		UUID a = unit("A");
		UUID b = unit("B");
		UUID user = newUser();

		UUID inA = join(a, user);
		join(b, user);
		send(admin, "/org-units/" + a + "/memberships", "{\"userId\":\"" + user + "\"}")
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MEMBERSHIP_DUPLICATE"));

		mvc.perform(get("/api/v1/org-memberships?userId=" + user).session(admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(2))).andExpect(jsonPath("$.totalElements").value(2));
		mvc.perform(get("/api/v1/org-units/" + a + "/memberships").session(admin))
				.andExpect(jsonPath("$.content", hasSize(1))).andExpect(jsonPath("$.content[0].userId").value(user.toString()))
				.andExpect(jsonPath("$.content[0].active").value(true));
		assertThat(query.unitIdsOfUser(user)).containsExactlyInAnyOrder(a, b);
		assertThat(query.memberIdsOfUnit(a)).containsExactly(user);
		assertThat(query.isMember(user, b)).isTrue();
		assertThat(audit(inA, "CREATE")).isEqualTo(1);
	}

	@Test
	void endingKeepsHistoryAndAllowsRejoining() throws Exception {
		UUID a = unit("A");
		UUID user = newUser();
		UUID first = join(a, user);

		send(admin, "/org-memberships/" + first + "/end", "").andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false)).andExpect(jsonPath("$.until").isNotEmpty());
		send(admin, "/org-memberships/" + first + "/end", "{}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("MEMBERSHIP_ALREADY_ENDED"));
		assertThat(audit(first, "UPDATE")).isEqualTo(1);
		assertThat(query.memberIdsOfUnit(a)).isEmpty();

		mvc.perform(get("/api/v1/org-units/" + a + "/memberships").session(admin))
				.andExpect(jsonPath("$.content", hasSize(0)));
		mvc.perform(get("/api/v1/org-units/" + a + "/memberships?includeEnded=true").session(admin))
				.andExpect(jsonPath("$.content", hasSize(1)));

		join(a, user); // allowed again after the previous one ended
		mvc.perform(get("/api/v1/org-units/" + a + "/memberships?includeEnded=true").session(admin))
				.andExpect(jsonPath("$.content", hasSize(2)));
		send(admin, "/org-memberships/" + first + "/end", "{\"until\":\"1999-01-01\"}")
				.andExpect(status().isConflict());
	}

	@Test
	void endBeforeSinceIsRejected() throws Exception {
		UUID a = unit("A");
		UUID user = newUser();
		MvcResult r = send(admin, "/org-units/" + a + "/memberships",
				"{\"userId\":\"" + user + "\",\"since\":\"2025-09-01\"}").andExpect(status().isCreated())
				.andReturn();
		UUID id = UUID.fromString(JsonPath.read(r.getResponse().getContentAsString(), "$.id"));

		send(admin, "/org-memberships/" + id + "/end", "{\"until\":\"2025-08-31\"}")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("MEMBERSHIP_INVALID_PERIOD"));
		send(admin, "/org-memberships/" + id + "/end", "{\"until\":\"2025-10-01\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.until").value("2025-10-01"));
	}

	@Test
	void moveCreatesEndAndStartRecords() throws Exception {
		UUID a = unit("A");
		UUID b = unit("B");
		UUID user = newUser();
		UUID old = join(a, user);

		MvcResult r = send(admin, "/org-memberships/" + old + "/move", "{\"targetOrgUnitId\":\"" + b + "\"}")
				.andExpect(status().isOk()).andExpect(jsonPath("$.orgUnitId").value(b.toString()))
				.andExpect(jsonPath("$.active").value(true)).andReturn();
		UUID created = UUID.fromString(JsonPath.read(r.getResponse().getContentAsString(), "$.id"));

		assertThat(created).isNotEqualTo(old);
		assertThat(query.unitIdsOfUser(user)).containsExactly(b);
		String oldUntil = jdbc.queryForObject("select until::text from org_membership where id = ?", String.class,
				old);
		String newSince = jdbc.queryForObject("select since::text from org_membership where id = ?", String.class,
				created);
		assertThat(oldUntil).isNotNull().isEqualTo(newSince);
		assertThat(audit(old, "UPDATE")).isEqualTo(1);
		assertThat(audit(created, "CREATE")).isEqualTo(1);

		send(admin, "/org-memberships/" + created + "/move", "{\"targetOrgUnitId\":\"" + b + "\"}")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("MEMBERSHIP_MOVE_SAME_UNIT"));
		send(admin, "/org-memberships/" + old + "/move", "{\"targetOrgUnitId\":\"" + b + "\"}")
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MEMBERSHIP_ALREADY_ENDED"));
	}

	@Test
	void unknownUserAndArchivedOrUnknownUnitAreRejected() throws Exception {
		UUID a = unit("A");
		UUID archived = unit("Gone");
		send(admin, "/org-units/" + archived + "/archive", "").andExpect(status().isOk());

		send(admin, "/org-units/" + a + "/memberships", "{\"userId\":\"" + UUID.randomUUID() + "\"}")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("MEMBERSHIP_USER_NOT_FOUND"));
		send(admin, "/org-units/" + archived + "/memberships", "{\"userId\":\"" + newUser() + "\"}")
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MEMBERSHIP_ORG_UNIT_ARCHIVED"));
		send(admin, "/org-units/" + UUID.randomUUID() + "/memberships", "{\"userId\":\"" + newUser() + "\"}")
				.andExpect(status().isNotFound());
		send(admin, "/org-units/" + a + "/memberships", "{}").andExpect(status().isBadRequest());
	}

	@Test
	void unitWithActiveMembershipsCannotBeArchivedUntilTheyEnd() throws Exception {
		UUID a = unit("A");
		UUID user = newUser();
		UUID membership = join(a, user);

		send(admin, "/org-units/" + a + "/archive", "").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ORG_UNIT_ARCHIVE_BLOCKED"));

		send(admin, "/org-memberships/" + membership + "/end", "").andExpect(status().isOk());
		send(admin, "/org-units/" + a + "/archive", "").andExpect(status().isOk());
	}

	@Test
	void archivingAMemberEndsAllTheirMembershipsWithAuditRows() throws Exception {
		UUID a = unit("A");
		UUID b = unit("B");
		UUID faculty = UUID.randomUUID();
		jdbc.update("insert into faculty (id, code, name_uk, name_en, created_at, updated_at) values (?, ?, ?, ?,"
				+ " now(), now())", faculty, "O" + faculty.toString().substring(0, 8), "Тест " + faculty,
				"Test " + faculty);
		String login = "fake." + UUID.randomUUID().toString().substring(0, 8);
		MvcResult created = mvc.perform(post("/api/v1/members").with(csrf()).session(admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"login\":\"" + login + "\",\"lastName\":\"Testenko\",\"firstName\":\"Test\","
						+ "\"facultyId\":\"" + faculty + "\",\"studyGroup\":\"XX-11\",\"telegram\":\"@fake_nick\","
						+ "\"joinedAt\":\"2025-09-01\"}"))
				.andExpect(status().isCreated()).andReturn();
		UUID member = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.member.id"));
		UUID m1 = join(a, member);
		UUID m2 = join(b, member);

		send(admin, "/members/" + member + "/archive", "").andExpect(status().isOk());

		assertThat(query.unitIdsOfUser(member)).isEmpty();
		assertThat(jdbc.queryForObject("select count(*) from org_membership where user_id = ? and until is not null",
				Long.class, member)).isEqualTo(2);
		assertThat(audit(m1, "UPDATE")).isEqualTo(1);
		assertThat(audit(m2, "UPDATE")).isEqualTo(1);
		// the units themselves are untouched and can now be archived
		send(admin, "/org-units/" + a + "/archive", "").andExpect(status().isOk());
	}

}
