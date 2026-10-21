package com.kpi.studentcouncil.crm.search.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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
import org.springframework.context.ApplicationEventPublisher;
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
import com.kpi.studentcouncil.crm.members.MemberEvent;
import com.kpi.studentcouncil.crmtest.SharedAdmin;

/** Search index and API against a real PostgreSQL (pg_trgm), fed by the real domain events. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SearchIntegrationTests {

	private static final String PASSWORD = "Sup3r-secret-pw";
	/** Letters and digits only, so it survives tokenizing; makes every fixture of this run unique. */
	private static final String RUN = "zx" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PasswordEncoder encoder;

	@Autowired
	AccessService access;

	@Autowired
	ApplicationEventPublisher publisher;

	MockHttpSession admin;

	private final List<UUID> articles = new ArrayList<>();
	private final List<UUID> orgUnits = new ArrayList<>();
	private final List<UUID> users = new ArrayList<>();
	private final List<UUID> roles = new ArrayList<>();
	private final List<UUID> members = new ArrayList<>();
	private final List<UUID> faculties = new ArrayList<>();
	private UUID space;
	private UUID task;

	@BeforeEach
	void setUp() throws Exception {
		SharedAdmin.ensure(jdbc, encoder);
		admin = signIn(SharedAdmin.LOGIN, SharedAdmin.PASSWORD);
	}

	@AfterEach
	void cleanUp() {
		List<UUID> everything = new ArrayList<>();
		everything.addAll(articles);
		everything.addAll(orgUnits);
		everything.addAll(members);
		if (task != null) {
			everything.add(task);
			for (UUID id : jdbc.queryForList("select id from resolution where task_id = ?", UUID.class, task)) {
				everything.add(id);
				jdbc.update("delete from resolution_history where resolution_id = ?", id);
				jdbc.update("delete from resolution_co_executor where resolution_id = ?", id);
			}
			jdbc.update("delete from resolution where task_id = ?", task);
			jdbc.update("delete from task where id = ?", task);
		}
		for (UUID id : everything) {
			jdbc.update("delete from search_document where entity_id = ?", id);
		}
		for (UUID id : articles) {
			jdbc.update("delete from kb_article_version where article_id = ?", id);
			jdbc.update("delete from kb_article_reference where article_id = ?", id);
			jdbc.update("delete from kb_article_tag where article_id = ?", id);
			jdbc.update("delete from kb_article_attachment where article_id = ?", id);
			jdbc.update("delete from kb_article where id = ?", id);
		}
		for (UUID id : orgUnits) {
			jdbc.update("delete from org_unit where id = ?", id);
		}
		for (UUID id : members) {
			jdbc.update("delete from member_profile where user_id = ?", id);
		}
		for (UUID id : faculties) {
			jdbc.update("delete from faculty where id = ?", id);
		}
		if (space != null) {
			jdbc.update("delete from task_space_status where space_id = ?", space);
			jdbc.update("delete from task_space where id = ?", space);
		}
		for (UUID user : users) {
			jdbc.update("delete from role_assignment where user_id = ?", user);
		}
		for (UUID role : roles) {
			jdbc.update("delete from role_permission where role_id = ?", role);
			jdbc.update("delete from role where id = ?", role);
		}
		for (UUID user : users) {
			jdbc.update("delete from users where id = ?", user);
		}
		articles.clear();
		orgUnits.clear();
		members.clear();
		faculties.clear();
		users.clear();
		roles.clear();
		task = null;
		space = null;
	}

	// ---- helpers ----

	private MockHttpSession signIn(String login, String password) throws Exception {
		MvcResult result = mvc.perform(post("/api/v1/auth/login").with(csrf()).with(request -> {
			request.setRemoteAddr("10.7." + (int) (Math.random() * 250) + "." + (1 + (int) (Math.random() * 250)));
			return request;
		}).contentType(MediaType.APPLICATION_JSON)
				.content("{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}")).andReturn();
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private UUID user(String status) {
		UUID id = UUID.randomUUID();
		users.add(id);
		jdbc.update("insert into users (id, login, password_hash, status, locale, must_change_password, created_at,"
				+ " updated_at) values (?, ?, ?, ?, 'en', false, now(), now())", id,
				"srch-" + id.toString().substring(0, 8), encoder.encode(PASSWORD), status);
		return id;
	}

	private MockHttpSession sessionWith(String... permissionKeys) throws Exception {
		UUID user = user("ACTIVE");
		if (permissionKeys.length > 0) {
			UUID role = UUID.randomUUID();
			roles.add(role);
			jdbc.update("insert into role (id, name, is_system, created_at, updated_at) values (?, ?, false, now(),"
					+ " now())", role, "srch-role-" + role);
			for (String key : permissionKeys) {
				jdbc.update("insert into role_permission (role_id, permission_key) values (?, ?)", role, key);
			}
			access.grantRole(user, role);
		}
		String login = jdbc.queryForObject("select login from users where id = ?", String.class, user);
		return signIn(login, PASSWORD);
	}

	private ResultActions send(MockHttpSession s, String path, String json) throws Exception {
		return mvc.perform(post("/api/v1" + path).with(csrf()).session(s).header(HttpHeaders.ACCEPT_LANGUAGE, "en")
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions search(MockHttpSession s, String q) throws Exception {
		return mvc.perform(get("/api/v1/search").param("q", q).param("limit", "20").session(s)
				.header(HttpHeaders.ACCEPT_LANGUAGE, "en"));
	}

	private static UUID id(ResultActions r) throws Exception {
		return UUID.fromString(JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id"));
	}

	private static String doc(String text) {
		return "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\""
				+ text + "\"}]}]}";
	}

	private UUID article(String title, String bodyText, boolean restricted) throws Exception {
		UUID id = id(send(admin, "/articles", "{\"title\":\"" + title + "\",\"content\":" + doc(bodyText)
				+ ",\"restricted\":" + restricted + ",\"tags\":[]}").andExpect(status().isCreated()));
		articles.add(id);
		return id;
	}

	private void taskFixture() throws Exception {
		space = UUID.randomUUID();
		jdbc.update("insert into task_space (id, name, is_general, created_at, updated_at) values (?, ?, true, now(),"
				+ " now())", space, "Search " + space);
		jdbc.update("insert into task_space_status (id, space_id, name, category, position, created_at, updated_at)"
				+ " values (?, ?, 'Todo', 'TODO', 0, now(), now())", UUID.randomUUID(), space);
		jdbc.update("insert into task_space_status (id, space_id, name, category, position, created_at, updated_at)"
				+ " values (?, ?, 'Done', 'DONE', 1, now(), now())", UUID.randomUUID(), space);
	}

	private UUID task(String title) throws Exception {
		task = id(send(admin, "/tasks", "{\"spaceId\":\"" + space + "\",\"title\":\"" + title + "\"}")
				.andExpect(status().isCreated()));
		return task;
	}

	private UUID resolution(String instruction) throws Exception {
		UUID executor = user("ACTIVE");
		return id(send(admin, "/tasks/" + task + "/resolutions", "{\"executorId\":\"" + executor
				+ "\",\"instruction\":\"" + instruction + "\",\"dueDate\":\"" + LocalDate.now().plusDays(7) + "\"}")
				.andExpect(status().isCreated()));
	}

	private static String unique() {
		return "zy" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
	}

	private static String ids(String group) {
		return "$." + group + ".items[*].id";
	}

	// ---- tests ----

	@Test
	void partialUkrainianWordFindsResolutionAndEnglishFixtureMatches() throws Exception {
		taskFixture();
		task("Budget " + RUN);
		UUID uk = resolution("Резолюція про бюджет " + RUN);
		UUID en = resolution("Resolution about the budget " + RUN);

		search(admin, "резол " + RUN).andExpect(status().isOk()).andExpect(jsonPath(ids("resolutions"), hasItem(uk.toString())))
				;
		search(admin, "резол").andExpect(jsonPath(ids("resolutions"), hasItem(uk.toString())));
		search(admin, "Resolut " + RUN).andExpect(jsonPath(ids("resolutions"), hasItem(en.toString())));
		search(admin, "budgets " + RUN).andExpect(jsonPath(ids("resolutions"), hasItem(en.toString())));
		search(admin, "budg " + RUN).andExpect(jsonPath(ids("tasks"), hasItem(task.toString())));
	}

	@Test
	void smallTyposAndInnerPartsOfWordsMatch() throws Exception {
		taskFixture();
		task("Бюджетування " + RUN);

		search(admin, "бюджетуваня " + RUN).andExpect(jsonPath(ids("tasks"), hasItem(task.toString())));
		search(admin, "джетуван " + RUN).andExpect(jsonPath(ids("tasks"), hasItem(task.toString())));
	}

	@Test
	void articleBodyIsSearchableAndRestrictedArticleIsHiddenWithoutPermission() throws Exception {
		UUID open = article("Open page " + RUN, "Інструкція щодо кошторису " + RUN, false);
		UUID secret = article("Secret page " + RUN, "Таємний кошторис " + RUN, true);
		MockHttpSession reader = sessionWith("kb.view");
		MockHttpSession restrictedReader = sessionWith("kb.view", "kb.restricted.view");
		MockHttpSession nobody = sessionWith();

		search(reader, "кошторис " + RUN).andExpect(jsonPath(ids("articles"), hasItem(open.toString())))
				.andExpect(jsonPath(ids("articles"), not(hasItem(secret.toString()))))
				.andExpect(jsonPath("$.articles.items[0].snippet").value(org.hamcrest.Matchers.containsString("кошторис")));
		search(restrictedReader, "кошторис " + RUN).andExpect(jsonPath(ids("articles"), hasItem(secret.toString())))
				.andExpect(jsonPath(ids("articles"), hasItem(open.toString())));
		search(admin, "кошторис " + RUN).andExpect(jsonPath(ids("articles"), hasItem(secret.toString())));
		search(nobody, "кошторис " + RUN).andExpect(status().isOk()).andExpect(jsonPath("$.articles.items", empty()))
				.andExpect(jsonPath("$.articles.total").value(0));
	}

	@Test
	void groupsRequireTheirViewPermission() throws Exception {
		taskFixture();
		task("Perm " + RUN);
		UUID resolution = resolution("Permcheck " + RUN);
		MockHttpSession kbOnly = sessionWith("kb.view");
		MockHttpSession tasksOnly = sessionWith("tasks.view");

		search(kbOnly, "perm " + RUN).andExpect(jsonPath("$.tasks.items", empty()))
				.andExpect(jsonPath("$.resolutions.items", empty()));
		search(tasksOnly, "perm " + RUN).andExpect(jsonPath(ids("tasks"), hasItem(task.toString())))
				.andExpect(jsonPath(ids("resolutions"), hasItem(resolution.toString())))
				.andExpect(jsonPath("$.people.items", empty()));
	}

	@Test
	void shortAndBlankQueriesReturnEmptyGroupsAndAnonymousIsRejected() throws Exception {
		search(admin, "a").andExpect(status().isOk()).andExpect(jsonPath("$.tasks.items", hasSize(0)));
		search(admin, "   ").andExpect(status().isOk()).andExpect(jsonPath("$.articles.total").value(0));
		mvc.perform(get("/api/v1/search").param("q", "abc")).andExpect(status().isUnauthorized());
	}

	@Test
	void updatingAndArchivingAnArticleUpdatesTheIndex() throws Exception {
		String alpha = unique();
		String beta = unique();
		UUID article = article("Alpha " + alpha, "первісний текст", false);
		search(admin, alpha).andExpect(jsonPath(ids("articles"), hasItem(article.toString())));

		int version = jdbc.queryForObject("select version from kb_article where id = ?", Integer.class, article);
		mvc.perform(put("/api/v1/articles/" + article).with(csrf()).session(admin)
				.contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":" + version + ",\"title\":\"Beta "
						+ beta + "\",\"content\":" + doc("оновлений вміст") + ",\"restricted\":false,\"tags\":[]}"))
				.andExpect(status().isOk());
		search(admin, alpha).andExpect(jsonPath(ids("articles"), not(hasItem(article.toString()))));
		search(admin, beta).andExpect(jsonPath(ids("articles"), hasItem(article.toString())));
		search(admin, "оновлен " + beta).andExpect(jsonPath(ids("articles"), hasItem(article.toString())));

		send(admin, "/articles/" + article + "/archive", "{}").andExpect(status().isOk());
		search(admin, beta).andExpect(jsonPath(ids("articles"), not(hasItem(article.toString()))));

		send(admin, "/articles/" + article + "/restore", "{}").andExpect(status().isOk());
		search(admin, beta).andExpect(jsonPath(ids("articles"), hasItem(article.toString())));
	}

	@Test
	void archivingATaskRemovesItAndItsResolutionsAndRestoreBringsThemBack() throws Exception {
		taskFixture();
		task("Archivable " + RUN);
		UUID resolution = resolution("Архівна резолюція " + RUN);
		search(admin, RUN).andExpect(jsonPath(ids("tasks"), hasItem(task.toString())))
				.andExpect(jsonPath(ids("resolutions"), hasItem(resolution.toString())));

		send(admin, "/tasks/" + task + "/archive", "{}").andExpect(status().isOk());
		search(admin, RUN).andExpect(jsonPath(ids("tasks"), not(hasItem(task.toString()))))
				.andExpect(jsonPath(ids("resolutions"), not(hasItem(resolution.toString()))));

		send(admin, "/tasks/" + task + "/restore", "{}").andExpect(status().isOk());
		search(admin, RUN).andExpect(jsonPath(ids("tasks"), hasItem(task.toString())))
				.andExpect(jsonPath(ids("resolutions"), hasItem(resolution.toString())));
	}

	@Test
	void orgUnitsAreIndexedRenamedAndRemovedOnArchive() throws Exception {
		String science = unique();
		String education = unique();
		UUID unit = id(send(admin, "/org-units", "{\"name\":\"Відділ Науки " + science
				+ "\",\"type\":\"WORKING_GROUP\"}").andExpect(status().isCreated()));
		orgUnits.add(unit);
		search(admin, "наук " + science).andExpect(jsonPath(ids("orgUnits"), hasItem(unit.toString())));

		mvc.perform(put("/api/v1/org-units/" + unit).with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Відділ Освіти " + education + "\"}")).andExpect(status().isOk());
		search(admin, science).andExpect(jsonPath(ids("orgUnits"), not(hasItem(unit.toString()))));
		search(admin, education).andExpect(jsonPath(ids("orgUnits"), hasItem(unit.toString())));

		send(admin, "/org-units/" + unit + "/archive", "{}").andExpect(status().isOk());
		search(admin, education).andExpect(jsonPath(ids("orgUnits"), not(hasItem(unit.toString()))));
	}

	@Test
	void peopleAreIndexedFromMemberEventsAndLeaveOnArchive() throws Exception {
		UUID faculty = UUID.randomUUID();
		faculties.add(faculty);
		jdbc.update("insert into faculty (id, code, name_uk, name_en, created_at, updated_at) values (?, ?, ?, ?,"
				+ " now(), now())", faculty, "F" + RUN, "Факультет " + RUN, "Faculty " + RUN);
		UUID member = user("ACTIVE");
		members.add(member);
		jdbc.update("insert into member_profile (id, user_id, last_name, first_name, faculty_id, study_group, telegram,"
				+ " joined_at, created_at, updated_at) values (?, ?, ?, ?, ?, 'ХХ-01', '@fake_user', current_date,"
				+ " now(), now())", UUID.randomUUID(), member, "Тестенко" + RUN, "Олена", faculty);
		publisher.publishEvent(new MemberEvent(MemberEvent.Kind.CREATED, member));

		search(admin, "тестен").andExpect(jsonPath(ids("people"), hasItem(member.toString())));
		search(admin, "олена тестенко" + RUN).andExpect(jsonPath(ids("people"), hasItem(member.toString())));

		jdbc.update("update member_profile set archived_at = now() where user_id = ?", member);
		publisher.publishEvent(new MemberEvent(MemberEvent.Kind.ARCHIVED, member));
		search(admin, "тестенко" + RUN).andExpect(jsonPath(ids("people"), not(hasItem(member.toString()))));
	}

	@Test
	void reindexRebuildsTheIndexIsAuditedAndNeedsSettingsManage() throws Exception {
		UUID article = article("Reindex " + RUN, "тіло статті", true);
		jdbc.update("delete from search_document where entity_id = ?", article);
		search(admin, "reindex" + RUN).andExpect(jsonPath(ids("articles"), not(hasItem(article.toString()))));
		long before = jdbc.queryForObject("select count(*) from audit_log where entity_type = 'SearchIndex'",
				Long.class);

		send(sessionWith("kb.view", "tasks.view"), "/search/reindex", "{}").andExpect(status().isForbidden());
		send(admin, "/search/reindex", "{}").andExpect(status().isOk()).andExpect(jsonPath("$.articles").isNumber());

		search(admin, "reindex" + RUN).andExpect(jsonPath(ids("articles"), hasItem(article.toString())))
				.andExpect(jsonPath("$.articles.items[0].restricted").value(true));
		assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_type = 'SearchIndex'",
				Long.class)).isEqualTo(before + 1);
		// restricted flag survives the reindex: a reader without kb.restricted.view still cannot find it
		search(sessionWith("kb.view"), "reindex" + RUN).andExpect(jsonPath("$.articles.items", empty()));
	}

}
