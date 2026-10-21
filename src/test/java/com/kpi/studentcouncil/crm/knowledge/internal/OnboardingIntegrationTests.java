package com.kpi.studentcouncil.crm.knowledge.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
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
class OnboardingIntegrationTests {

	private static final String PASSWORD = "Sup3r-secret-pw";
	private static final String RUN = UUID.randomUUID().toString().substring(0, 8);

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PasswordEncoder encoder;

	@Autowired
	AccessService access;

	MockHttpSession admin;

	private final List<UUID> articles = new ArrayList<>();
	private final List<UUID> units = new ArrayList<>();
	private final List<UUID> users = new ArrayList<>();
	private final List<UUID> roles = new ArrayList<>();

	@BeforeEach
	void setUp() throws Exception {
		SharedAdmin.ensure(jdbc, encoder);
		admin = signIn(SharedAdmin.LOGIN, SharedAdmin.PASSWORD);
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("delete from kb_onboarding_list_item where list_id in (select id from kb_onboarding_list"
				+ " where title like ?)", "%" + RUN);
		jdbc.update("delete from kb_onboarding_list where title like ?", "%" + RUN);
		for (UUID user : users) {
			jdbc.update("delete from kb_article_read where user_id = ?", user);
			jdbc.update("delete from kb_onboarding_prompt where user_id = ?", user);
			jdbc.update("delete from org_membership where user_id = ?", user);
			jdbc.update("delete from role_assignment where user_id = ?", user);
		}
		for (UUID article : articles) {
			jdbc.update("delete from kb_article_read where article_id = ?", article);
			jdbc.update("delete from kb_article_version where article_id = ?", article);
			jdbc.update("delete from kb_article where id = ?", article);
		}
		for (UUID unit : units) {
			jdbc.update("delete from org_unit where id = ?", unit);
		}
		for (UUID role : roles) {
			jdbc.update("delete from role_permission where role_id = ?", role);
			jdbc.update("delete from role where id = ?", role);
		}
		for (UUID user : users) {
			jdbc.update("delete from users where id = ?", user);
		}
		articles.clear();
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

	private UUID user(String... permissionKeys) {
		UUID user = UUID.randomUUID();
		users.add(user);
		jdbc.update("insert into users (id, login, password_hash, status, locale, must_change_password, created_at,"
				+ " updated_at) values (?, ?, ?, 'ACTIVE', 'en', false, now(), now())", user,
				"ob-user-" + user.toString().substring(0, 8), encoder.encode(PASSWORD));
		if (permissionKeys.length > 0) {
			UUID role = UUID.randomUUID();
			roles.add(role);
			jdbc.update("insert into role (id, name, is_system, created_at, updated_at) values (?, ?, false, now(),"
					+ " now())", role, "ob-role-" + role);
			for (String key : permissionKeys) {
				jdbc.update("insert into role_permission (role_id, permission_key) values (?, ?)", role, key);
			}
			access.grantRole(user, role);
		}
		return user;
	}

	private MockHttpSession session(UUID user) throws Exception {
		return signIn(jdbc.queryForObject("select login from users where id = ?", String.class, user), PASSWORD);
	}

	private MockHttpSession sessionWith(String... permissionKeys) throws Exception {
		return session(user(permissionKeys));
	}

	private UUID unit(String name) {
		UUID id = UUID.randomUUID();
		units.add(id);
		jdbc.update("insert into org_unit (id, name, type, created_at, updated_at) values (?, ?, 'DEPARTMENT', now(),"
				+ " now())", id, name + " " + RUN);
		return id;
	}

	private void join(UUID user, UUID unit) {
		jdbc.update("insert into org_membership (id, user_id, org_unit_id, since, created_at, updated_at) values"
				+ " (?, ?, ?, current_date, now(), now())", UUID.randomUUID(), user, unit);
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

	private UUID article(String title, boolean restricted) throws Exception {
		UUID id = id(send(admin, "/articles", "{\"title\":\"" + title + " " + RUN + "\",\"restricted\":" + restricted + "}")
				.andExpect(status().isCreated()));
		articles.add(id);
		return id;
	}

	private static String items(UUID list) {
		return "$[?(@.id=='" + list + "')].items[*].articleId";
	}

	private static String ids(UUID... ids) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < ids.length; i++) {
			sb.append(i == 0 ? "" : ",").append('"').append(ids[i]).append('"');
		}
		return sb.append(']').toString();
	}

	private static String create(String title, UUID unit, UUID... articleIds) {
		return "{\"title\":\"" + title + " " + RUN + "\"," + (unit == null ? "" : "\"orgUnitId\":\"" + unit + "\",")
				+ "\"articleIds\":" + ids(articleIds) + "}";
	}

	@Test
	void onlyOnboardingManageEditsLists() throws Exception {
		UUID a = article("A", false);
		MockHttpSession viewer = sessionWith("kb.view", "kb.edit");
		MockHttpSession manager = sessionWith("kb.view", "onboarding.manage");
		MockHttpSession plain = sessionWith();

		send(viewer, "/onboarding/manage/lists", create("General", null, a)).andExpect(status().isForbidden());
		read(viewer, "/onboarding/manage/lists").andExpect(status().isForbidden());
		read(plain, "/onboarding/lists").andExpect(status().isForbidden());
		UUID list = id(send(manager, "/onboarding/manage/lists", create("General", null, a)).andExpect(status().isCreated()));
		putJson(viewer, "/onboarding/manage/lists/" + list, "{\"title\":\"X " + RUN + "\",\"articleIds\":[]}")
				.andExpect(status().isForbidden());
		send(viewer, "/onboarding/manage/lists/" + list + "/archive", "").andExpect(status().isForbidden());
		send(viewer, "/onboarding/manage/lists/" + list + "/restore", "").andExpect(status().isForbidden());
		putJson(manager, "/onboarding/manage/lists/" + list, "{\"title\":\"X " + RUN + "\",\"articleIds\":[]}")
				.andExpect(status().isOk());
	}

	@Test
	void orderIsPreservedAndEditable() throws Exception {
		UUID a = article("A", false);
		UUID b = article("B", false);
		UUID c = article("C", false);
		UUID list = id(send(admin, "/onboarding/manage/lists", create("General", null, c, a, b))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.items[0].articleId").value(c.toString()))
				.andExpect(jsonPath("$.items[1].articleId").value(a.toString()))
				.andExpect(jsonPath("$.items[2].articleId").value(b.toString())));
		putJson(admin, "/onboarding/manage/lists/" + list, "{\"title\":\"General " + RUN + "\",\"articleIds\":" + ids(b, c) + "}")
				.andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(2)));
		read(admin, "/onboarding/lists").andExpect(status().isOk())
				.andExpect(jsonPath(items(list), contains(b.toString(), c.toString())));
		assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_type = 'OnboardingList'"
				+ " and entity_id = ?", Long.class, list)).isEqualTo(2);

		send(admin, "/onboarding/manage/lists", create("Dup", null, a)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ONBOARDING_LIST_EXISTS"));
		putJson(admin, "/onboarding/manage/lists/" + list, "{\"title\":\"General " + RUN + "\",\"articleIds\":" + ids(a, a) + "}")
				.andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("ONBOARDING_ARTICLE_INVALID"));
		putJson(admin, "/onboarding/manage/lists/" + list, "{\"title\":\"General " + RUN + "\",\"articleIds\":" + ids(UUID.randomUUID()) + "}")
				.andExpect(status().isUnprocessableContent());
	}

	@Test
	void archivedArticlesDisappearWithoutBreakingOrder() throws Exception {
		UUID a = article("A", false);
		UUID b = article("B", false);
		UUID c = article("C", false);
		UUID list = id(send(admin, "/onboarding/manage/lists", create("General", null, a, b, c)).andExpect(status().isCreated()));
		send(admin, "/articles/" + b + "/archive", "").andExpect(status().isOk());
		read(admin, "/onboarding/lists").andExpect(jsonPath("$[?(@.id=='" + list + "')].items", hasSize(1)))
				.andExpect(jsonPath(items(list), contains(a.toString(), c.toString())));
		send(admin, "/articles/" + b + "/restore", "").andExpect(status().isOk());
		read(admin, "/onboarding/lists")
				.andExpect(jsonPath(items(list), contains(a.toString(), b.toString(), c.toString())));
	}

	@Test
	void pageShowsGeneralAndOwnDepartmentLists() throws Exception {
		UUID a = article("A", false);
		UUID b = article("B", false);
		UUID mine = unit("Mine");
		UUID other = unit("Other");
		UUID user = user("kb.view");
		join(user, mine);
		MockHttpSession s = session(user);
		UUID general = id(send(admin, "/onboarding/manage/lists", create("General", null, a)).andExpect(status().isCreated()));
		UUID dept = id(send(admin, "/onboarding/manage/lists", create("Mine", mine, b)).andExpect(status().isCreated()));
		UUID foreign = id(send(admin, "/onboarding/manage/lists", create("Other", other, a)).andExpect(status().isCreated()));
		send(admin, "/onboarding/manage/lists", create("Mine again", mine, a)).andExpect(status().isConflict());

		String json = read(s, "/onboarding/lists").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		List<String> got = JsonPath.read(json, "$[*].id");
		assertThat(got).contains(general.toString(), dept.toString()).doesNotContain(foreign.toString());
		assertThat(got.indexOf(general.toString())).isLessThan(got.indexOf(dept.toString()));

		send(admin, "/onboarding/manage/lists", "{\"title\":\"Bad " + RUN + "\",\"orgUnitId\":\"" + UUID.randomUUID()
				+ "\",\"articleIds\":[]}").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("ONBOARDING_UNIT_INVALID"));
	}

	@Test
	void restrictedArticlesAreHiddenFromViewersWithoutPermission() throws Exception {
		UUID open = article("Open", false);
		UUID secret = article("Secret", true);
		UUID list = id(send(admin, "/onboarding/manage/lists", create("General", null, secret, open)).andExpect(status().isCreated()));
		MockHttpSession plain = sessionWith("kb.view");
		MockHttpSession privileged = sessionWith("kb.view", "kb.restricted.view");
		read(plain, "/onboarding/lists").andExpect(jsonPath(items(list), contains(open.toString())));
		read(privileged, "/onboarding/lists").andExpect(jsonPath(items(list), contains(secret.toString(), open.toString())));
	}

	@Test
	void readMarksArePrivateToTheirOwner() throws Exception {
		UUID a = article("A", false);
		UUID list = id(send(admin, "/onboarding/manage/lists", create("General", null, a)).andExpect(status().isCreated()));
		MockHttpSession alice = sessionWith("kb.view");
		MockHttpSession bob = sessionWith("kb.view");

		read(alice, "/articles/" + a + "/read-mark").andExpect(jsonPath("$.read").value(false));
		putJson(alice, "/articles/" + a + "/read-mark", "").andExpect(status().isOk()).andExpect(jsonPath("$.read").value(true));
		putJson(alice, "/articles/" + a + "/read-mark", "").andExpect(status().isOk());
		assertThat(jdbc.queryForObject("select count(*) from kb_article_read where article_id = ?", Long.class, a)).isEqualTo(1);

		read(alice, "/articles/" + a + "/read-mark").andExpect(jsonPath("$.read").value(true));
		read(alice, "/onboarding/lists").andExpect(jsonPath("$[?(@.id=='" + list + "')].items[*].read", contains(true)));
		// Others (another user, the admin, a manager) never see the mark.
		read(bob, "/articles/" + a + "/read-mark").andExpect(jsonPath("$.read").value(false));
		read(bob, "/onboarding/lists").andExpect(jsonPath("$[?(@.id=='" + list + "')].items[*].read", contains(false)));
		read(admin, "/onboarding/lists").andExpect(jsonPath("$[?(@.id=='" + list + "')].items[*].read", contains(false)));
		read(admin, "/onboarding/manage/lists").andExpect(jsonPath("$[?(@.id=='" + list + "')].items[*].read", contains(false)));
		// The article payload carries no marks and there is no endpoint that takes a user id.
		String article = read(alice, "/articles/" + a).andReturn().getResponse().getContentAsString();
		assertThat(article).doesNotContain("readBy").doesNotContain("read\":");
		read(bob, "/articles/" + a + "/read-mark?userId=" + UUID.randomUUID()).andExpect(jsonPath("$.read").value(false));

		// Bob withdrawing his (non-existent) mark does not touch Alice's.
		send(bob, "/articles/" + a + "/read-mark/withdraw", "").andExpect(status().isOk());
		read(alice, "/articles/" + a + "/read-mark").andExpect(jsonPath("$.read").value(true));
		send(alice, "/articles/" + a + "/read-mark/withdraw", "").andExpect(status().isOk()).andExpect(jsonPath("$.read").value(false));
		assertThat(jdbc.queryForObject("select count(*) from kb_article_read where article_id = ?", Long.class, a)).isZero();

		putJson(sessionWith(), "/articles/" + a + "/read-mark", "").andExpect(status().isForbidden());
		putJson(alice, "/articles/" + UUID.randomUUID() + "/read-mark", "").andExpect(status().isNotFound());
	}

	@Test
	void restrictedArticleCannotBeMarkedWithoutPermission() throws Exception {
		UUID secret = article("Secret", true);
		putJson(sessionWith("kb.view"), "/articles/" + secret + "/read-mark", "").andExpect(status().isNotFound());
	}

	@Test
	void archiveAndRestoreList() throws Exception {
		UUID a = article("A", false);
		UUID list = id(send(admin, "/onboarding/manage/lists", create("General", null, a)).andExpect(status().isCreated()));
		send(admin, "/onboarding/manage/lists/" + list + "/archive", "").andExpect(status().isOk())
				.andExpect(jsonPath("$.archived").value(true));
		putJson(admin, "/onboarding/manage/lists/" + list, "{\"title\":\"X " + RUN + "\",\"articleIds\":[]}")
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ONBOARDING_LIST_ARCHIVED"));
		read(admin, "/onboarding/lists").andExpect(jsonPath("$[?(@.id=='" + list + "')]", hasSize(0)));
		read(admin, "/onboarding/manage/lists?includeArchived=true").andExpect(jsonPath("$[?(@.id=='" + list + "')]", hasSize(1)));
		UUID second = id(send(admin, "/onboarding/manage/lists", create("Second", null, a)).andExpect(status().isCreated()));
		send(admin, "/onboarding/manage/lists/" + list + "/restore", "").andExpect(status().isConflict());
		send(admin, "/onboarding/manage/lists/" + second + "/archive", "").andExpect(status().isOk());
		send(admin, "/onboarding/manage/lists/" + list + "/restore", "").andExpect(status().isOk());
		assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_type = 'OnboardingList'"
				+ " and entity_id = ? and action in ('ARCHIVE','RESTORE')", Long.class, list)).isEqualTo(2);
	}

	@Test
	void newcomerPromptIsShownOnceUntilDismissed() throws Exception {
		MockHttpSession s = sessionWith("kb.view");
		read(s, "/onboarding/prompt").andExpect(jsonPath("$.pending").value(true));
		send(s, "/onboarding/prompt/dismiss", "").andExpect(status().isOk());
		send(s, "/onboarding/prompt/dismiss", "").andExpect(status().isOk());
		read(s, "/onboarding/prompt").andExpect(jsonPath("$.pending").value(false));
		read(sessionWith("kb.view"), "/onboarding/prompt").andExpect(jsonPath("$.pending").value(true));
	}

}
