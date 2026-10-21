package com.kpi.studentcouncil.crm.knowledge.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.kpi.studentcouncil.crm.TestcontainersConfiguration;
import com.kpi.studentcouncil.crm.access.AccessService;
import com.kpi.studentcouncil.crm.knowledge.ArticleEvent;
import com.kpi.studentcouncil.crmtest.SharedAdmin;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class ArticleIntegrationTests {

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

	@Autowired
	ApplicationEvents events;

	MockHttpSession admin;

	private final List<UUID> articles = new ArrayList<>();
	private final List<UUID> files = new ArrayList<>();
	private final List<UUID> users = new ArrayList<>();
	private final List<UUID> roles = new ArrayList<>();

	@BeforeEach
	void setUp() throws Exception {
		SharedAdmin.ensure(jdbc, encoder);
		admin = signIn(SharedAdmin.LOGIN, SharedAdmin.PASSWORD);
	}

	/** Removes everything this class created, children first, so other test classes sharing the DB are unaffected. */
	@AfterEach
	void cleanUp() {
		for (UUID id : articles) {
			jdbc.update("delete from kb_article_version where article_id = ?", id);
			jdbc.update("delete from kb_article_reference where article_id = ?", id);
			jdbc.update("delete from kb_article_tag where article_id = ?", id);
			jdbc.update("delete from kb_article_attachment where article_id = ?", id);
		}
		jdbc.update("update kb_article set parent_article_id = null where id = any (?)",
				(Object) articles.toArray(new UUID[0]));
		for (UUID id : articles) {
			jdbc.update("delete from kb_article where id = ?", id);
		}
		for (UUID file : files) {
			jdbc.update("delete from file_object where id = ?", file);
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
		files.clear();
		users.clear();
		roles.clear();
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

	private MockHttpSession sessionWith(String... permissionKeys) throws Exception {
		UUID user = UUID.randomUUID();
		users.add(user);
		jdbc.update("insert into users (id, login, password_hash, status, locale, must_change_password, created_at,"
				+ " updated_at) values (?, ?, ?, 'ACTIVE', 'en', false, now(), now())", user,
				"kb-user-" + user.toString().substring(0, 8), encoder.encode(PASSWORD));
		if (permissionKeys.length > 0) {
			UUID role = UUID.randomUUID();
			roles.add(role);
			jdbc.update("insert into role (id, name, is_system, created_at, updated_at) values (?, ?, false, now(),"
					+ " now())", role, "kb-role-" + role);
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

	/** Sends an edit; unless the body names {@code expectedVersion}, the article's current version is filled in. */
	private ResultActions putJson(MockHttpSession s, String path, String json) throws Exception {
		if (!json.contains("expectedVersion")) {
			UUID article = UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
			int current = jdbc.queryForObject("select version from kb_article where id = ?", Integer.class, article);
			json = "{\"expectedVersion\":" + current + "," + json.substring(1);
		}
		return mvc.perform(put("/api/v1" + path).with(csrf()).session(s).header(HttpHeaders.ACCEPT_LANGUAGE, "en")
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions read(MockHttpSession s, String path) throws Exception {
		return mvc.perform(get("/api/v1" + path).session(s).header(HttpHeaders.ACCEPT_LANGUAGE, "en"));
	}

	private static UUID id(ResultActions r) throws Exception {
		return UUID.fromString(JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id"));
	}

	private static String body(String title, UUID parent, boolean restricted, String tags) {
		return "{\"title\":\"" + title + "\"," + (parent == null ? "" : "\"parentArticleId\":\"" + parent + "\",")
				+ "\"restricted\":" + restricted + ",\"tags\":[" + tags + "]}";
	}

	private UUID article(String title, UUID parent, boolean restricted, String tags) throws Exception {
		UUID id = id(send(admin, "/articles", body(title + " " + RUN, parent, restricted, tags))
				.andExpect(status().isCreated()));
		articles.add(id);
		return id;
	}

	private UUID article(String title, UUID parent) throws Exception {
		return article(title, parent, false, "");
	}

	private UUID file() {
		UUID id = UUID.randomUUID();
		files.add(id);
		jdbc.update("insert into file_object (id, storage_key, file_name, mime_type, size_bytes, uploaded_by, created_at,"
				+ " updated_at) values (?, ?, 'plan.pdf', 'application/pdf', 10, ?, now(), now())", id,
				"test-" + id, UUID.randomUUID());
		return id;
	}

	private long audit(UUID id, String action) {
		return jdbc.queryForObject(
				"select count(*) from audit_log where entity_type = 'Article' and entity_id = ? and action = ?",
				Long.class, id, action);
	}

	@Test
	void permissionMatrix() throws Exception {
		UUID article = article("Matrix", null);
		MockHttpSession plain = sessionWith();
		MockHttpSession viewer = sessionWith("kb.view");
		MockHttpSession editor = sessionWith("kb.view", "kb.edit");
		MockHttpSession archiver = sessionWith("kb.view", "kb.archive");

		mvc.perform(get("/api/v1/articles")).andExpect(status().isUnauthorized());
		read(plain, "/articles").andExpect(status().isForbidden());
		read(plain, "/articles/" + article).andExpect(status().isForbidden());
		read(plain, "/articles/tree").andExpect(status().isForbidden());
		read(plain, "/articles/tags").andExpect(status().isForbidden());
		read(plain, "/articles/" + article + "/children").andExpect(status().isForbidden());
		read(viewer, "/articles").andExpect(status().isOk());
		read(viewer, "/articles/" + article).andExpect(status().isOk());
		read(viewer, "/articles/tree").andExpect(status().isOk());
		read(viewer, "/articles/tags").andExpect(status().isOk());
		read(viewer, "/articles/" + article + "/children").andExpect(status().isOk());

		String create = body("Nope " + RUN, null, false, "");
		send(viewer, "/articles", create).andExpect(status().isForbidden());
		send(archiver, "/articles", create).andExpect(status().isForbidden());
		putJson(viewer, "/articles/" + article, create).andExpect(status().isForbidden());
		send(viewer, "/articles/" + article + "/move", "{}").andExpect(status().isForbidden());
		send(editor, "/articles/" + article + "/archive", "").andExpect(status().isForbidden());
		send(editor, "/articles/" + article + "/restore", "").andExpect(status().isForbidden());
		send(viewer, "/articles/" + article + "/archive", "").andExpect(status().isForbidden());

		UUID created = id(send(editor, "/articles", body("By editor " + RUN, null, false, "")).andExpect(status().isCreated()));
		articles.add(created);
		putJson(editor, "/articles/" + created, body("By editor 2 " + RUN, null, false, "")).andExpect(status().isOk());
		send(archiver, "/articles/" + created + "/archive", "").andExpect(status().isOk());
		send(archiver, "/articles/" + created + "/restore", "").andExpect(status().isOk());
	}

	@Test
	void createReadEditAndVersionCounter() throws Exception {
		UUID file = file();
		UUID person = UUID.randomUUID();
		UUID task = UUID.randomUUID();
		String content = "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\","
				+ "\"text\":\"Hello\"},{\"type\":\"mention\",\"attrs\":{\"id\":\"" + person + "\"}},"
				+ "{\"type\":\"mention\",\"attrs\":{\"id\":\"" + task + "\",\"entityType\":\"task\"}}]}]}";
		UUID id = id(send(admin, "/articles", "{\"title\":\"  Rich " + RUN + " \",\"content\":" + content
				+ ",\"tags\":[\" Policy \",\"policy\",\"FAQ\"],\"attachmentIds\":[\"" + file + "\"]}")
				.andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(1))
				.andExpect(jsonPath("$.title").value("Rich " + RUN)).andExpect(jsonPath("$.restricted").value(false))
				.andExpect(jsonPath("$.tags", hasSize(2))).andExpect(jsonPath("$.tags[0]").value("faq"))
				.andExpect(jsonPath("$.attachments[0].id").value(file.toString()))
				.andExpect(jsonPath("$.references", hasSize(2))));
		articles.add(id);
		read(admin, "/articles/" + id).andExpect(status().isOk())
				.andExpect(jsonPath("$.content.content[0].content[0].text").value("Hello"))
				.andExpect(jsonPath("$.references[?(@.type=='PERSON')].id").value(hasItem(person.toString())))
				.andExpect(jsonPath("$.references[?(@.type=='TASK')].id").value(hasItem(task.toString())));
		assertThat(jdbc.queryForObject("select count(*) from kb_article_reference where article_id = ?", Long.class, id))
				.isEqualTo(2);
		assertThat(audit(id, "CREATE")).isEqualTo(1);

		putJson(admin, "/articles/" + id, "{\"title\":\"Rich 2 " + RUN + "\",\"tags\":[\"faq\"]}")
				.andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2))
				.andExpect(jsonPath("$.attachments", hasSize(0))).andExpect(jsonPath("$.references", hasSize(0)))
				.andExpect(jsonPath("$.content.type").value("doc"));
		putJson(admin, "/articles/" + id, "{\"title\":\"Rich 3 " + RUN + "\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(3));
		assertThat(audit(id, "UPDATE")).isEqualTo(2);
		assertThat(jdbc.queryForObject("select count(*) from kb_article_reference where article_id = ?", Long.class, id))
				.isZero();
	}

	@Test
	void validationRejectsBadInput() throws Exception {
		send(admin, "/articles", "{\"title\":\"  \"}").andExpect(status().isBadRequest());
		send(admin, "/articles", "{\"title\":\"X\",\"content\":{\"type\":\"paragraph\"}}")
				.andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("ARTICLE_CONTENT_INVALID"));
		send(admin, "/articles", "{\"title\":\"X\",\"attachmentIds\":[\"" + UUID.randomUUID() + "\"]}")
				.andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("ARTICLE_ATTACHMENT_INVALID"));
		send(admin, "/articles", "{\"title\":\"X\",\"parentArticleId\":\"" + UUID.randomUUID() + "\"}")
				.andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("ARTICLE_PARENT_INVALID"));
		send(admin, "/articles", "{\"title\":\"X\",\"tags\":[\" \"]}").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("ARTICLE_TAG_INVALID"));
		send(admin, "/articles", "{\"title\":\"X\",\"content\":{\"type\":\"doc\",\"content\":[{\"type\":\"mention\","
				+ "\"attrs\":{\"id\":\"nope\"}}]}}").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("ARTICLE_REFERENCE_INVALID"));
	}

	@Test
	void hierarchyTreeChildrenAndMove() throws Exception {
		UUID root = article("Root", null);
		UUID a = article("A", root);
		UUID b = article("B", root);
		UUID a1 = article("A1", a);

		read(admin, "/articles/" + a1).andExpect(jsonPath("$.parentArticleId").value(a.toString()))
				.andExpect(jsonPath("$.ancestors", hasSize(2))).andExpect(jsonPath("$.ancestors[0].id").value(root.toString()))
				.andExpect(jsonPath("$.ancestors[1].id").value(a.toString()));
		read(admin, "/articles/" + root + "/children").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].id").value(a.toString())).andExpect(jsonPath("$[1].id").value(b.toString()));
		read(admin, "/articles/tree").andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id=='" + root + "')].children[0].children[0].id").value(hasItem(a1.toString())));
		read(admin, "/articles?parentArticleId=" + a).andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].id").value(a1.toString()));

		// cycles: onto itself, onto a child, onto a deeper descendant
		send(admin, "/articles/" + root + "/move", "{\"parentArticleId\":\"" + root + "\"}")
				.andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("ARTICLE_CYCLE"));
		send(admin, "/articles/" + root + "/move", "{\"parentArticleId\":\"" + a + "\"}")
				.andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("ARTICLE_CYCLE"));
		send(admin, "/articles/" + root + "/move", "{\"parentArticleId\":\"" + a1 + "\"}")
				.andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("ARTICLE_CYCLE"));
		read(admin, "/articles/" + root).andExpect(jsonPath("$.parentArticleId").doesNotExist());

		// valid moves
		send(admin, "/articles/" + a1 + "/move", "{\"parentArticleId\":\"" + b + "\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.parentArticleId").value(b.toString()));
		send(admin, "/articles/" + a1 + "/move", "{}").andExpect(status().isOk())
				.andExpect(jsonPath("$.parentArticleId").doesNotExist());
		send(admin, "/articles/" + b + "/move", "{\"parentArticleId\":\"" + a + "\"}").andExpect(status().isOk());
		assertThat(audit(a1, "UPDATE")).isEqualTo(2);
		read(admin, "/articles/" + b + "/children").andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void archiveIsRefusedWhileActiveChildrenExist() throws Exception {
		UUID parent = article("Parent", null);
		UUID child = article("Child", parent);

		send(admin, "/articles/" + parent + "/archive", "").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ARTICLE_HAS_ACTIVE_CHILDREN"));
		read(admin, "/articles/" + parent).andExpect(jsonPath("$.archived").value(false));

		send(admin, "/articles/" + child + "/archive", "").andExpect(status().isOk())
				.andExpect(jsonPath("$.archived").value(true));
		send(admin, "/articles/" + child + "/archive", "").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ALREADY_ARCHIVED"));
		putJson(admin, "/articles/" + child, body("Edit " + RUN, null, false, "")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ARTICLE_ARCHIVED"));
		send(admin, "/articles/" + parent + "/archive", "").andExpect(status().isOk());
		assertThat(audit(parent, "ARCHIVE")).isEqualTo(1);

		// archived articles are hidden by default, kept in the database and listed on request
		read(admin, "/articles/tree").andExpect(jsonPath("$..id").value(not(hasItem(parent.toString()))));
		read(admin, "/articles?tag=zzz-none").andExpect(jsonPath("$.content", hasSize(0)));
		read(admin, "/articles?parentArticleId=" + parent).andExpect(jsonPath("$.content", hasSize(0)));
		read(admin, "/articles?parentArticleId=" + parent + "&includeArchived=true")
				.andExpect(jsonPath("$.content", hasSize(1)));
		send(admin, "/articles", body("Under archived " + RUN, parent, false, "")).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("ARTICLE_PARENT_INVALID"));

		// the child cannot be restored under an archived parent; restoring top-down works
		send(admin, "/articles/" + child + "/restore", "").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ARTICLE_PARENT_ARCHIVED"));
		send(admin, "/articles/" + parent + "/restore", "").andExpect(status().isOk());
		send(admin, "/articles/" + child + "/restore", "").andExpect(status().isOk());
		send(admin, "/articles/" + child + "/restore", "").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("NOT_ARCHIVED"));
		assertThat(audit(child, "RESTORE")).isEqualTo(1);
	}

	@Test
	void tagsAreListedAndFilterArticles() throws Exception {
		String tag = "t" + RUN;
		UUID one = article("Tagged one", null, false, "\"" + tag + "\",\"other" + RUN + "\"");
		UUID two = article("Tagged two", null, false, "\"" + tag.toUpperCase() + "\"");
		article("Untagged", null);

		read(admin, "/articles?tag=" + tag.toUpperCase()).andExpect(jsonPath("$.content", hasSize(2)))
				.andExpect(jsonPath("$.content[0].id").value(one.toString()))
				.andExpect(jsonPath("$.content[1].id").value(two.toString()));
		read(admin, "/articles/tags").andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.tag=='" + tag + "')].count").value(hasItem(2)))
				.andExpect(jsonPath("$[?(@.tag=='other" + RUN + "')].count").value(hasItem(1)));
	}

	@Test
	void restrictedArticlesAreInvisibleWithoutPermission() throws Exception {
		String tag = "r" + RUN;
		UUID open = article("Open", null, false, "\"" + tag + "\"");
		UUID secret = article("Secret", null, true, "\"" + tag + "\"");
		UUID openChild = article("Child of secret", secret);
		MockHttpSession viewer = sessionWith("kb.view");
		MockHttpSession privileged = sessionWith("kb.view", "kb.restricted.view");
		MockHttpSession editor = sessionWith("kb.view", "kb.edit", "kb.archive");

		// direct access: 404, not 403
		read(viewer, "/articles/" + secret).andExpect(status().isNotFound());
		read(viewer, "/articles/" + secret + "/children").andExpect(status().isNotFound());
		read(privileged, "/articles/" + secret).andExpect(status().isOk()).andExpect(jsonPath("$.restricted").value(true));
		read(admin, "/articles/" + secret).andExpect(status().isOk());
		read(viewer, "/articles/" + open).andExpect(status().isOk());

		// lists, filters, tree and tag counts
		read(viewer, "/articles?tag=" + tag).andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].id").value(open.toString()));
		read(privileged, "/articles?tag=" + tag).andExpect(jsonPath("$.content", hasSize(2)));
		read(viewer, "/articles?includeArchived=true&size=100").andExpect(jsonPath("$.content[*].id").value(not(hasItem(secret.toString()))));
		read(viewer, "/articles/tree").andExpect(jsonPath("$..id").value(not(hasItem(secret.toString()))))
				.andExpect(jsonPath("$..id").value(not(hasItem(openChild.toString()))));
		read(privileged, "/articles/tree").andExpect(jsonPath("$..id").value(hasItem(secret.toString())))
				.andExpect(jsonPath("$..id").value(hasItem(openChild.toString())));
		read(viewer, "/articles/tags").andExpect(jsonPath("$[?(@.tag=='" + tag + "')].count").value(hasItem(1)));
		read(privileged, "/articles/tags").andExpect(jsonPath("$[?(@.tag=='" + tag + "')].count").value(hasItem(2)));

		// changing a restricted article is impossible for an actor who cannot see it
		putJson(editor, "/articles/" + secret, body("Hijack " + RUN, null, true, "")).andExpect(status().isNotFound());
		send(editor, "/articles/" + secret + "/move", "{}").andExpect(status().isNotFound());
		send(editor, "/articles/" + secret + "/archive", "").andExpect(status().isNotFound());
		send(editor, "/articles", body("Under secret " + RUN, secret, false, "")).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("ARTICLE_PARENT_INVALID"));

		// marking restricted needs kb.restricted.view
		send(editor, "/articles", body("Mine " + RUN, null, true, "")).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ARTICLE_RESTRICTED_FORBIDDEN"));
		putJson(editor, "/articles/" + open, body("Open " + RUN, null, true, "")).andExpect(status().isForbidden());
		putJson(editor, "/articles/" + open, body("Open edited " + RUN, null, false, "")).andExpect(status().isOk());

		// the audit trail of a restricted article does not carry its title
		String snapshot = jdbc.queryForObject("select after_state::text from audit_log where entity_type = 'Article'"
				+ " and entity_id = ? and action = 'CREATE'", String.class, secret);
		assertThat(snapshot).doesNotContain("Secret").contains("\"restricted\": true");
	}

	private static String doc(String text) {
		return "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\""
				+ text + "\"}]}]}";
	}

	private long versionRows(UUID id) {
		return jdbc.queryForObject("select count(*) from kb_article_version where article_id = ?", Long.class, id);
	}

	private long events(UUID id, ArticleEvent.Kind kind) {
		return events.stream(ArticleEvent.class).filter(e -> e.id().equals(id) && e.kind() == kind).count();
	}

	@Test
	void everySaveCreatesExactlyOneVersionAndPublishesAnEvent() throws Exception {
		UUID id = id(send(admin, "/articles", "{\"title\":\"Versioned " + RUN + "\",\"content\":" + doc("one") + "}")
				.andExpect(status().isCreated()));
		articles.add(id);
		assertThat(versionRows(id)).isEqualTo(1);
		assertThat(events(id, ArticleEvent.Kind.CREATED)).isEqualTo(1);

		putJson(admin, "/articles/" + id, "{\"title\":\"Versioned 2 " + RUN + "\",\"content\":" + doc("two") + "}")
				.andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
		putJson(admin, "/articles/" + id, "{\"title\":\"Versioned 3 " + RUN + "\",\"content\":" + doc("three") + "}")
				.andExpect(status().isOk()).andExpect(jsonPath("$.version").value(3));
		assertThat(versionRows(id)).isEqualTo(3);
		assertThat(events(id, ArticleEvent.Kind.UPDATED)).isEqualTo(2);

		read(admin, "/articles/" + id + "/versions").andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(3))).andExpect(jsonPath("$.content[0].versionNumber").value(3))
				.andExpect(jsonPath("$.content[2].versionNumber").value(1))
				.andExpect(jsonPath("$.content[0].editedBy").exists()).andExpect(jsonPath("$.content[0].editedAt").exists())
				.andExpect(jsonPath("$.content[0].content").doesNotExist());
		read(admin, "/articles/" + id + "/versions/2").andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Versioned 2 " + RUN))
				.andExpect(jsonPath("$.content.content[0].content[0].text").value("two"));
		read(admin, "/articles/" + id + "/versions/9").andExpect(status().isNotFound());

		// a move is not a save
		UUID other = article("Other", null);
		send(admin, "/articles/" + id + "/move", "{\"parentArticleId\":\"" + other + "\"}").andExpect(status().isOk());
		assertThat(versionRows(id)).isEqualTo(3);
	}

	@Test
	void staleSaveIsRejectedWithConflict() throws Exception {
		UUID id = article("Concurrent", null);
		putJson(admin, "/articles/" + id, "{\"expectedVersion\":1,\"title\":\"First " + RUN + "\"}")
				.andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
		putJson(admin, "/articles/" + id, "{\"expectedVersion\":1,\"title\":\"Stale " + RUN + "\"}")
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ARTICLE_VERSION_CONFLICT"));
		send(admin, "/articles/" + id + "/versions/1/restore", "{\"expectedVersion\":1}")
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ARTICLE_VERSION_CONFLICT"));
		assertThat(versionRows(id)).isEqualTo(2);
		mvc.perform(put("/api/v1/articles/" + id).with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"No version " + RUN + "\"}")).andExpect(status().isBadRequest());
	}

	@Test
	void restoreAppendsANewVersionAndKeepsHistory() throws Exception {
		UUID id = id(send(admin, "/articles", "{\"title\":\"Restore " + RUN + "\",\"content\":" + doc("original")
				+ ",\"tags\":[\"keep\"]}").andExpect(status().isCreated()));
		articles.add(id);
		putJson(admin, "/articles/" + id, "{\"title\":\"Changed " + RUN + "\",\"content\":" + doc("changed")
				+ ",\"tags\":[\"keep\",\"more\"]}").andExpect(status().isOk());

		send(admin, "/articles/" + id + "/versions/1/restore", "").andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(3)).andExpect(jsonPath("$.title").value("Restore " + RUN))
				.andExpect(jsonPath("$.content.content[0].content[0].text").value("original"))
				.andExpect(jsonPath("$.tags", hasSize(2)));
		assertThat(versionRows(id)).isEqualTo(3);
		assertThat(jdbc.queryForObject("select title from kb_article_version where article_id = ? and version_number = 2",
				String.class, id)).isEqualTo("Changed " + RUN);
		assertThat(jdbc.queryForObject(
				"select content::text from kb_article_version where article_id = ? and version_number = 3", String.class,
				id)).isEqualTo(jdbc.queryForObject(
						"select content::text from kb_article_version where article_id = ? and version_number = 1",
						String.class, id));
		assertThat(audit(id, "UPDATE")).isEqualTo(2);
		assertThat(events(id, ArticleEvent.Kind.UPDATED)).isEqualTo(2);
		send(admin, "/articles/" + id + "/versions/42/restore", "").andExpect(status().isNotFound());

		send(admin, "/articles/" + id + "/archive", "").andExpect(status().isOk());
		send(admin, "/articles/" + id + "/versions/1/restore", "").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ARTICLE_ARCHIVED"));
		read(admin, "/articles/" + id + "/versions").andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(3)));
	}

	@Test
	void versionPermissionsAndRestrictedVisibility() throws Exception {
		UUID open = article("Open versions", null);
		UUID secret = article("Secret versions", null, true, "");
		MockHttpSession plain = sessionWith();
		MockHttpSession viewer = sessionWith("kb.view");
		MockHttpSession editor = sessionWith("kb.view", "kb.edit");
		MockHttpSession privileged = sessionWith("kb.view", "kb.edit", "kb.restricted.view");

		read(plain, "/articles/" + open + "/versions").andExpect(status().isForbidden());
		read(viewer, "/articles/" + open + "/versions").andExpect(status().isOk());
		read(viewer, "/articles/" + open + "/versions/1").andExpect(status().isOk());
		send(viewer, "/articles/" + open + "/versions/1/restore", "").andExpect(status().isForbidden());
		send(editor, "/articles/" + open + "/versions/1/restore", "").andExpect(status().isOk());

		read(editor, "/articles/" + secret + "/versions").andExpect(status().isNotFound());
		read(editor, "/articles/" + secret + "/versions/1").andExpect(status().isNotFound());
		send(editor, "/articles/" + secret + "/versions/1/restore", "").andExpect(status().isNotFound());
		read(privileged, "/articles/" + secret + "/versions").andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)));
		send(privileged, "/articles/" + secret + "/versions/1/restore", "").andExpect(status().isOk());
	}

}
