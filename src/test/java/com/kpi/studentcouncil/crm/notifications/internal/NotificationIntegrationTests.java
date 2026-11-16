package com.kpi.studentcouncil.crm.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

/**
 * Notifications created by real task, assignee, subtask and comment flows (NT-6, NT-7, spec §8 Q5), the absence of
 * resolution notifications (NT-6), and the inbox API.
 */
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
	private UUID todo;
	private UUID inProgress;
	private UUID task;

	private record Account(UUID id, MockHttpSession session) {
	}

	@BeforeEach
	void setUp() {
		space = UUID.randomUUID();
		jdbc.update("insert into task_space (id, name, is_general, created_at, updated_at) values (?, ?, true, now(),"
				+ " now())", space, "Notif " + space);
		todo = UUID.randomUUID();
		jdbc.update("insert into task_space_status (id, space_id, name, category, position, created_at, updated_at)"
				+ " values (?, ?, 'Todo', 'TODO', 0, now(), now())", todo, space);
		inProgress = UUID.randomUUID();
		jdbc.update("insert into task_space_status (id, space_id, name, category, position, created_at, updated_at)"
				+ " values (?, ?, 'Doing', 'IN_PROGRESS', 1, now(), now())", inProgress, space);
	}

	/** The fixture task, created by {@code creator}. */
	private UUID task(Account creator) {
		task = UUID.randomUUID();
		jdbc.update("insert into task (id, space_id, title, status_id, priority, deadline, created_at, updated_at,"
				+ " created_by, creator_id, issued_at) values (?, ?, 'Fixture', ?, 'NORMAL', ?, now(), now(), ?, ?,"
				+ " now())", task, space, todo, LocalDate.now().plusDays(20), creator.id(), creator.id());
		return task;
	}

	@AfterEach
	void cleanUp() {
		for (UUID user : users) {
			jdbc.update("delete from notification where recipient_id = ?", user);
		}
		String inSpace = "(select id from task where space_id = ?)";
		jdbc.update("delete from comment_mention where comment_id in (select id from comment where target_id in "
				+ inSpace + ")", space);
		jdbc.update("delete from comment where target_id in " + inSpace, space);
		List<UUID> ids = jdbc.queryForList("select id from resolution where task_id in " + inSpace, UUID.class, space);
		for (UUID id : ids) {
			jdbc.update("delete from resolution_history where resolution_id = ?", id);
			jdbc.update("delete from resolution_co_executor where resolution_id = ?", id);
		}
		jdbc.update("update resolution set parent_resolution_id = null where task_id in " + inSpace, space);
		jdbc.update("delete from resolution where task_id in " + inSpace, space);
		jdbc.update("delete from task_assignee where task_id in " + inSpace, space);
		jdbc.update("delete from task_label where task_id in " + inSpace, space);
		jdbc.update("delete from task where space_id = ? and parent_task_id is not null", space);
		jdbc.update("delete from task where space_id = ?", space);
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
		return account("tasks.view", "tasks.create", "tasks.assign", "resolutions.create", "resolutions.redirect");
	}

	private ResultActions send(Account who, String path, String json) throws Exception {
		return mvc.perform(post("/api/v1" + path).with(csrf()).session(who.session())
				.header(HttpHeaders.ACCEPT_LANGUAGE, "en").contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions read(Account who, String path) throws Exception {
		return mvc.perform(get("/api/v1" + path).session(who.session()).header(HttpHeaders.ACCEPT_LANGUAGE, "en"));
	}

	private void assign(Account actor, Account user) throws Exception {
		send(actor, "/tasks/" + task + "/assignees", "{\"userId\":\"" + user.id() + "\"}")
				.andExpect(status().isCreated());
	}

	private void unassign(Account actor, Account user) throws Exception {
		mvc.perform(delete("/api/v1/tasks/" + task + "/assignees/" + user.id()).with(csrf()).session(actor.session()))
				.andExpect(status().isNoContent());
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
	void assigningNotifiesTheAssigneeButNeverTheActor() throws Exception {
		Account creator = member();
		Account assignee = member();
		Account bystander = member();
		task(creator);
		assign(creator, assignee);
		assign(creator, creator); // self-assignment

		assertThat(types(assignee)).containsExactly("TASK_ASSIGNED");
		assertThat(count(creator)).isZero();
		assertThat(count(bystander)).isZero();
		assertThat(jdbc.queryForObject("select target_type || ':' || target_id from notification where recipient_id"
				+ " = ?", String.class, assignee.id())).isEqualTo("TASK:" + task);
		assertThat(jdbc.queryForObject("select payload->>'actorId' from notification where recipient_id = ?",
				String.class, assignee.id())).isEqualTo(creator.id().toString());
	}

	@Test
	void unassigningNotifiesTheRemovedAssigneeButNotWhenTheyLeaveThemselves() throws Exception {
		Account creator = member();
		Account assignee = member();
		Account leaver = member();
		task(creator);
		assign(creator, assignee);
		assign(creator, leaver);
		unassign(creator, assignee);
		unassign(leaver, leaver);

		assertThat(types(assignee)).containsExactly("TASK_ASSIGNED", "TASK_UNASSIGNED");
		assertThat(types(leaver)).containsExactly("TASK_ASSIGNED");
		assertThat(count(creator)).isZero();
	}

	@Test
	void statusChangeByAnAssigneeNotifiesTheCreatorAndOtherAssigneesOnly() throws Exception {
		Account creator = member();
		Account actor = member();
		Account other = member();
		Account bystander = member();
		task(creator);
		assign(creator, actor);
		assign(creator, other);

		send(actor, "/tasks/" + task + "/status", "{\"statusId\":\"" + inProgress + "\"}").andExpect(status().isOk());
		// moving to the current status is a no-op: nothing is published
		send(actor, "/tasks/" + task + "/status", "{\"statusId\":\"" + inProgress + "\"}").andExpect(status().isOk());

		assertThat(types(creator)).containsExactly("TASK_STATUS_CHANGED");
		assertThat(types(other)).containsExactly("TASK_ASSIGNED", "TASK_STATUS_CHANGED");
		assertThat(types(actor)).containsExactly("TASK_ASSIGNED");
		assertThat(count(bystander)).isZero();
		assertThat(jdbc.queryForObject("select payload->>'statusId' from notification where recipient_id = ? and"
				+ " type = 'TASK_STATUS_CHANGED'", String.class, creator.id())).isEqualTo(inProgress.toString());
	}

	@Test
	void deadlineChangeNotifiesTheAssigneesButNotTheCreatorWhoMadeIt() throws Exception {
		Account creator = member();
		Account assignee = member();
		task(creator);
		assign(creator, assignee);
		LocalDate moved = LocalDate.now().plusDays(30);

		mvc.perform(put("/api/v1/tasks/" + task).with(csrf()).session(creator.session())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Fixture\",\"deadline\":\"" + moved + "\"}")).andExpect(status().isOk());

		assertThat(types(assignee)).containsExactly("TASK_ASSIGNED", "TASK_DEADLINE_CHANGED");
		assertThat(count(creator)).isZero();
		assertThat(jdbc.queryForObject("select payload->>'deadline' from notification where recipient_id = ? and"
				+ " type = 'TASK_DEADLINE_CHANGED'", String.class, assignee.id())).isEqualTo(moved.toString());
	}

	@Test
	void subtaskAddedNotifiesTheParentsCreatorAndAssigneesButNotTheActor() throws Exception {
		Account creator = member();
		Account actor = member();
		Account other = member();
		Account helper = member();
		task(creator);
		assign(creator, actor);
		assign(creator, other);

		String body = send(actor, "/tasks/" + task + "/subtasks", "{\"title\":\"Sub\",\"assigneeIds\":[\""
				+ helper.id() + "\"]}").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		UUID subtask = UUID.fromString(JsonPath.read(body, "$.id"));

		assertThat(types(creator)).containsExactly("SUBTASK_ADDED");
		assertThat(types(other)).containsExactly("TASK_ASSIGNED", "SUBTASK_ADDED");
		assertThat(types(actor)).containsExactly("TASK_ASSIGNED");
		// the subtask's own assignee is told about the assignment
		assertThat(types(helper)).containsExactly("TASK_ASSIGNED");
		assertThat(jdbc.queryForObject("select target_id from notification where recipient_id = ?", UUID.class,
				creator.id())).isEqualTo(subtask);
		assertThat(jdbc.queryForObject("select payload->>'taskId' from notification where recipient_id = ?",
				String.class, creator.id())).isEqualTo(task.toString());
	}

	@Test
	void resolutionFlowsNoLongerEmitResolutionNotifications() throws Exception {
		Account author = member();
		Account executor = member();
		task(author);
		send(author, "/tasks/" + task + "/resolutions", "{\"executorId\":\"" + executor.id()
				+ "\",\"coExecutorIds\":[],\"instruction\":\"Do it\",\"dueDate\":\"" + LocalDate.now().plusDays(7)
				+ "\"}").andExpect(status().isCreated());

		assertThat(count(executor)).isZero();
		assertThat(count(author)).isZero();
	}

	@Test
	void storedResolutionNotificationsStillRender() throws Exception {
		Account user = member();
		jdbc.update("insert into notification (id, recipient_id, type, target_type, target_id, payload, created_at,"
				+ " updated_at) values (?, ?, 'RESOLUTION_ISSUED', 'RESOLUTION', ?, '{}'::jsonb, now(), now())",
				UUID.randomUUID(), user.id(), UUID.randomUUID());

		read(user, "/notifications").andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].type").value("RESOLUTION_ISSUED"))
				.andExpect(jsonPath("$.content[0].text").value("A resolution was issued to you"));
	}

	@Test
	void mentionInTaskCommentNotifiesTheMentionedUserOnly() throws Exception {
		Account writer = member();
		Account mentioned = member();
		Account other = member();
		task(writer);
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
		Account creator = member();
		Account assignee = member();
		Account other = member();
		task(creator);
		assign(creator, assignee);
		send(creator, "/tasks/" + task + "/status", "{\"statusId\":\"" + inProgress + "\"}")
				.andExpect(status().isOk());
		assign(creator, other);

		read(assignee, "/notifications").andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(2)))
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content[0].type").value("TASK_STATUS_CHANGED"))
				.andExpect(jsonPath("$.content[0].read").value(false))
				.andExpect(jsonPath("$.content[0].text").value("The status of a task was changed"))
				.andExpect(jsonPath("$.content[1].text").value("You were assigned to a task"));
		read(assignee, "/notifications?size=1").andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.totalPages").value(2));
		read(assignee, "/notifications/unread-count").andExpect(jsonPath("$.count").value(2));
		read(creator, "/notifications/unread-count").andExpect(jsonPath("$.count").value(0));

		UUID first = jdbc.queryForObject("select id from notification where recipient_id = ? order by created_at"
				+ " limit 1", UUID.class, assignee.id());
		send(assignee, "/notifications/" + first + "/read", "").andExpect(status().isOk())
				.andExpect(jsonPath("$.read").value(true));
		// idempotent
		send(assignee, "/notifications/" + first + "/read", "").andExpect(status().isOk());
		read(assignee, "/notifications/unread-count").andExpect(jsonPath("$.count").value(1));
		read(assignee, "/notifications?unread=true").andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].read").value(false));
		read(assignee, "/notifications").andExpect(jsonPath("$.content", hasSize(2)));

		send(assignee, "/notifications/read-all", "").andExpect(status().isOk()).andExpect(jsonPath("$.updated")
				.value(1));
		read(assignee, "/notifications/unread-count").andExpect(jsonPath("$.count").value(0));
		send(assignee, "/notifications/read-all", "").andExpect(jsonPath("$.updated").value(0));
		// another user's inbox is untouched
		read(other, "/notifications/unread-count").andExpect(jsonPath("$.count").value(1));
		assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_type = 'Notification' and"
				+ " actor_id = ?", Long.class, assignee.id())).isEqualTo(2);
	}

	@Test
	void textsAreRenderedPerLocaleWhilePayloadStaysLocaleIndependent() throws Exception {
		Account creator = member();
		Account assignee = member();
		task(creator);
		assign(creator, assignee);
		// the saved user locale wins over Accept-Language
		jdbc.update("update users set locale = 'uk' where id = ?", assignee.id());
		mvc.perform(get("/api/v1/notifications").session(assignee.session()).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].text").value("Вас призначено виконавцем задачі"));
		String stored = jdbc.queryForObject("select payload::text from notification where recipient_id = ?",
				String.class, assignee.id());
		assertThat(stored).contains("taskId").doesNotContain("задачі").doesNotContain("assigned to");
	}

	@Test
	void cannotReadOrMarkSomeoneElsesNotification() throws Exception {
		Account creator = member();
		Account assignee = member();
		Account intruder = member();
		task(creator);
		assign(creator, assignee);
		UUID id = jdbc.queryForObject("select id from notification where recipient_id = ?", UUID.class,
				assignee.id());

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
