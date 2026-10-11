package com.kpi.studentcouncil.crm.access.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.UUID;

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
import com.kpi.studentcouncil.crm.access.Permission;
import com.kpi.studentcouncil.crmtest.SharedAdmin;
import com.kpi.studentcouncil.crmtest.access.TestAccessController;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, TestAccessController.class })
class RoleEndpointsIntegrationTests {

	private static final String PASSWORD = "Sup3r-secret-pw";

	private static final String INSERT_USER = "insert into users (id, login, password_hash, status, locale,"
			+ " must_change_password, created_at, updated_at) values (?, ?, ?, 'ACTIVE', 'en', false, now(), now())";

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

	private UUID createUser() {
		UUID id = UUID.randomUUID();
		jdbc.update(INSERT_USER, id, "r-" + id.toString().substring(0, 8), encoder.encode(PASSWORD));
		return id;
	}

	private MockHttpSession signIn(String login, String password) throws Exception {
		MvcResult result = mvc.perform(post("/api/v1/auth/login").with(csrf()).with(request -> {
			request.setRemoteAddr("10.4." + (int) (Math.random() * 250) + "." + (1 + (int) (Math.random() * 250)));
			return request;
		}).contentType(MediaType.APPLICATION_JSON)
				.content("{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}")).andReturn();
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private MockHttpSession signInUserWith(Permission... permissions) throws Exception {
		UUID id = createUser();
		if (permissions.length > 0) {
			Set<String> keys = new java.util.HashSet<>();
			for (Permission p : permissions) {
				keys.add(p.key());
			}
			access.grantRole(id,
					roleService.create("Holder-" + UUID.randomUUID().toString().substring(0, 8), null, keys).getId());
		}
		return signIn(jdbc.queryForObject("select login from users where id = ?", String.class, id), PASSWORD);
	}

	private MockHttpSession admin() throws Exception {
		SharedAdmin.ensure(jdbc, encoder);
		return signIn(SharedAdmin.LOGIN, SharedAdmin.PASSWORD);
	}

	private ResultActions call(MockHttpSession session, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b,
			String body) throws Exception {
		b = b.session(session).with(csrf());
		if (body != null) {
			b = b.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mvc.perform(b);
	}

	private static String roleJson(String name, String... keys) {
		StringBuilder sb = new StringBuilder("{\"name\":\"" + name + "\",\"description\":\"Fake role\",\"permissions\":[");
		for (int i = 0; i < keys.length; i++) {
			sb.append(i == 0 ? "" : ",").append('"').append(keys[i]).append('"');
		}
		return sb.append("]}").toString();
	}

	private String createRole(MockHttpSession admin, String name, String... keys) throws Exception {
		MvcResult r = call(admin, post("/api/v1/roles"), roleJson(name, keys)).andExpect(status().isCreated())
				.andReturn();
		return com.jayway.jsonpath.JsonPath.read(r.getResponse().getContentAsString(), "$.id");
	}

	private static String unique(String prefix) {
		return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
	}

	@Test
	void roleMutationsRequireRolesManage() throws Exception {
		UUID someRole = roleService.create(unique("Target"), null, Set.of()).getId();
		for (MockHttpSession noManage : List.of(signInUserWith(), signInUserWith(Permission.ROLES_ASSIGN))) {
			call(noManage, post("/api/v1/roles"), roleJson(unique("X"))).andExpect(status().isForbidden())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
			call(noManage, put("/api/v1/roles/" + someRole), roleJson(unique("X"))).andExpect(status().isForbidden());
			call(noManage, post("/api/v1/roles/" + someRole + "/archive"), null).andExpect(status().isForbidden());
		}
		call(signInUserWith(Permission.ROLES_MANAGE), post("/api/v1/roles"), roleJson(unique("Ok"), "kb.view"))
				.andExpect(status().isCreated());
	}

	@Test
	void assignmentEndpointsRequireRolesAssign() throws Exception {
		UUID target = createUser();
		UUID role = roleService.create(unique("Assignable"), null, Set.of("kb.view")).getId();
		String url = "/api/v1/users/" + target + "/roles/" + role;
		for (MockHttpSession noAssign : List.of(signInUserWith(), signInUserWith(Permission.ROLES_MANAGE))) {
			call(noAssign, put(url), null).andExpect(status().isForbidden());
			call(noAssign, delete(url), null).andExpect(status().isForbidden());
		}
		MockHttpSession assigner = signInUserWith(Permission.ROLES_ASSIGN);
		call(assigner, put(url), null).andExpect(status().isNoContent());
		call(assigner, put(url), null).andExpect(status().isNoContent());
		call(assigner, get("/api/v1/users/" + target + "/roles"), null).andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(role.toString()));
		call(assigner, delete(url), null).andExpect(status().isNoContent());
		call(assigner, get("/api/v1/users/" + target + "/roles"), null).andExpect(jsonPath("$", hasSize(0)));
		call(assigner, put("/api/v1/users/" + UUID.randomUUID() + "/roles/" + role), null)
				.andExpect(status().isNotFound());
	}

	@Test
	void listIsOpenToAuthenticatedUsersAndStartsWithAdmin() throws Exception {
		mvc.perform(get("/api/v1/roles")).andExpect(status().isUnauthorized());
		MockHttpSession plain = signInUserWith();
		call(plain, get("/api/v1/roles"), null).andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(AccessService.ADMIN_ROLE_ID.toString()))
				.andExpect(jsonPath("$.content[0].system").value(true))
				.andExpect(jsonPath("$.content[0].permissions", hasSize(Permission.values().length)));
	}

	@Test
	void adminRoleIsProtected() throws Exception {
		MockHttpSession admin = admin();
		UUID adminId = SharedAdmin.ensure(jdbc, encoder);
		String adminRole = AccessService.ADMIN_ROLE_ID.toString();
		call(admin, put("/api/v1/roles/" + adminRole), roleJson(unique("Renamed"))).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("SYSTEM_ROLE_IMMUTABLE"));
		call(admin, post("/api/v1/roles/" + adminRole + "/archive"), null).andExpect(status().isForbidden());
		call(admin, delete("/api/v1/users/" + adminId + "/roles/" + adminRole), null)
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ADMIN_ROLE_RESERVED"));
		call(admin, put("/api/v1/users/" + createUser() + "/roles/" + adminRole), null)
				.andExpect(status().isForbidden());
		assertThat(access.effectivePermissions(adminId)).isEqualTo(Permission.allKeys());
		call(admin, post("/api/v1/roles"), roleJson("admin")).andExpect(status().isUnprocessableContent());
	}

	@Test
	void roleInUseCanBeArchivedHoldersLosePermissionsAndAuditKeepsPermissionSets() throws Exception {
		MockHttpSession admin = admin();
		String name = unique("Editors");
		String id = createRole(admin, name, "kb.view");
		call(admin, post("/api/v1/roles"), roleJson(name, "kb.view")).andExpect(status().isConflict());
		call(admin, post("/api/v1/roles"), roleJson(unique("Bad"), "no.such.key"))
				.andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("UNKNOWN_PERMISSION"));

		UUID holder = createUser();
		call(admin, put("/api/v1/users/" + holder + "/roles/" + id), null).andExpect(status().isNoContent());
		MockHttpSession holderSession = signIn(
				jdbc.queryForObject("select login from users where id = ?", String.class, holder), PASSWORD);
		call(holderSession, get("/api/v1/auth/me"), null).andExpect(jsonPath("$.permissions", hasItem("kb.view")));

		call(admin, put("/api/v1/roles/" + id), roleJson(name + "2", "kb.view", "audit.view"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.permissions", hasSize(2)))
				.andExpect(jsonPath("$.assignedUserCount").value(1));
		call(holderSession, get("/api/v1/auth/me"), null).andExpect(jsonPath("$.permissions", hasItem("audit.view")));

		call(admin, post("/api/v1/roles/" + id + "/archive"), null).andExpect(status().isOk())
				.andExpect(jsonPath("$.archived").value(true));
		call(holderSession, get("/api/v1/auth/me"), null).andExpect(jsonPath("$.permissions", not(hasItem("kb.view"))))
				.andExpect(jsonPath("$.permissions", hasSize(0)));
		call(admin, put("/api/v1/users/" + createUser() + "/roles/" + id), null).andExpect(status().isNotFound());

		List<String> rows = jdbc.queryForList(
				"select before_state::text || '>' || after_state::text from audit_log where entity_type = 'Role'"
						+ " and entity_id = ? order by at",
				String.class, UUID.fromString(id));
		assertThat(rows).hasSize(3);
		assertThat(rows.get(1)).contains("\"permissions\": [\"kb.view\"]").contains("[\"audit.view\", \"kb.view\"]");
		assertThat(rows.get(2)).contains("\"archived\": false").contains("\"archived\": true");

		// archived roles are listed only on request by roles.manage holders
		call(admin, get("/api/v1/roles?includeArchived=true&size=100"), null)
				.andExpect(jsonPath("$.content[?(@.id=='" + id + "')]", hasSize(1)));
		call(admin, get("/api/v1/roles?size=100"), null)
				.andExpect(jsonPath("$.content[?(@.id=='" + id + "')]", hasSize(0)));
	}

}
