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
import com.kpi.studentcouncil.crmtest.SharedAdmin;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class MemberIntegrationTests {

	private static final String PASSWORD = "Sup3r-secret-pw";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PasswordEncoder encoder;

	@Autowired
	AccessService access;

	MockHttpSession admin;

	UUID faculty;

	@BeforeEach
	void setUp() throws Exception {
		SharedAdmin.ensure(jdbc, encoder);
		admin = signIn(SharedAdmin.LOGIN, SharedAdmin.PASSWORD);
		faculty = newFaculty();
	}

	private UUID newFaculty() {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into faculty (id, code, name_uk, name_en, created_at, updated_at) values (?, ?, ?, ?,"
				+ " now(), now())", id, "M" + id.toString().substring(0, 8), "Тестовий " + id, "Test " + id);
		return id;
	}

	private ResultActions tryLogin(String login, String password) throws Exception {
		return mvc.perform(post("/api/v1/auth/login").with(csrf()).with(request -> {
			request.setRemoteAddr("10.9." + (int) (Math.random() * 250) + "." + (1 + (int) (Math.random() * 250)));
			return request;
		}).contentType(MediaType.APPLICATION_JSON)
				.content("{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}"));
	}

	private MockHttpSession signIn(String login, String password) throws Exception {
		MvcResult result = tryLogin(login, password).andReturn();
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private UUID role(String... permissionKeys) {
		UUID role = UUID.randomUUID();
		jdbc.update("insert into role (id, name, is_system, created_at, updated_at) values (?, ?, false, now(),"
				+ " now())", role, "mem-role-" + role);
		for (String key : permissionKeys) {
			jdbc.update("insert into role_permission (role_id, permission_key) values (?, ?)", role, key);
		}
		return role;
	}

	private MockHttpSession userWith(String... permissionKeys) throws Exception {
		UUID user = UUID.randomUUID();
		String login = "mem-user-" + user.toString().substring(0, 8);
		jdbc.update("insert into users (id, login, password_hash, status, locale, must_change_password, created_at,"
				+ " updated_at) values (?, ?, ?, 'ACTIVE', 'en', false, now(), now())", user, login,
				encoder.encode(PASSWORD));
		if (permissionKeys.length > 0) {
			access.grantRole(user, role(permissionKeys));
		}
		return signIn(login, PASSWORD);
	}

	private String createBody(String login, UUID facultyId, UUID roleId) {
		return "{\"login\":\"" + login + "\",\"lastName\":\"Testenko\",\"firstName\":\"Test\",\"facultyId\":\""
				+ facultyId + "\",\"studyGroup\":\"XX-11\",\"telegram\":\"@fake_nick\",\"joinedAt\":\"2025-09-01\""
				+ (roleId == null ? "" : ",\"roleId\":\"" + roleId + "\"") + "}";
	}

	private ResultActions postJson(MockHttpSession s, String path, String json) throws Exception {
		return mvc.perform(post("/api/v1/members" + path).with(csrf()).session(s)
				.header(HttpHeaders.ACCEPT_LANGUAGE, "en").contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private String uniqueLogin() {
		return "fake." + UUID.randomUUID().toString().substring(0, 8);
	}

	private record Created(UUID id, String login, String password) {
	}

	private Created createMember(String login) throws Exception {
		MvcResult r = postJson(admin, "", createBody(login, faculty, null)).andExpect(status().isCreated())
				.andReturn();
		String json = r.getResponse().getContentAsString();
		return new Created(UUID.fromString(JsonPath.read(json, "$.member.id")), login,
				JsonPath.read(json, "$.temporaryPassword"));
	}

	private long audit(String type, UUID id, String action) {
		return jdbc.queryForObject(
				"select count(*) from audit_log where entity_type = ? and entity_id = ? and action = ?", Long.class,
				type, id, action);
	}

	@Test
	void missingRequiredFieldsReturnValidationProblemListingFields() throws Exception {
		postJson(admin, "", "{\"login\":\"" + uniqueLogin() + "\",\"firstName\":\"Test\"}")
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.errors[?(@.field=='lastName')]").exists())
				.andExpect(jsonPath("$.errors[?(@.field=='facultyId')]").exists())
				.andExpect(jsonPath("$.errors[?(@.field=='studyGroup')]").exists())
				.andExpect(jsonPath("$.errors[?(@.field=='telegram')]").exists())
				.andExpect(jsonPath("$.errors[?(@.field=='joinedAt')]").exists());
	}

	@Test
	void permissionMatrix() throws Exception {
		Created member = createMember(uniqueLogin());
		MockHttpSession plain = userWith();
		MockHttpSession creator = userWith("members.create");
		MockHttpSession creatorWithAssign = userWith("members.create", "roles.assign");

		// base access: any authenticated user reads profiles
		mvc.perform(get("/api/v1/members").session(plain)).andExpect(status().isOk());
		mvc.perform(get("/api/v1/members/" + member.id()).session(plain)).andExpect(status().isOk())
				.andExpect(jsonPath("$.lastName").value("Testenko"));
		mvc.perform(get("/api/v1/members")).andExpect(status().isUnauthorized());

		// mutations
		postJson(plain, "", createBody(uniqueLogin(), faculty, null)).andExpect(status().isForbidden());
		mvc.perform(put("/api/v1/members/" + member.id()).with(csrf()).session(plain)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"lastName\":\"A\",\"firstName\":\"B\",\"facultyId\":\"" + faculty
						+ "\",\"studyGroup\":\"G\",\"telegram\":\"fake_nick\",\"joinedAt\":\"2025-09-01\"}"))
				.andExpect(status().isForbidden());
		postJson(plain, "/" + member.id() + "/archive", "").andExpect(status().isForbidden());
		postJson(plain, "/" + member.id() + "/restore", "").andExpect(status().isForbidden());
		postJson(plain, "/" + member.id() + "/reset-password", "").andExpect(status().isForbidden());
		postJson(creator, "/" + member.id() + "/archive", "").andExpect(status().isForbidden());

		postJson(creator, "", createBody(uniqueLogin(), faculty, null)).andExpect(status().isCreated());

		// an initial role needs roles.assign as well, and a refused request creates nothing
		UUID role = role("members.view");
		String blockedLogin = uniqueLogin();
		postJson(creator, "", createBody(blockedLogin, faculty, role)).andExpect(status().isForbidden());
		assertThat(jdbc.queryForObject("select count(*) from users where login = ?", Long.class, blockedLogin))
				.isZero();

		String assignedLogin = uniqueLogin();
		MvcResult r = postJson(creatorWithAssign, "", createBody(assignedLogin, faculty, role))
				.andExpect(status().isCreated()).andReturn();
		UUID assignedId = UUID.fromString(JsonPath.read(r.getResponse().getContentAsString(), "$.member.id"));
		assertThat(access.effectivePermissions(assignedId)).contains("members.view");
	}

	@Test
	void createdAccountMustChangePasswordAndTemporaryPasswordIsNeverAudited() throws Exception {
		String login = uniqueLogin();
		Created member = createMember(login);
		assertThat(member.password()).isNotBlank();

		assertThat(jdbc.queryForObject("select must_change_password from users where id = ?", Boolean.class,
				member.id())).isTrue();
		assertThat(jdbc.queryForObject("select password_hash from users where id = ?", String.class, member.id()))
				.isNotEqualTo(member.password()).startsWith("$2");

		MockHttpSession session = signIn(login, member.password());
		mvc.perform(get("/api/v1/members").session(session)).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

		// two accounts never share a temporary password
		assertThat(createMember(uniqueLogin()).password()).isNotEqualTo(member.password());

		assertThat(audit("MemberProfile", member.id(), "CREATE")).isEqualTo(1);
		assertThat(audit("User", member.id(), "CREATE")).isEqualTo(1);
		Long leaks = jdbc.queryForObject("select count(*) from audit_log where entity_id = ? and (before_state::text"
				+ " like '%' || ? || '%' or after_state::text like '%' || ? || '%')", Long.class, member.id(),
				member.password(), member.password());
		assertThat(leaks).isZero();
		Long hashes = jdbc.queryForObject("select count(*) from audit_log where entity_id = ? and (after_state::text"
				+ " like '%$2a$%' or after_state::text like '%password_hash%' or after_state::text like"
				+ " '%passwordHash%')", Long.class, member.id());
		assertThat(hashes).isZero();
	}

	@Test
	void duplicateLoginAndUnknownOrArchivedFacultyAreRejected() throws Exception {
		Created member = createMember(uniqueLogin());
		postJson(admin, "", createBody(member.login(), faculty, null)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("LOGIN_TAKEN"));
		postJson(admin, "", createBody(uniqueLogin(), UUID.randomUUID(), null)).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("FACULTY_NOT_SELECTABLE"));
		UUID archived = newFaculty();
		jdbc.update("update faculty set archived_at = now() where id = ?", archived);
		String login = uniqueLogin();
		postJson(admin, "", createBody(login, archived, null)).andExpect(status().isUnprocessableContent());
		assertThat(jdbc.queryForObject("select count(*) from users where login = ?", Long.class, login)).isZero();
	}

	@Test
	void editAndSelfEditEmitAuditAndRespectPermissions() throws Exception {
		Created member = createMember(uniqueLogin());
		String edit = "{\"lastName\":\"Changed\",\"firstName\":\"Test\",\"facultyId\":\"" + faculty
				+ "\",\"studyGroup\":\"YY-22\",\"telegram\":\"fake_nick2\",\"joinedAt\":\"2025-09-02\"}";
		mvc.perform(put("/api/v1/members/" + member.id()).with(csrf()).session(admin)
				.contentType(MediaType.APPLICATION_JSON).content(edit)).andExpect(status().isOk())
				.andExpect(jsonPath("$.lastName").value("Changed")).andExpect(jsonPath("$.studyGroup").value("YY-22"));
		assertThat(audit("MemberProfile", member.id(), "UPDATE")).isEqualTo(1);

		// the member edits own bio/telegram; the name stays untouched
		MockHttpSession self = signIn(member.login(), member.password());
		jdbc.update("update users set must_change_password = false where id = ?", member.id());
		mvc.perform(put("/api/v1/members/me").with(csrf()).session(self).contentType(MediaType.APPLICATION_JSON)
				.content("{\"telegram\":\"@self_nick\",\"bio\":\"About me\"}")).andExpect(status().isOk())
				.andExpect(jsonPath("$.telegram").value("self_nick")).andExpect(jsonPath("$.bio").value("About me"))
				.andExpect(jsonPath("$.lastName").value("Changed"));
		assertThat(audit("MemberProfile", member.id(), "UPDATE")).isEqualTo(2);
		// without members.edit the member cannot edit the full profile
		mvc.perform(put("/api/v1/members/" + member.id()).with(csrf()).session(self)
				.contentType(MediaType.APPLICATION_JSON).content(edit)).andExpect(status().isForbidden());
		// unknown avatar file
		mvc.perform(put("/api/v1/members/me").with(csrf()).session(self).contentType(MediaType.APPLICATION_JSON)
				.content("{\"telegram\":\"@self_nick\",\"avatarFileId\":\"" + UUID.randomUUID() + "\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void archiveBlocksSignInImmediatelyKeepsDataAndRestoreReopensIt() throws Exception {
		Created member = createMember(uniqueLogin());
		MockHttpSession session = signIn(member.login(), member.password());
		mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isOk());

		postJson(admin, "/" + member.id() + "/archive", "").andExpect(status().isOk())
				.andExpect(jsonPath("$.archived").value(true));

		// the already open session stops working right away, new sign-ins fail
		mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isUnauthorized());
		tryLogin(member.login(), member.password()).andExpect(status().isUnauthorized());

		// nothing is deleted and the member stays readable for historical references
		assertThat(jdbc.queryForObject("select count(*) from users where id = ?", Long.class, member.id()))
				.isEqualTo(1);
		assertThat(jdbc.queryForObject("select archived_at is not null from member_profile where user_id = ?",
				Boolean.class, member.id())).isTrue();
		mvc.perform(get("/api/v1/members/" + member.id()).session(admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.archived").value(true));
		assertThat(audit("MemberProfile", member.id(), "ARCHIVE")).isEqualTo(1);
		assertThat(audit("User", member.id(), "ARCHIVE")).isEqualTo(1);

		// archived: hidden from the default list, no edits, no password reset, no second archive
		mvc.perform(get("/api/v1/members?size=100").session(admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.content[?(@.id=='" + member.id() + "')]").isEmpty());
		mvc.perform(get("/api/v1/members?size=100&includeArchived=true").session(admin))
				.andExpect(jsonPath("$.content[?(@.id=='" + member.id() + "')]").isNotEmpty());
		postJson(admin, "/" + member.id() + "/reset-password", "").andExpect(status().isConflict());
		postJson(admin, "/" + member.id() + "/archive", "").andExpect(status().isConflict());

		postJson(admin, "/" + member.id() + "/restore", "").andExpect(status().isOk())
				.andExpect(jsonPath("$.archived").value(false));
		signIn(member.login(), member.password());
		assertThat(audit("MemberProfile", member.id(), "RESTORE")).isEqualTo(1);
	}

	@Test
	void adminCannotArchiveOwnAccount() throws Exception {
		UUID adminId = jdbc.queryForObject("select id from users where login = ?", UUID.class, SharedAdmin.LOGIN);
		// the shared admin may have no profile; make one so that the rule (not 404) is exercised
		jdbc.update("insert into member_profile (id, user_id, last_name, first_name, faculty_id, study_group,"
				+ " telegram, joined_at, created_at, updated_at) select ?, ?, 'Admin', 'Fake', ?, 'XX-00',"
				+ " 'admin_fake', current_date, now(), now() where not exists (select 1 from member_profile where"
				+ " user_id = ?)", UUID.randomUUID(), adminId, faculty, adminId);
		postJson(admin, "/" + adminId + "/archive", "").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("CANNOT_ARCHIVE_SELF"));
	}

	@Test
	void passwordResetIssuesNewTemporaryPasswordOnce() throws Exception {
		Created member = createMember(uniqueLogin());
		jdbc.update("update users set must_change_password = false where id = ?", member.id());
		MvcResult r = postJson(admin, "/" + member.id() + "/reset-password", "").andExpect(status().isOk())
				.andReturn();
		String fresh = JsonPath.read(r.getResponse().getContentAsString(), "$.temporaryPassword");
		assertThat(fresh).isNotEqualTo(member.password());
		assertThat(r.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
		tryLogin(member.login(), member.password()).andExpect(status().isUnauthorized());
		signIn(member.login(), fresh);
		assertThat(jdbc.queryForObject("select must_change_password from users where id = ?", Boolean.class,
				member.id())).isTrue();
		assertThat(audit("User", member.id(), "PASSWORD_CHANGE")).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_id = ? and after_state::text like"
				+ " '%' || ? || '%'", Long.class, member.id(), fresh)).isZero();
		// the response of a plain GET never contains a password
		mvc.perform(get("/api/v1/members/" + member.id()).session(admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.temporaryPassword").doesNotExist());
	}

}
