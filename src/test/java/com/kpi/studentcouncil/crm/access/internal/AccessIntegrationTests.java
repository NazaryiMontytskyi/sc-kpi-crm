package com.kpi.studentcouncil.crm.access.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
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

import com.kpi.studentcouncil.crm.TestcontainersConfiguration;
import com.kpi.studentcouncil.crm.access.AccessService;
import com.kpi.studentcouncil.crm.access.Permission;
import com.kpi.studentcouncil.crmtest.access.TestAccessController;

@SpringBootTest(properties = { "crm.bootstrap.admin.login=Root.Admin", "crm.bootstrap.admin.password=Bootstrap-pass-42" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, TestAccessController.class })
class AccessIntegrationTests {

	private static final String PASSWORD = "Sup3r-secret-pw";

	private static final String INSERT_USER = "insert into users (id, login, password_hash, status, locale,"
			+ " must_change_password, created_at, updated_at) values (?, ?, ?, 'ACTIVE', 'en', ?, now(), now())";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PasswordEncoder encoder;

	@Autowired
	AccessService access;

	@Autowired
	RoleService roleService;

	private UUID createUser(String prefix) {
		UUID id = UUID.randomUUID();
		jdbc.update(INSERT_USER, id, prefix + "-" + id.toString().substring(0, 8), encoder.encode(PASSWORD), false);
		return id;
	}

	private String loginOf(UUID id) {
		return jdbc.queryForObject("select login from users where id = ?", String.class, id);
	}

	private MockHttpSession signIn(String login, String password) throws Exception {
		MvcResult result = mvc.perform(post("/api/v1/auth/login").with(csrf()).with(request -> {
			request.setRemoteAddr("10.2." + (int) (Math.random() * 250) + "." + (1 + (int) (Math.random() * 250)));
			return request;
		}).contentType(MediaType.APPLICATION_JSON)
				.content("{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}")).andReturn();
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private Role newRole(String name, Permission... permissions) {
		return roleService.create(name + "-" + UUID.randomUUID().toString().substring(0, 6), null,
				Set.of(Arrays.stream(permissions).map(Permission::key).toArray(String[]::new)));
	}

	@Test
	void userWithoutRolesIsAuthenticatedButHasNoPermissionsAndGets403Problem() throws Exception {
		UUID id = createUser("plain");
		MockHttpSession session = signIn(loginOf(id), PASSWORD);

		mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isOk())
				.andExpect(jsonPath("$.permissions", hasSize(0)));
		mvc.perform(get("/api/v1/test-access/activity").session(session).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("FORBIDDEN"))
				.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void twoRolesGiveUnionInMeAndOpenSessionSeesGrantAndRevokeOnNextRequest() throws Exception {
		UUID id = createUser("member");
		Role r1 = newRole("R1", Permission.MEMBERS_ACTIVITY_SET, Permission.KB_VIEW);
		Role r2 = newRole("R2", Permission.KB_VIEW, Permission.AUDIT_VIEW);
		MockHttpSession session = signIn(loginOf(id), PASSWORD);
		mvc.perform(get("/api/v1/test-access/activity").session(session)).andExpect(status().isForbidden());

		access.grantRole(id, r1.getId());
		access.grantRole(id, r2.getId());
		access.grantRole(id, r2.getId()); // idempotent

		mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isOk())
				.andExpect(jsonPath("$.permissions", containsInAnyOrder("members.activity.set", "kb.view", "audit.view")));
		mvc.perform(get("/api/v1/test-access/activity").session(session)).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("select count(*) from role_assignment where user_id = ?", Long.class, id))
				.isEqualTo(2);

		access.revokeRole(id, r1.getId());
		mvc.perform(get("/api/v1/test-access/activity").session(session)).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/test-access/audit").session(session)).andExpect(status().isOk());
		access.revokeRole(id, r1.getId()); // no-op
	}

	@Test
	void editingARoleTakesEffectForOpenSessionsAndCacheIsActuallyUsed() throws Exception {
		UUID id = createUser("member");
		Role role = newRole("Edit", Permission.KB_VIEW);
		access.grantRole(id, role.getId());
		MockHttpSession session = signIn(loginOf(id), PASSWORD);
		mvc.perform(get("/api/v1/test-access/audit").session(session)).andExpect(status().isForbidden());

		// A change that bypasses the service is not noticed: the cache serves the stored value.
		jdbc.update("insert into role_permission (role_id, permission_key) values (?, 'audit.view')", role.getId());
		mvc.perform(get("/api/v1/test-access/audit").session(session)).andExpect(status().isForbidden());

		// A change through the service invalidates the cache.
		roleService.update(role.getId(), role.getName(), null, Set.of("audit.view", "kb.view"));
		mvc.perform(get("/api/v1/test-access/audit").session(session)).andExpect(status().isOk());

		roleService.archive(role.getId());
		mvc.perform(get("/api/v1/test-access/audit").session(session)).andExpect(status().isForbidden());
	}

	@Test
	void firstBootstrapAdminHasAdminRoleWithoutRoleRow() throws Exception {
		UUID id = jdbc.queryForObject("select id from users where login = 'root.admin'", UUID.class);
		assertThat(jdbc.queryForObject("select role_id from role_assignment where user_id = ?", UUID.class, id))
				.isEqualTo(AccessService.ADMIN_ROLE_ID);
		assertThat(jdbc.queryForObject("select count(*) from role where id = ?", Long.class,
				AccessService.ADMIN_ROLE_ID)).isZero();
		assertThat(access.effectivePermissions(id)).isEqualTo(Permission.allKeys());

		MockHttpSession session = signIn("root.admin", "Bootstrap-pass-42");
		mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isOk())
				.andExpect(jsonPath("$.permissions", hasSize(Permission.values().length)))
				.andExpect(jsonPath("$.mustChangePassword").value(true));
		jdbc.update("update users set must_change_password = false where id = ?", id);
		mvc.perform(get("/api/v1/test-access/activity").session(session)).andExpect(status().isOk());
		mvc.perform(get("/api/v1/test-access/audit").session(session)).andExpect(status().isOk());
	}

	@Test
	void roleAndAssignmentChangesAreAuditedWithBeforeAndAfter() {
		UUID id = createUser("audited");
		Role role = newRole("Aud", Permission.KB_VIEW);
		roleService.update(role.getId(), role.getName() + "x", "d", Set.of("kb.view", "kb.edit"));
		access.grantRole(id, role.getId());
		access.revokeRole(id, role.getId());
		roleService.archive(role.getId());

		String rowSql = "select action || ':' || (before_state is not null) || ':' || (after_state is not null)"
				+ " from audit_log where entity_type = ? and ";
		List<String> roleRows = jdbc.queryForList(rowSql + "entity_id = ?", String.class, "Role", role.getId());
		assertThat(roleRows).containsExactlyInAnyOrder("ROLE_CHANGE:false:true", "ROLE_CHANGE:true:true",
				"ROLE_CHANGE:true:true");
		List<String> assignmentRows = jdbc.queryForList(rowSql + "after_state ->> 'userId' = ?", String.class,
				"RoleAssignment", id.toString());
		assertThat(assignmentRows).containsExactlyInAnyOrder("ROLE_CHANGE:false:true", "ROLE_CHANGE:true:true");
	}

}
