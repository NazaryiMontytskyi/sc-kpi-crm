package com.kpi.studentcouncil.crm.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** Notifications created by real resolution and comment flows, and the inbox API. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class NotificationIntegrationTests {

	private static final String PASSWORD = "Sup3r-secret-pw";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PasswordEncoder encoder;

	@Autowired
	AccessService access;

	private final List<UUID> users = new ArrayList<>();
	private final List<UUID> roles = new ArrayList<>();
	private UUID space;
	private UUID task;

	private record Account(UUID id, MockHttpSession session) {
	}

	@BeforeEach
	void setUp() {
		space = UUID.randomUUID();
		jdbc.update("insert into task_space (id, name, is_general, created_at, updated_at) values (?, ?, true, now(),"
				+ " now())", space, "Notif " + space);
		UUID statusId = UUID.randomUUID();
		jdbc.update("insert into task_space_status (id, space_id, name, category, position, created_at, updated_at)"
				+ " values (?, ?, 'Todo', 'TODO', 0, now(), now())", statusId, space);
		task = UUID.randomUUID();
		jdbc.update("insert into task (id, space_id, title, status_id, priority, created_at, updated_at) values (?, ?,"
				+ " 'Fixture', ?, 'NORMAL', now(), now())", task, space, statusId);
	}

	@AfterEach
	void cleanUp() {
		for (UUID user : users) {
			jdbc.update("delete from notification where recipient_id = ?", user);
		}
		jdbc.update("delete from comment_mention where comment_id in (select id from comment where target_id = ?)",
				task);
		jdbc.update("delete from comment where target_id = ?", task);
		List<UUID> ids = jdbc.queryForList("select id from resolution where task_id = ?", UUID.class, task);
		for (UUID id : ids) {
			jdbc.update("delete from resolution_history where resolution_id = ?", id);
			jdbc.update("delete from resolution_co_executor where resolution_id = ?", id);
		}
		jdbc.update("update resolution set parent_resolution_id = null where task_id = ?", task);
		jdbc.update("delete from resolution where task_id = ?", task);
		jdbc.update("delete from task where id = ?", task);
		jdbc.update("delete from task_space_status where space_id = ?", space);
		jdbc.update("delete from task_space where id = ?", space);
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
		users.clear();
		roles.clear();
	}

	private Account account(String... permissionKeys) throws Exception {
		UUID user = UUID.randomUUID();
		users.add(user);
		String login = "notif-" + user.toString().substring(0, 8);
		jdbc.update("insert into users (id, login, password_hash, status, locale, must_change_password, created_at,"
				+ " updated_at) values (?, ?, ?, 'ACTIVE', 'en', false, now(), now())", user, login,
				encoder.encode(PASSWORD));
		UUID role = UUID.randomUUID();
		roles.add(role);
		jdbc.update("insert into role (id, name, is_system, created_at, updated_at) values (?, ?, false, now(),"
				+ " now())", role, "notif-role-" + role);
		for (String key : permissionKeys) {
			jdbc.update("insert into role_permission (role_id, permission_key) values (?, ?)", role, key);
		}
		access.grantRole(user, role);
		MvcResult result = mvc.perform(post("/api/v1/auth/login").with(csrf()).with(request -> {
			request.setRemoteAddr("10.9." + (int) (Math.random() * 250) + "." + (1 + (int) (Math.random() * 250)));
			return request;
		}).contentType(MediaType.APPLICATION_JSON)
				.content("{\"login\":\"" + login + "\",\"password\":\"" + PASSWORD + "\"}")).andReturn();
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		return new Account(user, (MockHttpSession) result.getRequest().getSession(false));
	}

	private Account member() throws Exception {
		return account("tasks.view", "resolutions.create", "resolutions.redirect");
	}

	private ResultActions send(Account who, String path, String json) throws Exception {
		return mvc.perform(post("/api/v1" + path).with(csrf()).session(who.session())
				.header(HttpHeaders.ACCEPT_LANGUAGE, "en").contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions read(Account who, String path) throws Exception {
		return mvc.perform(get("/api/v1" + path).session(who.session()).header(HttpHeaders.ACCEPT_LANGUAGE, "en"));
	}

	private UUID issue(Account author, UUID executor, UUID... co) throws Exception {
		StringBuilder coJson = new StringBuilder();
		for (UUID c : co) {
			coJson.append(coJson.length() == 0 ? "" : ",").append('"').append(c).append('"');
		}
		ResultActions r = send(author, "/tasks/" + task + "/resolutions",
				"{\"executorId\":\"" + executor + "\",\"coExecutorIds\":[" + coJson
						+ "],\"instruction\":\"Do it\",\"dueDate\":\"" + LocalDate.now().plusDays(7) + "\"}")
				.andExpect(status().isCreated());
		return UUID.fromString(JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id"));
	}

	private void act(Account who, UUID resolution, String action, String json) throws Exception {
		send(who, "/resolutions/" + resolution + "/" + action, json).andExpect(status().isOk());
	}

	/** Types of all notifications of the user, oldest first. */
	private List<String> types(Account who) {
		return jdbc.queryForList(
				"select type from notification where recipient_id = ? order by created_at, id", String.class,
				who.id());
	}

	private long count(Account who) {
		return jdbc.queryForObject("select count(*) from notification where recipient_id = ?", Long.class, who.id());
	}

	@Test
	void issuedNotifiesExecutorAndCoExecutorButNotTheAuthor() throws Exception {
		Account author = member();
		Account executor = member();
		Account co = member();
		Account bystander = member();
		UUID resolution = issue(author, executor.id(), co.id());

		assertThat(types(executor)).containsExactly("RESOLUTION_ISSUED");
		assertThat(types(co)).containsExactly("RESOLUTION_ISSUED");
		assertThat(count(author)).isZero();
		assertThat(count(bystander)).isZero();
		assertThat(jdbc.queryForObject("select target_id from notification where recipient_id = ?", UUID.class,
				executor.id())).isEqualTo(resolution);
		assertThat(jdbc.queryForObject("select target_type from notification where recipient_id = ?", String.class,
				executor.id())).isEqualTo("RESOLUTION");
	}

	@Test
	void lifecycleNotifiesTheRightPeopleOnly() throws Exception {
		Account author = member();
		Account executor = member();
		Account co = member();
		UUID resolution = issue(author, executor.id(), co.id());

		act(executor, resolution, "start", "");
		assertThat(count(author)).isZero();

		act(executor, resolution, "submit", "{\"completionNote\":\"Done\"}");
		assertThat(types(author)).containsExactly("RESOLUTION_SUBMITTED");
		assertThat(types(executor)).containsExactly("RESOLUTION_ISSUED");

		act(author, resolution, "rework", "{\"comment\":\"Add numbers\"}");
		assertThat(types(executor)).containsExactly("RESOLUTION_ISSUED", "RESOLUTION_REWORK");
		assertThat(types(co)).containsExactly("RESOLUTION_ISSUED", "RESOLUTION_REWORK");
		assertThat(types(author)).containsExactly("RESOLUTION_SUBMITTED");

		act(executor, resolution, "resume", "");
		act(executor, resolution, "submit", "{\"completionNote\":\"Now with numbers\"}");
		act(author, resolution, "accept", "");
		assertThat(types(executor)).containsExactly("RESOLUTION_ISSUED", "RESOLUTION_REWORK", "RESOLUTION_ACCEPTED");
		assertThat(types(co)).containsExactly("RESOLUTION_ISSUED", "RESOLUTION_REWORK", "RESOLUTION_ACCEPTED");
		assertThat(types(author)).containsExactly("RESOLUTION_SUBMITTED", "RESOLUTION_SUBMITTED");
	}

	@Test
	void redirectNotifiesAuthorAndNewExecutorAndChildAcceptanceNotifiesParentExecutor() throws Exception {
		Account author = member();
		Account executor = member();
		Account tail = member();
		UUID root = issue(author, executor.id());
		act(executor, root, "start", "");

		ResultActions redirected = send(executor, "/resolutions/" + root + "/redirect", "{\"executorId\":\""
				+ tail.id() + "\",\"instruction\":\"Take over\",\"dueDate\":\"" + LocalDate.now().plusDays(3) + "\"}")
				.andExpect(status().isCreated());
		UUID child = UUID.fromString(JsonPath.read(redirected.andReturn().getResponse().getContentAsString(),
				"$.child.id"));

		assertThat(types(author)).containsExactly("RESOLUTION_REDIRECTED");
		assertThat(types(tail)).containsExactly("RESOLUTION_ISSUED");
		assertThat(types(executor)).containsExactly("RESOLUTION_ISSUED");

		act(tail, child, "start", "");
		act(tail, child, "submit", "{\"completionNote\":\"Done\"}");
		assertThat(types(executor)).containsExactly("RESOLUTION_ISSUED", "RESOLUTION_SUBMITTED");
		act(executor, child, "accept", "");
		assertThat(types(tail)).containsExactly("RESOLUTION_ISSUED", "RESOLUTION_ACCEPTED");
		assertThat(types(executor)).containsExactly("RESOLUTION_ISSUED", "RESOLUTION_SUBMITTED",
				"RESOLUTION_CHILD_ACCEPTED");
		assertThat(jdbc.queryForObject("select target_id from notification where recipient_id = ? and type ="
				+ " 'RESOLUTION_CHILD_ACCEPTED'", UUID.class, executor.id())).isEqualTo(root);
		assertThat(types(author)).containsExactly("RESOLUTION_REDIRECTED");
	}

	@Test
	void mentionInTaskCommentNotifiesTheMentionedUserOnly() throws Exception {
		Account writer = member();
		Account mentioned = member();
		Account other = member();
		String body = "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\","
				+ "\"text\":\"Hi \"},{\"type\":\"mention\",\"attrs\":{\"id\":\"" + mentioned.id()
				+ "\",\"label\":\"Fake\"}},{\"type\":\"mention\",\"attrs\":{\"id\":\"" + writer.id()
				+ "\",\"label\":\"Self\"}}]}]}";
		send(writer, "/comments", "{\"targetType\":\"TASK\",\"targetId\":\"" + task + "\",\"body\":" + body
				+ ",\"mentionUserIds\":[\"" + mentioned.id() + "\",\"" + writer.id() + "\"],\"attachmentIds\":[]}")
				.andExpect(status().isCreated());

		assertThat(types(mentioned)).containsExactly("MENTION");
		assertThat(count(writer)).isZero();
		assertThat(count(other)).isZero();
		assertThat(jdbc.queryForObject("select target_id from notification where recipient_id = ?", UUID.class,
				mentioned.id())).isEqualTo(task);
	}

	@Test
	void inboxListsOnlyOwnNotificationsAndSupportsUnreadFilterAndCount() throws Exception {
		Account author = member();
		Account executor = member();
		Account other = member();
		issue(author, executor.id());
		issue(author, executor.id());
		issue(author, other.id());

		read(executor, "/notifications").andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(2)))
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content[0].type").value("RESOLUTION_ISSUED"))
				.andExpect(jsonPath("$.content[0].read").value(false))
				.andExpect(jsonPath("$.content[0].text").value("A resolution was issued to you"));
		read(executor, "/notifications?size=1").andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.totalPages").value(2));
		read(executor, "/notifications/unread-count").andExpect(jsonPath("$.count").value(2));
		read(author, "/notifications/unread-count").andExpect(jsonPath("$.count").value(0));

		UUID first = jdbc.queryForObject("select id from notification where recipient_id = ? order by created_at"
				+ " limit 1", UUID.class, executor.id());
		send(executor, "/notifications/" + first + "/read", "").andExpect(status().isOk())
				.andExpect(jsonPath("$.read").value(true));
		// idempotent
		send(executor, "/notifications/" + first + "/read", "").andExpect(status().isOk());
		read(executor, "/notifications/unread-count").andExpect(jsonPath("$.count").value(1));
		read(executor, "/notifications?unread=true").andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].read").value(false));
		read(executor, "/notifications").andExpect(jsonPath("$.content", hasSize(2)));

		send(executor, "/notifications/read-all", "").andExpect(status().isOk()).andExpect(jsonPath("$.updated")
				.value(1));
		read(executor, "/notifications/unread-count").andExpect(jsonPath("$.count").value(0));
		send(executor, "/notifications/read-all", "").andExpect(jsonPath("$.updated").value(0));
		// another user's inbox is untouched
		read(other, "/notifications/unread-count").andExpect(jsonPath("$.count").value(1));
		assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_type = 'Notification' and"
				+ " actor_id = ?", Long.class, executor.id())).isEqualTo(2);
	}

	@Test
	void textsAreRenderedPerLocaleWhilePayloadStaysLocaleIndependent() throws Exception {
		Account author = member();
		Account executor = member();
		issue(author, executor.id());
		// the saved user locale wins over Accept-Language
		jdbc.update("update users set locale = 'uk' where id = ?", executor.id());
		mvc.perform(get("/api/v1/notifications").session(executor.session()).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content[0].text").value("Вам видано резолюцію"));
		String stored = jdbc.queryForObject("select payload::text from notification where recipient_id = ?",
				String.class, executor.id());
		assertThat(stored).contains("taskId").doesNotContain("резолюцію").doesNotContain("A resolution");
	}

	@Test
	void cannotReadOrMarkSomeoneElsesNotification() throws Exception {
		Account author = member();
		Account executor = member();
		Account intruder = member();
		issue(author, executor.id());
		UUID id = jdbc.queryForObject("select id from notification where recipient_id = ?", UUID.class,
				executor.id());

		send(intruder, "/notifications/" + id + "/read", "").andExpect(status().isNotFound());
		send(intruder, "/notifications/" + UUID.randomUUID() + "/read", "").andExpect(status().isNotFound());
		read(intruder, "/notifications").andExpect(jsonPath("$.content", hasSize(0)));
		assertThat(jdbc.queryForObject("select read_at from notification where id = ?", java.sql.Timestamp.class, id))
				.isNull();
	}

	@Test
	void endpointsRequireAuthentication() throws Exception {
		mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/notifications/unread-count")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/notifications/read-all").with(csrf())).andExpect(status().isUnauthorized());
	}

}
