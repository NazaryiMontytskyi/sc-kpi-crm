package com.kpi.studentcouncil.crm.members.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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

/** Activity level API: permission, history, audit, append-only guarantee, directory filter (fake data only). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ActivityLevelIntegrationTests {

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

	private final List<UUID> users = new ArrayList<>();
	private final List<UUID> roles = new ArrayList<>();
	private final List<UUID> levels = new ArrayList<>();
	private final List<UUID> faculties = new ArrayList<>();

	@BeforeEach
	void setUp() throws Exception {
		SharedAdmin.ensure(jdbc, encoder);
		admin = signIn(SharedAdmin.LOGIN, SharedAdmin.PASSWORD);
	}

	/** FK-ordered cleanup. The immutability trigger is disabled only inside one transactional statement batch. */
	@AfterEach
	void cleanUp() {
		StringBuilder sql = new StringBuilder(
				"alter table activity_level_change disable trigger trg_activity_level_change_immutable;");
		for (UUID user : users) {
			sql.append("delete from activity_level_change where member_id = '").append(user).append("';");
		}
		for (UUID level : levels) {
			sql.append("delete from activity_level_change where to_level_id = '").append(level)
					.append("' or from_level_id = '").append(level).append("';");
		}
		sql.append("alter table activity_level_change enable trigger trg_activity_level_change_immutable;");
		jdbc.execute(sql.toString());
		for (UUID user : users) {
			jdbc.update("delete from member_profile where user_id = ?", user);
			jdbc.update("delete from role_assignment where user_id = ?", user);
			jdbc.update("delete from users where id = ?", user);
		}
		for (UUID role : roles) {
			jdbc.update("delete from role_permission where role_id = ?", role);
			jdbc.update("delete from role where id = ?", role);
		}
		for (UUID level : levels) {
			jdbc.update("delete from activity_level_definition where id = ?", level);
		}
		for (UUID faculty : faculties) {
			jdbc.update("delete from faculty where id = ?", faculty);
		}
		users.clear();
		roles.clear();
		levels.clear();
		faculties.clear();
	}

	private MockHttpSession signIn(String login, String password) throws Exception {
		MvcResult result = mvc.perform(post("/api/v1/auth/login").with(csrf()).with(request -> {
			request.setRemoteAddr("10.7." + (int) (Math.random() * 250) + "." + (1 + (int) (Math.random() * 250)));
			return request;
		}).contentType(MediaType.APPLICATION_JSON)
				.content("{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}")).andReturn();
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private MockHttpSession userWith(String... permissionKeys) throws Exception {
		UUID user = newUser();
		String login = jdbc.queryForObject("select login from users where id = ?", String.class, user);
		if (permissionKeys.length > 0) {
			UUID role = UUID.randomUUID();
			jdbc.update("insert into role (id, name, is_system, created_at, updated_at) values (?, ?, false, now(),"
					+ " now())", role, "al-role-" + role);
			roles.add(role);
			for (String key : permissionKeys) {
				jdbc.update("insert into role_permission (role_id, permission_key) values (?, ?)", role, key);
			}
			access.grantRole(user, role);
		}
		return signIn(login, PASSWORD);
	}

	private UUID newUser() {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into users (id, login, password_hash, status, locale, must_change_password, created_at,"
				+ " updated_at) values (?, ?, ?, 'ACTIVE', 'en', false, now(), now())", id,
				"al-user-" + id.toString().substring(0, 12), encoder.encode(PASSWORD));
		users.add(id);
		return id;
	}

	private UUID member(boolean archived) {
		UUID faculty = UUID.randomUUID();
		jdbc.update("insert into faculty (id, code, name_uk, name_en, created_at, updated_at) values (?, ?, ?, ?,"
				+ " now(), now())", faculty, "AL" + faculty.toString().substring(0, 8), "Тест " + faculty,
				"Test " + faculty);
		faculties.add(faculty);
		UUID user = newUser();
		jdbc.update("insert into member_profile (id, user_id, last_name, first_name, faculty_id, study_group,"
				+ " telegram, joined_at, created_at, updated_at, archived_at) values (?, ?, ?, 'Test', ?, 'XX-11',"
				+ " 'fake_nick', current_date, now(), now(), " + (archived ? "now()" : "null") + ")",
				UUID.randomUUID(), user, "Alvl" + user.toString().substring(0, 8).replaceAll("[0-9]", "x"), faculty);
		return user;
	}

	private UUID level(String suffix) throws Exception {
		String code = "T" + UUID.randomUUID().toString().substring(0, 8) + suffix;
		MvcResult r = mvc.perform(post("/api/v1/activity-levels").with(csrf()).session(admin)
				.contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\",\"labelUk\":\"Тест "
						+ code + "\",\"labelEn\":\"Test " + code + "\",\"sortOrder\":999}"))
				.andExpect(status().isCreated()).andReturn();
		UUID id = UUID.fromString(JsonPath.read(r.getResponse().getContentAsString(), "$.id"));
		levels.add(id);
		return id;
	}

	private ResultActions setLevel(MockHttpSession s, UUID member, UUID level, String comment) throws Exception {
		return mvc.perform(put("/api/v1/members/" + member + "/activity-level").with(csrf()).session(s)
				.contentType(MediaType.APPLICATION_JSON).content("{\"levelId\":\"" + level + "\""
						+ (comment == null ? "" : ",\"comment\":\"" + comment + "\"") + "}"));
	}

	private long audit(String type, String action) {
		return jdbc.queryForObject("select count(*) from audit_log where entity_type = ? and action = ?",
				Long.class, type, action);
	}

	@Test
	void setWithoutPermissionIsForbiddenAndWithPermissionWritesHistoryAndAudit() throws Exception {
		UUID member = member(false);
		UUID a = level("A");
		MockHttpSession noPerm = userWith("members.view");
		setLevel(noPerm, member, a, null).andExpect(status().isForbidden());
		assertThat(jdbc.queryForObject("select count(*) from activity_level_change where member_id = ?", Long.class,
				member)).isZero();

		MockHttpSession hr = userWith("members.activity.set");
		long auditBefore = audit("ActivityLevelChange", "CREATE");
		setLevel(hr, member, a, "initial").andExpect(status().isOk()).andExpect(jsonPath("$.to.id").value(a.toString()))
				.andExpect(jsonPath("$.from").doesNotExist()).andExpect(jsonPath("$.comment").value("initial"));
		assertThat(audit("ActivityLevelChange", "CREATE")).isEqualTo(auditBefore + 1);
		assertThat(jdbc.queryForObject("select activity_level_id from member_profile where user_id = ?", UUID.class,
				member)).isEqualTo(a);

		UUID b = level("B");
		setLevel(hr, member, b, null).andExpect(status().isOk()).andExpect(jsonPath("$.from.id").value(a.toString()));
		mvc.perform(get("/api/v1/members/" + member + "/activity-level-history").session(noPerm))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.content[0].to.id").value(b.toString()))
				.andExpect(jsonPath("$.content[1].to.id").value(a.toString()));
		mvc.perform(get("/api/v1/members/" + member).session(noPerm)).andExpect(status().isOk())
				.andExpect(jsonPath("$.activityLevel.id").value(b.toString()));
	}

	@Test
	void sameArchivedAndUnknownLevelsAreRejected() throws Exception {
		UUID member = member(false);
		UUID a = level("A");
		setLevel(admin, member, a, null).andExpect(status().isOk());
		setLevel(admin, member, a, null).andExpect(status().isUnprocessableContent());
		setLevel(admin, member, UUID.randomUUID(), null).andExpect(status().isUnprocessableContent());

		UUID b = level("B");
		mvc.perform(post("/api/v1/activity-levels/" + b + "/archive").with(csrf()).session(admin))
				.andExpect(status().isOk());
		setLevel(admin, member, b, null).andExpect(status().isUnprocessableContent());

		UUID archivedMember = member(true);
		setLevel(admin, archivedMember, a, null).andExpect(status().isConflict());
	}

	@Test
	void definitionsAreDataAndNeedSettingsManageToEdit() throws Exception {
		MockHttpSession reader = userWith();
		mvc.perform(get("/api/v1/activity-levels").session(reader)).andExpect(status().isOk());
		mvc.perform(post("/api/v1/activity-levels").with(csrf()).session(reader)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"X\",\"labelUk\":\"Х\",\"labelEn\":\"X\",\"sortOrder\":1}"))
				.andExpect(status().isForbidden());
		UUID a = level("A");
		mvc.perform(post("/api/v1/activity-levels").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
				.content(jdbc.queryForObject("select '{\"code\":\"' || code || '\",\"labelUk\":\"Д\",\"labelEn\":\"D\","
						+ "\"sortOrder\":1}' from activity_level_definition where id = ?", String.class, a)))
				.andExpect(status().isConflict());
		mvc.perform(put("/api/v1/activity-levels/" + a).with(csrf()).session(admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"RENAMED" + a.toString().substring(0, 6)
						+ "\",\"labelUk\":\"Новий\",\"labelEn\":\"New\",\"sortOrder\":5}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.labelEn").value("New"));
	}

	@Test
	void historyIsAppendOnlyInTheDatabase() throws Exception {
		UUID member = member(false);
		UUID a = level("A");
		setLevel(admin, member, a, null).andExpect(status().isOk());
		org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataAccessException.class,
				() -> jdbc.update("update activity_level_change set comment = 'x' where member_id = ?", member));
		org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataAccessException.class,
				() -> jdbc.update("delete from activity_level_change where member_id = ?", member));
	}

	@Test
	void directoryFiltersByLevel() throws Exception {
		UUID m1 = member(false);
		UUID m2 = member(false);
		UUID a = level("A");
		setLevel(admin, m1, a, null).andExpect(status().isOk());
		MvcResult r = mvc.perform(get("/api/v1/members?activityLevelId=" + a + "&size=50").session(admin))
				.andExpect(status().isOk()).andReturn();
		List<String> ids = JsonPath.read(r.getResponse().getContentAsString(), "$.content[*].id");
		assertThat(ids).containsExactly(m1.toString()).doesNotContain(m2.toString());
	}

}
