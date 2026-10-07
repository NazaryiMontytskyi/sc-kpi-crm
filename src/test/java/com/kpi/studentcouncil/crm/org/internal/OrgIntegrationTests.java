package com.kpi.studentcouncil.crm.org.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
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
import com.kpi.studentcouncil.crm.org.OrgService;
import com.kpi.studentcouncil.crm.org.OrgUnitArchiveGuard;
import com.kpi.studentcouncil.crm.org.OrgUnitInfo;
import com.kpi.studentcouncil.crm.org.OrgUnitType;

@SpringBootTest(properties = { "crm.bootstrap.admin.login=Org.Admin", "crm.bootstrap.admin.password=Bootstrap-pass-42" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, OrgIntegrationTests.VetoConfig.class })
class OrgIntegrationTests {

	private static final String PASSWORD = "Sup3r-secret-pw";
	private static final UUID VETOED = UUID.randomUUID();

	/** Stands in for the future membership guard (INC-018): vetoes archiving of one specific unit id. */
	@TestConfiguration(proxyBeanMethods = false)
	static class VetoConfig {

		@Bean
		OrgUnitArchiveGuard testGuard() {
			return id -> id.equals(VETOED) ? Optional.of("has active memberships") : Optional.empty();
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
	OrgService orgService;

	MockHttpSession admin;

	@BeforeEach
	void setUp() throws Exception {
		jdbc.update("update org_unit set parent_id = null");
		jdbc.update("delete from org_unit");
		jdbc.update("update users set must_change_password = false where login = 'org.admin'");
		admin = signIn("org.admin", "Bootstrap-pass-42");
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
		UUID user = UUID.randomUUID();
		String login = "org-user-" + user.toString().substring(0, 8);
		jdbc.update("insert into users (id, login, password_hash, status, locale, must_change_password, created_at,"
				+ " updated_at) values (?, ?, ?, 'ACTIVE', 'en', false, now(), now())", user, login,
				encoder.encode(PASSWORD));
		if (permissionKeys.length > 0) {
			UUID role = UUID.randomUUID();
			jdbc.update("insert into role (id, name, is_system, created_at, updated_at) values (?, ?, false, now(),"
					+ " now())", role, "org-role-" + role);
			for (String key : permissionKeys) {
				jdbc.update("insert into role_permission (role_id, permission_key) values (?, ?)", role, key);
			}
			access.grantRole(user, role);
		}
		return signIn(login, PASSWORD);
	}

	private ResultActions send(MockHttpSession session, String path, String json) throws Exception {
		return mvc.perform(post("/api/v1/org-units" + path).with(csrf()).session(session)
				.header(HttpHeaders.ACCEPT_LANGUAGE, "en").contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private UUID create(String name, String type, UUID parent) throws Exception {
		String json = "{\"name\":\"" + name + "\",\"type\":\"" + type + "\""
				+ (parent == null ? "" : ",\"parentId\":\"" + parent + "\"") + "}";
		MvcResult result = send(admin, "", json).andExpect(status().isCreated()).andReturn();
		return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
	}

	private long auditCount(UUID id, String action) {
		return jdbc.queryForObject(
				"select count(*) from audit_log where entity_type = 'OrgUnit' and entity_id = ? and action = ?",
				Long.class, id, action);
	}

	@Test
	void anonymousIsRejected() throws Exception {
		mvc.perform(get("/api/v1/org-units/tree")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/org-units").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"X\",\"type\":\"LEADERSHIP\"}")).andExpect(status().isUnauthorized());
	}

	@Test
	void readsNeedAuthenticationOnlyAndMutationsNeedOrgManage() throws Exception {
		UUID head = create("Leadership", "LEADERSHIP", null);
		MockHttpSession plain = userWith();
		MockHttpSession viewer = userWith("org.view");
		MockHttpSession manager = userWith("org.manage");

		mvc.perform(get("/api/v1/org-units/tree").session(plain)).andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)));
		mvc.perform(get("/api/v1/org-units/" + head).session(plain)).andExpect(status().isOk());

		String body = "{\"name\":\"Dept\",\"type\":\"DEPARTMENT\"}";
		send(plain, "", body).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
		send(viewer, "", body).andExpect(status().isForbidden());
		mvc.perform(put("/api/v1/org-units/" + head).with(csrf()).session(viewer)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Renamed\"}"))
				.andExpect(status().isForbidden());
		send(viewer, "/" + head + "/move", "{}").andExpect(status().isForbidden());
		send(viewer, "/" + head + "/archive", "").andExpect(status().isForbidden());
		send(viewer, "/" + head + "/restore", "").andExpect(status().isForbidden());

		send(manager, "", body).andExpect(status().isCreated());
	}

	@Test
	void invariantsAreEnforcedWithLocalizedProblems() throws Exception {
		UUID head = create("Leadership", "LEADERSHIP", null);
		UUID dept = create("Dept A", "DEPARTMENT", head);
		UUID division = create("Division A1", "DIVISION", dept);
		UUID group = create("Group", "WORKING_GROUP", null);

		send(admin, "", "{\"name\":\"Second\",\"type\":\"LEADERSHIP\"}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ORG_UNIT_LEADERSHIP_EXISTS"));
		send(admin, "", "{\"name\":\"D2\",\"type\":\"DIVISION\",\"parentId\":\"" + division + "\"}")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("ORG_UNIT_PARENT_NOT_ALLOWED"))
				.andExpect(jsonPath("$.detail").value("Оргодиницю типу DIVISION не можна розмістити під одиницею типу DIVISION."));
		send(admin, "", "{\"name\":\"D3\",\"type\":\"DIVISION\"}").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("ORG_UNIT_PARENT_REQUIRED"));
		send(admin, "", "{\"name\":\"D4\",\"type\":\"DEPARTMENT\",\"parentId\":\"" + group + "\"}")
				.andExpect(status().isUnprocessableContent());
		send(admin, "", "{\"name\":\"D5\",\"type\":\"DEPARTMENT\",\"parentId\":\"" + UUID.randomUUID() + "\"}")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("ORG_UNIT_PARENT_NOT_FOUND"));
		send(admin, "", "{\"name\":\" \",\"type\":\"DEPARTMENT\"}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		// the admin profile locale is uk; an English user gets English text
		send(admin, "", "{\"name\":\"Second\",\"type\":\"LEADERSHIP\"}")
				.andExpect(jsonPath("$.title").value("Керівництво вже існує"));
		send(userWith("org.manage"), "", "{\"name\":\"Second\",\"type\":\"LEADERSHIP\"}")
				.andExpect(jsonPath("$.title").value("Leadership already exists"));
	}

	@Test
	void moveRejectsCyclesAndInvalidParents() throws Exception {
		UUID head = create("Leadership", "LEADERSHIP", null);
		UUID dept1 = create("Dept 1", "DEPARTMENT", head);
		UUID dept2 = create("Dept 2", "DEPARTMENT", head);
		UUID division = create("Division", "DIVISION", dept1);
		UUID wgA = create("WG A", "WORKING_GROUP", null);
		UUID wgB = create("WG B", "WORKING_GROUP", wgA);

		send(admin, "/" + wgA + "/move", "{\"parentId\":\"" + wgB + "\"}").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("ORG_UNIT_CYCLE"));
		send(admin, "/" + wgA + "/move", "{\"parentId\":\"" + wgA + "\"}").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("ORG_UNIT_CYCLE"));
		send(admin, "/" + division + "/move", "{\"parentId\":\"" + head + "\"}")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("ORG_UNIT_PARENT_NOT_ALLOWED"));
		send(admin, "/" + division + "/move", "{}").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("ORG_UNIT_PARENT_REQUIRED"));

		send(admin, "/" + division + "/move", "{\"parentId\":\"" + dept2 + "\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.parentId").value(dept2.toString()))
				.andExpect(jsonPath("$.ancestorIds[0]").value(dept2.toString()))
				.andExpect(jsonPath("$.ancestorIds[1]").value(head.toString()));
		assertThat(auditCount(division, "UPDATE")).isEqualTo(1);
	}

	@Test
	void archiveIsSoftHidesFromTreeAndIsRestorable() throws Exception {
		UUID head = create("Leadership", "LEADERSHIP", null);
		UUID dept = create("Dept", "DEPARTMENT", head);
		UUID division = create("Division", "DIVISION", dept);

		send(admin, "/" + dept + "/archive", "").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ORG_UNIT_HAS_ACTIVE_CHILDREN"));

		send(admin, "/" + division + "/archive", "").andExpect(status().isOk())
				.andExpect(jsonPath("$.archived").value(true));
		assertThat(jdbc.queryForObject("select count(*) from org_unit where id = ? and archived_at is not null",
				Long.class, division)).isEqualTo(1);
		send(admin, "/" + division + "/archive", "").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ALREADY_ARCHIVED"));

		mvc.perform(get("/api/v1/org-units/tree").session(admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].children[0].id").value(dept.toString()))
				.andExpect(jsonPath("$[0].children[0].children", hasSize(0)));
		mvc.perform(get("/api/v1/org-units/tree?includeArchived=true").session(admin))
				.andExpect(jsonPath("$[0].children[0].children", hasSize(1)))
				.andExpect(jsonPath("$[0].children[0].children[0].archived").value(true));
		mvc.perform(get("/api/v1/org-units/" + division).session(admin)).andExpect(status().isOk());

		// archived units cannot be edited and cannot be a parent
		mvc.perform(put("/api/v1/org-units/" + division).with(csrf()).session(admin)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ORG_UNIT_ARCHIVED"));
		assertThat(orgService.isActive(division)).isFalse();

		send(admin, "/" + division + "/restore", "").andExpect(status().isOk())
				.andExpect(jsonPath("$.archived").value(false));
		send(admin, "/" + division + "/restore", "").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("NOT_ARCHIVED"));
		assertThat(auditCount(division, "ARCHIVE")).isEqualTo(1);
		assertThat(auditCount(division, "RESTORE")).isEqualTo(1);

		// restoring a child under an archived parent is refused
		send(admin, "/" + division + "/archive", "").andExpect(status().isOk());
		send(admin, "/" + dept + "/archive", "").andExpect(status().isOk());
		send(admin, "/" + division + "/restore", "").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("ORG_UNIT_PARENT_ARCHIVED"));
	}

	@Test
	void archivedLeadershipCanBeReplacedButNotRestoredWhileAnotherIsActive() throws Exception {
		UUID first = create("Leadership 1", "LEADERSHIP", null);
		send(admin, "/" + first + "/archive", "").andExpect(status().isOk());
		UUID second = create("Leadership 2", "LEADERSHIP", null);
		send(admin, "/" + first + "/restore", "").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ORG_UNIT_LEADERSHIP_EXISTS"));
		assertThat(second).isNotNull();
	}

	@Test
	void archiveGuardCanVeto() throws Exception {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into org_unit (id, name, type, created_at, updated_at) values (?, 'Guarded', 'WORKING_GROUP',"
				+ " now(), now())", VETOED);
		send(admin, "/" + VETOED + "/archive", "").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ORG_UNIT_ARCHIVE_BLOCKED"))
				.andExpect(jsonPath("$.detail").value("Зараз оргодиницю не можна архівувати: has active memberships."));
		assertThat(id).isNotNull();
	}

	@Test
	void createRenameAreAuditedAndUnknownIdIs404() throws Exception {
		UUID head = create("Leadership", "LEADERSHIP", null);
		assertThat(auditCount(head, "CREATE")).isEqualTo(1);

		mvc.perform(put("/api/v1/org-units/" + head).with(csrf()).session(admin)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  New name \",\"description\":\"d\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("New name"))
				.andExpect(jsonPath("$.description").value("d"));
		assertThat(auditCount(head, "UPDATE")).isEqualTo(1);
		String after = jdbc.queryForObject("select after_state::text from audit_log where entity_id = ? and action ="
				+ " 'UPDATE'", String.class, head);
		assertThat(after).contains("New name");

		mvc.perform(get("/api/v1/org-units/" + UUID.randomUUID()).session(admin)).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void publicServiceExposesStructure() throws Exception {
		UUID head = create("Leadership", "LEADERSHIP", null);
		UUID dept = create("Dept", "DEPARTMENT", head);
		UUID division = create("Division", "DIVISION", dept);

		OrgUnitInfo info = orgService.require(division);
		assertThat(info.type()).isEqualTo(OrgUnitType.DIVISION);
		assertThat(info.ancestorIds()).containsExactly(dept, head);
		assertThat(info.active()).isTrue();
		assertThat(orgService.require(dept).childIds()).containsExactly(division);
		assertThat(orgService.find(UUID.randomUUID())).isEmpty();
	}

}
