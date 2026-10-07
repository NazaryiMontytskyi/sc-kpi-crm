package com.kpi.studentcouncil.crm.access.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.kpi.studentcouncil.crm.shared.error.NotFoundException;
import com.kpi.studentcouncil.crmtest.access.TestAccessController;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, TestAccessController.class })
class PermissionEndpointsIntegrationTests {

	private static final String PASSWORD = "Sup3r-secret-pw";

	private static final String INSERT_USER = "insert into users (id, login, password_hash, status, locale,"
			+ " must_change_password, created_at, updated_at) values (?, ?, ?, 'ACTIVE', ?, false, now(), now())";

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

	private UUID createUser(String locale) {
		UUID id = UUID.randomUUID();
		jdbc.update(INSERT_USER, id, "u-" + id.toString().substring(0, 8), encoder.encode(PASSWORD), locale);
		return id;
	}

	private MockHttpSession signIn(UUID id) throws Exception {
		String login = jdbc.queryForObject("select login from users where id = ?", String.class, id);
		MvcResult result = mvc.perform(post("/api/v1/auth/login").with(csrf()).with(request -> {
			request.setRemoteAddr("10.3." + (int) (Math.random() * 250) + "." + (1 + (int) (Math.random() * 250)));
			return request;
		}).contentType(MediaType.APPLICATION_JSON)
				.content("{\"login\":\"" + login + "\",\"password\":\"" + PASSWORD + "\"}")).andReturn();
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private Role newRole(String name, Permission... permissions) {
		Set<String> keys = new java.util.HashSet<>();
		for (Permission p : permissions) {
			keys.add(p.key());
		}
		return roleService.create(name + "-" + UUID.randomUUID().toString().substring(0, 6), null, keys);
	}

	@Test
	void catalogRequiresAuthentication() throws Exception {
		mvc.perform(get("/api/v1/permissions")).andExpect(status().isUnauthorized());
	}

	@Test
	void catalogListsAllKeysGroupedWithEnglishLabelsForEnglishUser() throws Exception {
		MockHttpSession session = signIn(createUser("en"));

		mvc.perform(get("/api/v1/permissions").session(session)).andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(8))).andExpect(jsonPath("$[0].code").value("members"))
				.andExpect(jsonPath("$[0].label").value("Members"))
				.andExpect(jsonPath("$[0].permissions", hasSize(5)))
				.andExpect(jsonPath("$[0].permissions[0].key").value("members.view"))
				.andExpect(jsonPath("$[0].permissions[0].label").value("View members"))
				.andExpect(jsonPath("$..permissions[*].key", hasSize(Permission.values().length)))
				.andExpect(jsonPath("$[7].code").value("other"));
	}

	@Test
	void catalogUsesUkrainianForUkrainianUser() throws Exception {
		MockHttpSession session = signIn(createUser("uk"));

		mvc.perform(get("/api/v1/permissions").session(session).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].label").value("Учасники"))
				.andExpect(jsonPath("$[0].permissions[0].label").value("Перегляд учасників"));
	}

	@Test
	void userPermissionsAllowedForSelfAndForRolesAssignHolderOnly() throws Exception {
		UUID target = createUser("en");
		UUID other = createUser("en");
		UUID assigner = createUser("en");
		access.grantRole(assigner, newRole("Assigner", Permission.ROLES_ASSIGN).getId());
		access.grantRole(target, newRole("Kb", Permission.KB_VIEW).getId());

		mvc.perform(get("/api/v1/users/" + target + "/permissions").session(signIn(target)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(target.toString()))
				.andExpect(jsonPath("$.permissions", hasSize(1)))
				.andExpect(jsonPath("$.permissions[0]").value("kb.view"));

		mvc.perform(get("/api/v1/users/" + target + "/permissions").session(signIn(other)))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

		MockHttpSession assignerSession = signIn(assigner);
		mvc.perform(get("/api/v1/users/" + target + "/permissions").session(assignerSession))
				.andExpect(status().isOk()).andExpect(jsonPath("$.permissions[0]").value("kb.view"));
		mvc.perform(get("/api/v1/users/" + UUID.randomUUID() + "/permissions").session(assignerSession))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void revokingARoleRemovesPermissionOnNextRequestWithoutReLogin() throws Exception {
		UUID id = createUser("en");
		Role role = newRole("Live", Permission.AUDIT_VIEW, Permission.MEMBERS_ACTIVITY_SET);
		access.grantRole(id, role.getId());
		MockHttpSession session = signIn(id);
		mvc.perform(get("/api/v1/test-access/audit").session(session)).andExpect(status().isOk());
		mvc.perform(get("/api/v1/auth/me").session(session))
				.andExpect(jsonPath("$.permissions", hasItem("audit.view")));

		access.revokeRole(id, role.getId());

		mvc.perform(get("/api/v1/test-access/audit").session(session)).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isOk())
				.andExpect(jsonPath("$.permissions", not(hasItem("audit.view"))));
		mvc.perform(get("/api/v1/users/" + id + "/permissions").session(session)).andExpect(status().isOk())
				.andExpect(jsonPath("$.permissions", hasSize(0)));
	}

	@Test
	void grantRoleRejectsUnknownUser() {
		Role role = newRole("Ghost", Permission.KB_VIEW);
		assertThatThrownBy(() -> access.grantRole(UUID.randomUUID(), role.getId()))
				.isInstanceOf(NotFoundException.class);
	}

}
