package com.kpi.studentcouncil.crm.org.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.kpi.studentcouncil.crm.org.OrgQueryService;
import com.kpi.studentcouncil.crmtest.SharedAdmin;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PositionIntegrationTests {

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

	MockHttpSession admin;

	@BeforeEach
	void setUp() throws Exception {
		SharedAdmin.ensure(jdbc, encoder);
		admin = signIn(SharedAdmin.LOGIN, SharedAdmin.PASSWORD);
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

	private UUID newUser() {
		UUID user = UUID.randomUUID();
		jdbc.update("insert into users (id, login, password_hash, status, locale, must_change_password, created_at,"
				+ " updated_at) values (?, ?, ?, 'ACTIVE', 'en', false, now(), now())", user,
				"pos-user-" + user.toString().substring(0, 8), encoder.encode(PASSWORD));
		return user;
	}

	private UUID newRole(String... permissionKeys) {
		UUID role = UUID.randomUUID();
		jdbc.update("insert into role (id, name, is_system, created_at, updated_at) values (?, ?, false, now(),"
				+ " now())", role, "pos-role-" + role);
		for (String key : permissionKeys) {
			jdbc.update("insert into role_permission (role_id, permission_key) values (?, ?)", role, key);
		}
		return role;
	}

	private MockHttpSession sessionOf(UUID user, String... permissionKeys) throws Exception {
		if (permissionKeys.length > 0) {
			access.grantRole(user, newRole(permissionKeys));
		}
		String login = jdbc.queryForObject("select login from users where id = ?", String.class, user);
		return signIn(login, PASSWORD);
	}

	private ResultActions send(MockHttpSession s, String path, String json) throws Exception {
		return mvc.perform(post("/api/v1" + path).with(csrf()).session(s).header(HttpHeaders.ACCEPT_LANGUAGE, "en")
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private UUID id(ResultActions r) throws Exception {
		return UUID.fromString(JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id"));
	}

	private UUID unit() throws Exception {
		return id(mvc.perform(post("/api/v1/org-units").with(csrf()).session(admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Unit " + UUID.randomUUID() + "\",\"type\":\"WORKING_GROUP\"}"))
				.andExpect(status().isCreated()));
	}

	private UUID position(UUID unit, String title, boolean head, Integer capacity, UUID... roles) throws Exception {
		StringBuilder roleJson = new StringBuilder();
		for (UUID r : roles) {
			roleJson.append(roleJson.isEmpty() ? "" : ",").append('"').append(r).append('"');
		}
		String body = "{\"title\":\"" + title + "\",\"isHead\":" + head
				+ (capacity == null ? "" : ",\"capacity\":" + capacity) + ",\"defaultRoleIds\":[" + roleJson + "]}";
		return id(send(admin, "/org-units/" + unit + "/positions", body).andExpect(status().isCreated()));
	}

	private UUID appoint(UUID position, UUID user) throws Exception {
		return id(send(admin, "/positions/" + position + "/appointments", "{\"userId\":\"" + user + "\"}")
				.andExpect(status().isCreated()));
	}

	private long audit(String type, UUID id, String action) {
		return jdbc.queryForObject(
				"select count(*) from audit_log where entity_type = ? and entity_id = ? and action = ?", Long.class,
				type, id, action);
	}

	@Test
	void managePositionsAndAppointAreSeparatePermissions() throws Exception {
		UUID unit = unit();
		UUID position = position(unit, "Chair", false, null);
		UUID target = newUser();
		MockHttpSession plain = sessionOf(newUser());
		MockHttpSession manager = sessionOf(newUser(), "positions.manage");
		MockHttpSession appointer = sessionOf(newUser(), "positions.appoint");
		String appointBody = "{\"userId\":\"" + target + "\"}";
		String positionBody = "{\"title\":\"Deputy\"}";

		mvc.perform(get("/api/v1/org-units/" + unit + "/positions")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/org-units/" + unit + "/positions").session(plain)).andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1))).andExpect(jsonPath("$.content[0].vacant").value(true));
		mvc.perform(get("/api/v1/positions/" + position).session(plain)).andExpect(status().isOk());

		send(plain, "/org-units/" + unit + "/positions", positionBody).andExpect(status().isForbidden());
		send(appointer, "/org-units/" + unit + "/positions", positionBody).andExpect(status().isForbidden());
		send(manager, "/positions/" + position + "/appointments", appointBody).andExpect(status().isForbidden());
		mvc.perform(put("/api/v1/positions/" + position).with(csrf()).session(appointer)
				.contentType(MediaType.APPLICATION_JSON).content(positionBody)).andExpect(status().isForbidden());
		send(appointer, "/positions/" + position + "/archive", "").andExpect(status().isForbidden());

		send(manager, "/org-units/" + unit + "/positions", positionBody).andExpect(status().isCreated());
		UUID appointment = id(send(appointer, "/positions/" + position + "/appointments", appointBody)
				.andExpect(status().isCreated()));
		send(manager, "/appointments/" + appointment + "/end", "{}").andExpect(status().isForbidden());
		send(appointer, "/appointments/" + appointment + "/end", "{}").andExpect(status().isOk());
	}

	@Test
	void appointingGrantsBoundRolesAndEndingRevokesThem() throws Exception {
		UUID unit = unit();
		UUID role = newRole("tasks.view");
		UUID position = position(unit, "Secretary", false, null, role);
		UUID user = newUser();

		UUID appointment = appoint(position, user);
		assertThat(access.hasPermission(user, "tasks.view")).isTrue();
		assertThat(audit("Appointment", appointment, "APPOINT")).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_type = 'RoleAssignment'"
				+ " and action = 'ROLE_CHANGE' and after_state::text like ?", Long.class,
				"%" + appointment + "%")).isEqualTo(1);

		send(admin, "/appointments/" + appointment + "/end", "").andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false)).andExpect(jsonPath("$.endDate").isNotEmpty());
		assertThat(access.hasPermission(user, "tasks.view")).isFalse();
		assertThat(audit("Appointment", appointment, "DISMISS")).isEqualTo(1);
		send(admin, "/appointments/" + appointment + "/end", "{}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("APPOINTMENT_ALREADY_ENDED"));
	}

	@Test
	void roleFromAnotherSourceSurvivesTheEndOfAnAppointment() throws Exception {
		UUID unit = unit();
		UUID role = newRole("tasks.view");
		UUID p1 = position(unit, "One", false, null, role);
		UUID p2 = position(unit, "Two", false, null, role);
		UUID user = newUser();

		// manual grant through the INC-012 endpoint plus two appointments binding the same role
		mvc.perform(put("/api/v1/users/" + user + "/roles/" + role).with(csrf()).session(admin))
				.andExpect(status().isNoContent());
		UUID a1 = appoint(p1, user);
		UUID a2 = appoint(p2, user);
		assertThat(jdbc.queryForObject("select count(*) from role_assignment where user_id = ? and archived_at is null",
				Long.class, user)).isEqualTo(3);

		send(admin, "/appointments/" + a1 + "/end", "").andExpect(status().isOk());
		assertThat(access.hasPermission(user, "tasks.view")).isTrue();
		send(admin, "/appointments/" + a2 + "/end", "").andExpect(status().isOk());
		assertThat(access.hasPermission(user, "tasks.view")).isTrue(); // the manual grant stays

		// manual revoke removes only the manual grant
		UUID a3 = appoint(p1, user);
		mvc.perform(delete("/api/v1/users/" + user + "/roles/" + role).with(csrf()).session(admin))
				.andExpect(status().isNoContent());
		assertThat(access.hasPermission(user, "tasks.view")).isTrue(); // still held through the appointment
		send(admin, "/appointments/" + a3 + "/end", "").andExpect(status().isOk());
		assertThat(access.hasPermission(user, "tasks.view")).isFalse();
	}

	@Test
	void historyKeepsPreviousHoldersWithDatesAndEndedRecordsAreNeverDeleted() throws Exception {
		UUID unit = unit();
		UUID position = position(unit, "Treasurer", false, null);
		UUID first = newUser();
		UUID second = newUser();

		send(admin, "/positions/" + position + "/appointments",
				"{\"userId\":\"" + first + "\",\"startDate\":\"2025-09-01\"}").andExpect(status().isCreated());
		UUID a1 = jdbc.queryForObject("select id from org_appointment where user_id = ?", UUID.class, first);
		send(admin, "/appointments/" + a1 + "/end", "{\"endDate\":\"2026-01-31\"}").andExpect(status().isOk());
		appoint(position, second);

		mvc.perform(get("/api/v1/positions/" + position + "/appointments").session(admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(2))).andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content[1].userId").value(first.toString()))
				.andExpect(jsonPath("$.content[1].startDate").value("2025-09-01"))
				.andExpect(jsonPath("$.content[1].endDate").value("2026-01-31"))
				.andExpect(jsonPath("$.content[0].userId").value(second.toString()))
				.andExpect(jsonPath("$.content[0].active").value(true));
		mvc.perform(get("/api/v1/positions/" + position + "/appointments?includeEnded=false").session(admin))
				.andExpect(jsonPath("$.content", hasSize(1)));
		mvc.perform(get("/api/v1/appointments?userId=" + first + "&includeEnded=true").session(admin))
				.andExpect(jsonPath("$.content", hasSize(1)));
		assertThat(jdbc.queryForObject("select count(*) from org_appointment where position_id = ?", Long.class,
				position)).isEqualTo(2);
	}

	@Test
	void capacityAndDuplicateRulesAreEnforced() throws Exception {
		UUID unit = unit();
		UUID single = position(unit, "Single", false, null);
		UUID duo = position(unit, "Duo", false, 2);
		UUID u1 = newUser();
		UUID u2 = newUser();
		UUID u3 = newUser();

		appoint(single, u1);
		send(admin, "/positions/" + single + "/appointments", "{\"userId\":\"" + u2 + "\"}")
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POSITION_FULL"));

		appoint(duo, u1);
		send(admin, "/positions/" + duo + "/appointments", "{\"userId\":\"" + u1 + "\"}")
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPOINTMENT_DUPLICATE"));
		appoint(duo, u2);
		send(admin, "/positions/" + duo + "/appointments", "{\"userId\":\"" + u3 + "\"}")
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POSITION_FULL"));
		mvc.perform(get("/api/v1/positions/" + duo).session(admin)).andExpect(jsonPath("$.activeHolderIds", hasSize(2)))
				.andExpect(jsonPath("$.vacant").value(false));
		mvc.perform(put("/api/v1/positions/" + duo).with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Duo\",\"capacity\":1}")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("POSITION_CAPACITY_BELOW_HOLDERS"));
	}

	@Test
	void headAppointmentSetsTheUnitHeadAndOnlyOneHeadPositionExists() throws Exception {
		UUID unit = unit();
		UUID head = position(unit, "Head", true, null);
		UUID user = newUser();

		assertThat(query.headOfUnit(unit)).isEmpty();
		send(admin, "/org-units/" + unit + "/positions", "{\"title\":\"Head 2\",\"isHead\":true}")
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POSITION_HEAD_EXISTS"));
		send(admin, "/org-units/" + unit + "/positions", "{\"title\":\"Head 3\",\"isHead\":true,\"capacity\":2}")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("POSITION_HEAD_SINGLE_HOLDER"));

		UUID appointment = appoint(head, user);
		assertThat(query.headOfUnit(unit)).contains(user);
		send(admin, "/appointments/" + appointment + "/end", "").andExpect(status().isOk());
		assertThat(query.headOfUnit(unit)).isEmpty();
	}

	@Test
	void adminRoleCannotBeBoundToAPosition() throws Exception {
		UUID unit = unit();
		send(admin, "/org-units/" + unit + "/positions", "{\"title\":\"Boss\",\"defaultRoleIds\":[\""
				+ AccessService.ADMIN_ROLE_ID + "\"]}").andExpect(status().isForbidden());
		send(admin, "/org-units/" + unit + "/positions", "{\"title\":\"Ghost\",\"defaultRoleIds\":[\""
				+ UUID.randomUUID() + "\"]}").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("POSITION_ROLE_UNAVAILABLE"));
	}

	@Test
	void changingBoundRolesAppliesToCurrentHolders() throws Exception {
		UUID unit = unit();
		UUID roleA = newRole("tasks.view");
		UUID roleB = newRole("org.view");
		UUID position = position(unit, "Chair", false, null, roleA);
		UUID user = newUser();
		appoint(position, user);

		mvc.perform(put("/api/v1/positions/" + position).with(csrf()).session(admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Chair\",\"defaultRoleIds\":[\"" + roleB + "\"]}")).andExpect(status().isOk());

		assertThat(access.hasPermission(user, "tasks.view")).isFalse();
		assertThat(access.hasPermission(user, "org.view")).isTrue();
	}

	@Test
	void positionArchiveRulesAndAuditRows() throws Exception {
		UUID unit = unit();
		UUID position = position(unit, "Chair", false, null);
		UUID user = newUser();
		UUID appointment = appoint(position, user);

		send(admin, "/positions/" + position + "/archive", "").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("POSITION_HAS_ACTIVE_APPOINTMENTS"));
		send(admin, "/appointments/" + appointment + "/end", "").andExpect(status().isOk());
		send(admin, "/positions/" + position + "/archive", "").andExpect(status().isOk())
				.andExpect(jsonPath("$.archived").value(true));
		send(admin, "/positions/" + position + "/appointments", "{\"userId\":\"" + newUser() + "\"}")
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POSITION_ARCHIVED"));
		mvc.perform(get("/api/v1/org-units/" + unit + "/positions").session(admin))
				.andExpect(jsonPath("$.content", hasSize(0)));
		mvc.perform(get("/api/v1/org-units/" + unit + "/positions?includeArchived=true").session(admin))
				.andExpect(jsonPath("$.content", hasSize(1)));
		send(admin, "/positions/" + position + "/restore", "").andExpect(status().isOk());

		assertThat(audit("Position", position, "CREATE")).isEqualTo(1);
		assertThat(audit("Position", position, "ARCHIVE")).isEqualTo(1);
		assertThat(audit("Position", position, "RESTORE")).isEqualTo(1);
	}

	@Test
	void unitWithActiveAppointmentsCannotBeArchivedAndArchivedUnitRefusesPositions() throws Exception {
		UUID unit = unit();
		UUID position = position(unit, "Chair", false, null);
		UUID appointment = appoint(position, newUser());

		send(admin, "/org-units/" + unit + "/archive", "").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ORG_UNIT_ARCHIVE_BLOCKED"));
		send(admin, "/appointments/" + appointment + "/end", "").andExpect(status().isOk());
		send(admin, "/org-units/" + unit + "/archive", "").andExpect(status().isOk());

		send(admin, "/org-units/" + unit + "/positions", "{\"title\":\"Late\"}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ORG_UNIT_ARCHIVED"));
		send(admin, "/positions/" + position + "/appointments", "{\"userId\":\"" + newUser() + "\"}")
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORG_UNIT_ARCHIVED"));
	}

	@Test
	void archivingAMemberEndsTheirAppointmentsAndRevokesTheRoles() throws Exception {
		UUID unit = unit();
		UUID role = newRole("tasks.view");
		UUID position = position(unit, "Secretary", false, null, role);
		UUID faculty = UUID.randomUUID();
		jdbc.update("insert into faculty (id, code, name_uk, name_en, created_at, updated_at) values (?, ?, ?, ?,"
				+ " now(), now())", faculty, "P" + faculty.toString().substring(0, 8), "Test " + faculty,
				"Test " + faculty);
		String login = "fake." + UUID.randomUUID().toString().substring(0, 8);
		MvcResult created = mvc.perform(post("/api/v1/members").with(csrf()).session(admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"login\":\"" + login + "\",\"lastName\":\"Testenko\",\"firstName\":\"Test\","
						+ "\"facultyId\":\"" + faculty + "\",\"studyGroup\":\"XX-11\",\"telegram\":\"@fake_nick\","
						+ "\"joinedAt\":\"2025-09-01\"}"))
				.andExpect(status().isCreated()).andReturn();
		UUID member = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.member.id"));
		UUID appointment = appoint(position, member);
		assertThat(access.hasPermission(member, "tasks.view")).isTrue();

		send(admin, "/members/" + member + "/archive", "").andExpect(status().isOk());

		assertThat(access.hasPermission(member, "tasks.view")).isFalse();
		assertThat(jdbc.queryForObject("select end_date is not null from org_appointment where id = ?",
				Boolean.class, appointment)).isTrue();
		assertThat(audit("Appointment", appointment, "DISMISS")).isEqualTo(1);
		mvc.perform(get("/api/v1/positions/" + position).session(admin)).andExpect(jsonPath("$.vacant").value(true));
	}

	@Test
	void validationAndNotFound() throws Exception {
		UUID unit = unit();
		UUID position = position(unit, "Chair", false, null);
		send(admin, "/org-units/" + unit + "/positions", "{\"title\":\" \"}").andExpect(status().isBadRequest());
		send(admin, "/org-units/" + UUID.randomUUID() + "/positions", "{\"title\":\"X\"}")
				.andExpect(status().isNotFound());
		send(admin, "/positions/" + position + "/appointments", "{}").andExpect(status().isBadRequest());
		send(admin, "/positions/" + position + "/appointments", "{\"userId\":\"" + UUID.randomUUID() + "\"}")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("APPOINTMENT_USER_NOT_FOUND"));
		send(admin, "/positions/" + position + "/appointments",
				"{\"userId\":\"" + newUser() + "\",\"startDate\":\"2999-01-01\"}")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("APPOINTMENT_DATE_IN_FUTURE"));
		mvc.perform(get("/api/v1/positions/" + UUID.randomUUID()).session(admin)).andExpect(status().isNotFound());
	}

}
