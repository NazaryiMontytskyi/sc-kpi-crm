package com.kpi.studentcouncil.crm.tasks.internal;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.concurrent.CopyOnWriteArrayList;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
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
import com.kpi.studentcouncil.crm.tasks.UserMentioned;
import com.kpi.studentcouncil.crmtest.SharedAdmin;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, CommentIntegrationTests.EventsConfig.class })
class CommentIntegrationTests {

	private static final String PASSWORD = "Sup3r-secret-pw";

	/** Collects the published mention events. */
	static class CollectedMentions {

		final List<UserMentioned> all = new CopyOnWriteArrayList<>();

		@EventListener
		void on(UserMentioned event) {
			all.add(event);
		}

	}

	@TestConfiguration(proxyBeanMethods = false)
	static class EventsConfig {

		@Bean
		CollectedMentions collectedMentions() {
			return new CollectedMentions();
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
	CollectedMentions mentions;

	MockHttpSession admin;

	private final List<UUID> spaces = new ArrayList<>();
	private final List<UUID> tasks = new ArrayList<>();
	private final List<UUID> comments = new ArrayList<>();
	private final List<UUID> files = new ArrayList<>();
	private final List<UUID> users = new ArrayList<>();
	private final List<UUID> roles = new ArrayList<>();

	@BeforeEach
	void setUp() throws Exception {
		SharedAdmin.ensure(jdbc, encoder);
		admin = signIn(SharedAdmin.LOGIN, SharedAdmin.PASSWORD);
		mentions.all.clear();
	}

	/** Removes everything this class created, children first (FK order). */
	@AfterEach
	void cleanUp() {
		for (UUID comment : comments) {
			jdbc.update("delete from comment_mention where comment_id = ?", comment);
			jdbc.update("delete from comment_attachment where comment_id = ?", comment);
			jdbc.update("delete from comment where id = ?", comment);
		}
		for (UUID space : spaces) {
			jdbc.update("delete from task where space_id = ?", space);
			jdbc.update("delete from task_space_status where space_id = ?", space);
			jdbc.update("delete from task_space where id = ?", space);
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
		spaces.clear();
		tasks.clear();
		comments.clear();
		files.clear();
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

	private record Account(UUID id, MockHttpSession session) {
	}

	private Account account(String... permissionKeys) throws Exception {
		UUID user = UUID.randomUUID();
		users.add(user);
		jdbc.update("insert into users (id, login, password_hash, status, locale, must_change_password, created_at,"
				+ " updated_at) values (?, ?, ?, 'ACTIVE', 'en', false, now(), now())", user,
				"cmt-user-" + user.toString().substring(0, 8), encoder.encode(PASSWORD));
		if (permissionKeys.length > 0) {
			UUID role = UUID.randomUUID();
			roles.add(role);
			jdbc.update("insert into role (id, name, is_system, created_at, updated_at) values (?, ?, false, now(),"
					+ " now())", role, "cmt-role-" + role);
			for (String key : permissionKeys) {
				jdbc.update("insert into role_permission (role_id, permission_key) values (?, ?)", role, key);
			}
			access.grantRole(user, role);
		}
		String login = jdbc.queryForObject("select login from users where id = ?", String.class, user);
		return new Account(user, signIn(login, PASSWORD));
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

	private UUID task() throws Exception {
		UUID space = id(send(admin, "/spaces", "{\"name\":\"Space " + UUID.randomUUID() + "\",\"general\":true}")
				.andExpect(status().isCreated()));
		spaces.add(space);
		UUID task = id(send(admin, "/tasks", "{\"spaceId\":\"" + space + "\",\"title\":\"T " + UUID.randomUUID() + "\"}")
				.andExpect(status().isCreated()));
		tasks.add(task);
		return task;
	}

	private UUID file() {
		UUID id = UUID.randomUUID();
		files.add(id);
		jdbc.update("insert into file_object (id, storage_key, file_name, mime_type, size_bytes, uploaded_by, created_at,"
				+ " updated_at) values (?, ?, 'plan.pdf', 'application/pdf', 10, ?, now(), now())", id,
				"test-" + id, UUID.randomUUID());
		return id;
	}

	private static String doc(String text, UUID... mentioned) {
		StringBuilder inline = new StringBuilder("{\"type\":\"text\",\"text\":\"" + text + "\"}");
		for (UUID m : mentioned) {
			inline.append(",{\"type\":\"mention\",\"attrs\":{\"id\":\"").append(m).append("\",\"label\":\"Fake\"}}");
		}
		return "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[" + inline + "]}]}";
	}

	private static String ids(UUID... ids) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < ids.length; i++) {
			sb.append(i == 0 ? "" : ",").append('"').append(ids[i]).append('"');
		}
		return sb.append(']').toString();
	}

	private static String create(UUID task, String body, UUID[] mentioned, UUID... attachments) {
		return "{\"targetType\":\"TASK\",\"targetId\":\"" + task + "\",\"body\":" + body + ",\"mentionUserIds\":"
				+ ids(mentioned) + ",\"attachmentIds\":" + ids(attachments) + "}";
	}

	private UUID comment(MockHttpSession s, UUID task, String text) throws Exception {
		UUID id = id(send(s, "/comments", create(task, doc(text), new UUID[0])).andExpect(status().isCreated()));
		comments.add(id);
		return id;
	}

	private long audit(UUID id, String action) {
		return jdbc.queryForObject(
				"select count(*) from audit_log where entity_type = 'Comment' and entity_id = ? and action = ?",
				Long.class, id, action);
	}

	@Test
	void anAdvisorWithViewOnlyAndAnyAuthenticatedUserCanComment() throws Exception {
		UUID task = task();
		Account advisor = account("tasks.view");
		Account plain = account();

		UUID first = comment(advisor.session(), task, "advice");
		UUID second = comment(plain.session(), task, "me too");
		assertThat(audit(first, "CREATE")).isEqualTo(1);
		assertThat(audit(second, "CREATE")).isEqualTo(1);

		read(advisor.session(), "/comments?targetType=TASK&targetId=" + task).andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(2)))
				.andExpect(jsonPath("$.content[0].id").value(first.toString()))
				.andExpect(jsonPath("$.content[0].authorId").value(advisor.id().toString()))
				.andExpect(jsonPath("$.content[1].id").value(second.toString()))
				.andExpect(jsonPath("$.content[0].edited").value(false));
		// reading needs the view permission of the target
		read(plain.session(), "/comments?targetType=TASK&targetId=" + task).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/comments?targetType=TASK&targetId=" + task)).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/comments").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(create(task, doc("x"), new UUID[0]))).andExpect(status().isUnauthorized());
	}

	@Test
	void listIsPaginatedOldestFirst() throws Exception {
		UUID task = task();
		UUID a = comment(admin, task, "one");
		UUID b = comment(admin, task, "two");
		UUID c = comment(admin, task, "three");
		read(admin, "/comments?targetType=TASK&targetId=" + task + "&size=2").andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.content", hasSize(2)))
				.andExpect(jsonPath("$.content[0].id").value(a.toString()))
				.andExpect(jsonPath("$.content[1].id").value(b.toString()));
		read(admin, "/comments?targetType=TASK&targetId=" + task + "&size=2&page=1")
				.andExpect(jsonPath("$.content[0].id").value(c.toString()));
	}

	@Test
	void mentionsAreStoredAsUserIdsAndPublishEventsOnlyForNewOnesOnEdit() throws Exception {
		UUID task = task();
		Account author = account("tasks.view");
		Account bob = account();
		Account carol = account();

		UUID cid = id(send(author.session(), "/comments", create(task, doc("hi ", bob.id()), new UUID[] { bob.id() }))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.mentionUserIds[0]").value(bob.id().toString())));
		comments.add(cid);
		assertThat(jdbc.queryForList("select user_id from comment_mention where comment_id = ?", UUID.class, cid))
				.containsExactly(bob.id());
		assertThat(mentions.all).singleElement().satisfies(e -> {
			assertThat(e.userId()).isEqualTo(bob.id());
			assertThat(e.commentId()).isEqualTo(cid);
			assertThat(e.authorId()).isEqualTo(author.id());
			assertThat(e.targetId()).isEqualTo(task);
		});

		mentions.all.clear();
		putJson(author.session(), "/comments/" + cid, "{\"body\":" + doc("hi again ", bob.id(), carol.id())
				+ ",\"mentionUserIds\":" + ids(bob.id(), carol.id()) + "}").andExpect(status().isOk())
				.andExpect(jsonPath("$.edited").value(true)).andExpect(jsonPath("$.mentionUserIds", hasSize(2)));
		assertThat(mentions.all).singleElement().satisfies(e -> assertThat(e.userId()).isEqualTo(carol.id()));

		// history: the edit keeps before and after in the audit log
		assertThat(audit(cid, "UPDATE")).isEqualTo(1);
		assertThat(jdbc.queryForObject("select before_state -> 'body' -> 'content' -> 0 -> 'content' -> 0 ->> 'text'"
				+ " from audit_log where entity_id = ? and action = 'UPDATE'", String.class, cid)).isEqualTo("hi ");
		assertThat(jdbc.queryForObject("select after_state -> 'body' -> 'content' -> 0 -> 'content' -> 0 ->> 'text'"
				+ " from audit_log where entity_id = ? and action = 'UPDATE'", String.class, cid)).isEqualTo("hi again ");
	}

	@Test
	void mentioningAnArchivedUnknownOrMismatchedUserIsRejected() throws Exception {
		UUID task = task();
		Account archived = account();
		jdbc.update("update users set status = 'ARCHIVED', archived_at = now() where id = ?", archived.id());
		Account blocked = account();
		jdbc.update("update users set status = 'BLOCKED' where id = ?", blocked.id());
		Account fine = account();
		UUID unknown = UUID.randomUUID();

		for (UUID bad : new UUID[] { archived.id(), blocked.id(), unknown }) {
			send(admin, "/comments", create(task, doc("hi ", bad), new UUID[] { bad }))
					.andExpect(status().isUnprocessableContent())
					.andExpect(jsonPath("$.code").value("COMMENT_MENTION_INVALID"));
		}
		// ids sent must equal the mention nodes of the body
		send(admin, "/comments", create(task, doc("hi ", fine.id()), new UUID[0]))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("COMMENT_MENTIONS_MISMATCH"));
		send(admin, "/comments", create(task, doc("hi"), new UUID[] { fine.id() }))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("COMMENT_MENTIONS_MISMATCH"));
		assertThat(jdbc.queryForObject("select count(*) from comment where target_id = ?", Long.class, task))
				.isZero();
		assertThat(mentions.all).isEmpty();
	}

	@Test
	void onlyTheAuthorOrAnAdminMayEditAndArchive() throws Exception {
		UUID task = task();
		Account author = account("tasks.view");
		Account other = account("tasks.view", "tasks.edit.any", "tasks.archive");
		UUID cid = comment(author.session(), task, "mine");
		String edit = "{\"body\":" + doc("changed") + ",\"mentionUserIds\":[]}";

		putJson(other.session(), "/comments/" + cid, edit).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("COMMENT_EDIT_FORBIDDEN"));
		send(other.session(), "/comments/" + cid + "/archive", "").andExpect(status().isForbidden());
		putJson(author.session(), "/comments/" + cid, edit).andExpect(status().isOk());
		putJson(admin, "/comments/" + cid, "{\"body\":" + doc("moderated") + ",\"mentionUserIds\":[]}")
				.andExpect(status().isOk());
		assertThat(audit(cid, "UPDATE")).isEqualTo(2);
		putJson(admin, "/comments/" + UUID.randomUUID(), edit).andExpect(status().isNotFound());
	}

	@Test
	void archivingHidesTheCommentKeepsTheRowAndBlocksFurtherEdits() throws Exception {
		UUID task = task();
		Account author = account("tasks.view");
		UUID cid = comment(author.session(), task, "oops");

		send(author.session(), "/comments/" + cid + "/archive", "").andExpect(status().isOk())
				.andExpect(jsonPath("$.archived").value(true));
		read(author.session(), "/comments?targetType=TASK&targetId=" + task).andExpect(jsonPath("$.content", hasSize(0)));
		assertThat(jdbc.queryForObject("select archived_at is not null from comment where id = ?", Boolean.class, cid))
				.isTrue();
		assertThat(audit(cid, "ARCHIVE")).isEqualTo(1);
		putJson(author.session(), "/comments/" + cid, "{\"body\":" + doc("again") + ",\"mentionUserIds\":[]}")
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COMMENT_ARCHIVED"));
		send(author.session(), "/comments/" + cid + "/archive", "").andExpect(status().isConflict());
	}

	@Test
	void targetsAreValidated() throws Exception {
		UUID task = task();
		send(admin, "/comments", create(UUID.randomUUID(), doc("x"), new UUID[0])).andExpect(status().isNotFound());
		send(admin, "/comments", "{\"targetType\":\"RESOLUTION\",\"targetId\":\"" + UUID.randomUUID()
				+ "\",\"body\":" + doc("x") + "}").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("COMMENT_TARGET_UNSUPPORTED"));
		send(admin, "/comments", "{\"targetType\":\"PLANET\",\"targetId\":\"" + task + "\",\"body\":" + doc("x") + "}")
				.andExpect(status().isBadRequest());
		send(admin, "/tasks/" + task + "/archive", "").andExpect(status().isOk());
		send(admin, "/comments", create(task, doc("late"), new UUID[0])).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TASK_ARCHIVED"));
	}

	@Test
	void bodiesAreValidated() throws Exception {
		UUID task = task();
		send(admin, "/comments", create(task, doc(" "), new UUID[0])).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("COMMENT_EMPTY"));
		send(admin, "/comments", create(task, "{\"type\":\"paragraph\"}", new UUID[0]))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("COMMENT_BODY_INVALID"));
	}

	@Test
	void attachmentsMustExistAndAreReturned() throws Exception {
		UUID task = task();
		UUID file = file();
		send(admin, "/comments", create(task, doc("see file"), new UUID[0], UUID.randomUUID()))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("COMMENT_ATTACHMENT_INVALID"));
		UUID cid = id(send(admin, "/comments", create(task, doc("see file"), new UUID[0], file))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.attachments[0].id").value(file.toString()))
				.andExpect(jsonPath("$.attachments[0].fileName").value("plan.pdf")));
		comments.add(cid);
		// an attachment alone is a valid comment
		UUID onlyFile = id(send(admin, "/comments", create(task, "{\"type\":\"doc\"}", new UUID[0], file))
				.andExpect(status().isCreated()));
		comments.add(onlyFile);
	}

}
