package com.kpi.studentcouncil.crm.tasks.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import com.kpi.studentcouncil.crm.tasks.TaskEvent;
import com.kpi.studentcouncil.crmtest.SharedAdmin;

/**
 * Subtasks API (INC-074): ST-1..ST-5 and TM-4 (spec §9.5, §9.6) with audit, domain events and the 403 cases. Shares
 * the application context of {@link TaskIntegrationTests} (same imports) to reuse its event collector.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, TaskIntegrationTests.EventsConfig.class })
class TaskSubtaskIntegrationTests {

	private static final String PASSWORD = "Sup3r-secret-pw";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PasswordEncoder encoder;

	@Autowired
	AccessService access;

	@Autowired
	TaskIntegrationTests.CollectedEvents events;

	MockHttpSession admin;

	private final List<UUID> spaces = new ArrayList<>();
	private final List<UUID> users = new ArrayList<>();
	private final List<UUID> roles = new ArrayList<>();

	private record Account(UUID id, MockHttpSession session) {
	}

	@BeforeEach
	void setUp() throws Exception {
		SharedAdmin.ensure(jdbc, encoder);
		admin = signIn(SharedAdmin.LOGIN, SharedAdmin.PASSWORD);
		events.all.clear();
	}

	@AfterEach
	void cleanUp() {
		for (UUID space : spaces) {
			jdbc.update("delete from task_assignee where task_id in (select id from task where space_id = ?)", space);
			jdbc.update("delete from task_label where task_id in (select id from task where space_id = ?)", space);
			jdbc.update("delete from task where space_id = ?", space);
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
		spaces.clear();
		users.clear();
		roles.clear();
	}

	// ---- helpers ----

	private MockHttpSession signIn(String login, String password) throws Exception {
		MvcResult result = mvc.perform(post("/api/v1/auth/login").with(csrf()).with(request -> {
			request.setRemoteAddr("10.8." + (int) (Math.random() * 250) + "." + (1 + (int) (Math.random() * 250)));
			return request;
		}).contentType(MediaType.APPLICATION_JSON)
				.content("{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}")).andReturn();
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private UUID user() {
		UUID user = UUID.randomUUID();
		users.add(user);
		jdbc.update("insert into users (id, login, password_hash, status, locale, must_change_password, created_at,"
				+ " updated_at) values (?, ?, ?, 'ACTIVE', 'en', false, now(), now())", user,
				"subtask-" + user.toString().substring(0, 8), encoder.encode(PASSWORD));
		return user;
	}

	/** An active user holding a role with the given permission keys, signed in. */
	private Account account(String... permissionKeys) throws Exception {
		UUID user = user();
		if (permissionKeys.length > 0) {
			UUID role = UUID.randomUUID();
			roles.add(role);
			jdbc.update("insert into role (id, name, is_system, created_at, updated_at) values (?, ?, false, now(),"
					+ " now())", role, "subtask-role-" + role);
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

	private UUID space() throws Exception {
		UUID id = UUID.fromString(JsonPath.read(send(admin, "/spaces", "{\"name\":\"Subtasks " + UUID.randomUUID()
				+ "\",\"general\":true}").andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString(), "$.id"));
		spaces.add(id);
		return id;
	}

	private UUID statusId(UUID space, String category) throws Exception {
		List<String> ids = JsonPath.read(read(admin, "/spaces/" + space + "/statuses").andReturn().getResponse()
				.getContentAsString(), "$[?(@.category=='" + category + "')].id");
		return UUID.fromString(ids.get(0));
	}

	private UUID createTask(MockHttpSession s, UUID space, String title, String extra) throws Exception {
		return UUID.fromString(JsonPath.read(send(s, "/tasks", "{\"spaceId\":\"" + space + "\",\"title\":\"" + title
				+ "\"" + extra + "}").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
				"$.id"));
	}

	private ResultActions addSubtask(MockHttpSession s, UUID parent, String body) throws Exception {
		return send(s, "/tasks/" + parent + "/subtasks", body);
	}

	private UUID subtask(MockHttpSession s, UUID parent, String title, String extra) throws Exception {
		return UUID.fromString(JsonPath.read(addSubtask(s, parent, "{\"title\":\"" + title + "\"" + extra + "}")
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id"));
	}

	private void moveTo(MockHttpSession s, UUID task, UUID statusId) throws Exception {
		send(s, "/tasks/" + task + "/status", "{\"statusId\":\"" + statusId + "\"}").andExpect(status().isOk());
	}

	private long audits(UUID task, String action) {
		return jdbc.queryForObject("select count(*) from audit_log where entity_type = 'Task' and entity_id = ? and"
				+ " action = ?", Long.class, task, action);
	}

	private String deadlineOf(UUID task) {
		return jdbc.queryForObject("select deadline::text from task where id = ?", String.class, task);
	}

	private boolean archived(UUID task) {
		return Boolean.TRUE.equals(jdbc.queryForObject("select archived_at is not null from task where id = ?",
				Boolean.class, task));
	}

	// ---- ST-2, spec §9.5: who may add a subtask, for whom ----

	@Test
	void tasksCreateAddsASubtaskForSelfAndAnotherUserNeedsTasksAssign() throws Exception {
		UUID space = space();
		Account owner = account("tasks.view", "tasks.create");
		Account creator = account("tasks.view", "tasks.create");
		Account assigner = account("tasks.view", "tasks.create", "tasks.assign");
		Account viewer = account("tasks.view");
		Account nobody = account("tasks.create");
		UUID colleague = user();
		UUID parent = createTask(owner.session(), space, "Organise the fair", "");
		events.all.clear();

		// for myself (the default): the subtask inherits the space, I am its creator and its assignee
		addSubtask(creator.session(), parent, "{\"title\":\"Book the hall\"}").andExpect(status().isCreated())
				.andExpect(jsonPath("$.parentId").value(parent.toString()))
				.andExpect(jsonPath("$.spaceId").value(space.toString()))
				.andExpect(jsonPath("$.creatorId").value(creator.id().toString()))
				.andExpect(jsonPath("$.assignees.length()").value(1))
				.andExpect(jsonPath("$.assignees[0].userId").value(creator.id().toString()))
				.andExpect(jsonPath("$.assignees[0].assignedBy").value(creator.id().toString()));
		// explicitly for myself
		addSubtask(creator.session(), parent, "{\"title\":\"Order chairs\",\"assigneeIds\":[\"" + creator.id() + "\"]}")
				.andExpect(status().isCreated());
		// for another user without tasks.assign: 403, nothing created
		addSubtask(creator.session(), parent, "{\"title\":\"Print posters\",\"assigneeIds\":[\"" + colleague + "\"]}")
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("TASK_ASSIGN_FORBIDDEN"));
		addSubtask(creator.session(), parent,
				"{\"title\":\"Print posters\",\"assigneeIds\":[\"" + creator.id() + "\",\"" + colleague + "\"]}")
				.andExpect(status().isForbidden());
		// with tasks.assign: anybody
		addSubtask(assigner.session(), parent, "{\"title\":\"Print posters\",\"assigneeIds\":[\"" + colleague + "\"]}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.assignees[0].userId").value(colleague.toString()))
				.andExpect(jsonPath("$.assignees[0].assignedBy").value(assigner.id().toString()));
		// Admin: anybody
		addSubtask(admin, parent, "{\"title\":\"Call sponsors\",\"assigneeIds\":[\"" + colleague + "\"]}")
				.andExpect(status().isCreated());
		// coarse gates: tasks.create and tasks.view
		addSubtask(viewer.session(), parent, "{\"title\":\"Nope\"}").andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		addSubtask(nobody.session(), parent, "{\"title\":\"Nope\"}").andExpect(status().isForbidden());
		read(nobody.session(), "/tasks/" + parent + "/subtasks").andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/tasks/" + parent + "/subtasks")).andExpect(status().isUnauthorized());
		// unknown parent, validation
		addSubtask(admin, UUID.randomUUID(), "{\"title\":\"Nope\"}").andExpect(status().isNotFound());
		addSubtask(admin, parent, "{\"title\":\" \"}").andExpect(status().isBadRequest());

		assertThat(jdbc.queryForObject("select count(*) from task where parent_task_id = ?", Long.class, parent))
				.isEqualTo(4);
		// an empty list creates it unassigned
		UUID unassigned = subtask(creator.session(), parent, "Unassigned", ",\"assigneeIds\":[]");
		read(viewer.session(), "/tasks/" + unassigned).andExpect(jsonPath("$.assignees.length()").value(0));
	}

	@Test
	void creatingASubtaskIsAuditedAndPublishesCreatedAssignedAndSubtaskAdded() throws Exception {
		UUID space = space();
		Account creator = account("tasks.view", "tasks.create");
		UUID parent = createTask(admin, space, "Parent", "");
		events.all.clear();

		UUID sub = subtask(creator.session(), parent, "Child", "");

		assertThat(audits(sub, "CREATE")).isEqualTo(1);
		assertThat(jdbc.queryForObject("select after_state ->> 'parentId' from audit_log where entity_id = ? and"
				+ " action = 'CREATE'", String.class, sub)).isEqualTo(parent.toString());
		assertThat(audits(sub, "UPDATE")).isEqualTo(1); // the assignment, with before/after
		assertThat(jdbc.queryForObject("select after_state ->> 'assigneeIds' from audit_log where entity_id = ? and"
				+ " action = 'UPDATE'", String.class, sub)).contains(creator.id().toString());
		assertThat(events.all.stream().filter(e -> e.id().equals(sub)).map(TaskEvent::kind))
				.containsExactly(TaskEvent.Kind.CREATED, TaskEvent.Kind.ASSIGNED);
		assertThat(events.all).anySatisfy(e -> {
			assertThat(e.kind()).isEqualTo(TaskEvent.Kind.SUBTASK_ADDED);
			assertThat(e.id()).isEqualTo(parent);
			assertThat(e.subtaskId()).isEqualTo(sub);
			assertThat(e.actorId()).isEqualTo(creator.id());
		});
		// the parent row itself is not changed
		assertThat(audits(parent, "UPDATE")).isZero();
	}

	// ---- ST-1 ----

	@Test
	void aSubtaskOfASubtaskIsRejectedWithAProblem() throws Exception {
		UUID space = space();
		UUID parent = createTask(admin, space, "Parent", "");
		UUID sub = subtask(admin, parent, "Child", "");

		Account english = account("tasks.view", "tasks.create");
		addSubtask(english.session(), sub, "{\"title\":\"Grandchild\"}").andExpect(status().isUnprocessableContent())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("SUBTASK_DEPTH_EXCEEDED"))
				.andExpect(jsonPath("$.title").value("A subtask cannot have subtasks"));
		// the shared admin account uses Ukrainian
		addSubtask(admin, sub, "{\"title\":\"Grandchild\"}").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.title").value("Підзадача не може мати підзадач"));
		assertThat(jdbc.queryForObject("select count(*) from task where parent_task_id = ?", Long.class, sub)).isZero();

		// archived parent
		send(admin, "/tasks/" + parent + "/archive", "").andExpect(status().isOk());
		addSubtask(admin, parent, "{\"title\":\"Late\"}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TASK_ARCHIVED"));
	}

	// ---- ST-3 ----

	@Test
	void aSubtaskDeadlineLaterThanTheParentsIsRejectedAndAParentWithoutDeadlineHasNoLimit() throws Exception {
		UUID space = space();
		UUID parent = createTask(admin, space, "Parent", ",\"deadline\":\"2031-05-20\"");
		UUID open = createTask(admin, space, "Open-ended", "");

		addSubtask(admin, parent, "{\"title\":\"Too late\",\"deadline\":\"2031-05-21\"}")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("SUBTASK_DEADLINE_AFTER_PARENT"))
				.andExpect(jsonPath("$.detail").value(containsString("2031-05-20")));
		UUID sub = subtask(admin, parent, "On time", ",\"deadline\":\"2031-05-20\"");
		subtask(admin, parent, "No deadline", "");
		subtask(admin, open, "Far away", ",\"deadline\":\"2040-01-01\"");

		// editing the subtask deadline past the parent's is rejected too
		putJson(admin, "/tasks/" + sub, "{\"title\":\"On time\",\"deadline\":\"2031-06-01\"}")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("SUBTASK_DEADLINE_AFTER_PARENT"));
		assertThat(deadlineOf(sub)).isEqualTo("2031-05-20");
		putJson(admin, "/tasks/" + sub, "{\"title\":\"On time\",\"deadline\":\"2031-05-01\"}")
				.andExpect(status().isOk());
	}

	@Test
	void movingTheParentDeadlineBeforeSubtaskDeadlinesIsRejectedNamingThemAndChangesNothing() throws Exception {
		UUID space = space();
		UUID parent = createTask(admin, space, "Parent", ",\"deadline\":\"2031-05-31\"");
		UUID posters = subtask(admin, parent, "Print posters", ",\"deadline\":\"2031-05-25\"");
		UUID hall = subtask(admin, parent, "Book the hall", ",\"deadline\":\"2031-05-20\"");
		subtask(admin, parent, "Early bird", ",\"deadline\":\"2031-05-01\"");
		UUID gone = subtask(admin, parent, "Archived one", ",\"deadline\":\"2031-05-30\"");
		send(admin, "/tasks/" + gone + "/archive", "").andExpect(status().isOk());
		long version = ((Number) JsonPath.read(read(admin, "/tasks/" + parent).andReturn().getResponse()
				.getContentAsString(), "$.version")).longValue();
		long updates = audits(parent, "UPDATE");
		events.all.clear();

		putJson(admin, "/tasks/" + parent, "{\"title\":\"Parent\",\"deadline\":\"2031-05-10\"}")
				.andExpect(status().isUnprocessableContent())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("TASK_DEADLINE_BEFORE_SUBTASKS"))
				.andExpect(jsonPath("$.type").value("urn:crm:problem:task-deadline-before-subtasks"))
				.andExpect(jsonPath("$.status").value(422))
				.andExpect(jsonPath("$.detail").value(containsString("Print posters")))
				.andExpect(jsonPath("$.detail").value(containsString("Book the hall")))
				.andExpect(jsonPath("$.conflictingSubtasks.length()").value(2))
				.andExpect(jsonPath("$.conflictingSubtasks[0].id").value(posters.toString()))
				.andExpect(jsonPath("$.conflictingSubtasks[0].deadline").value("2031-05-25"))
				.andExpect(jsonPath("$.conflictingSubtasks[1].id").value(hall.toString()))
				.andExpect(jsonPath("$.conflictingSubtasks[1].title").value("Book the hall"));

		// nothing changed
		assertThat(deadlineOf(parent)).isEqualTo("2031-05-31");
		read(admin, "/tasks/" + parent).andExpect(jsonPath("$.version").value(version));
		assertThat(audits(parent, "UPDATE")).isEqualTo(updates);
		assertThat(events.all).isEmpty();

		// in Ukrainian too
		mvc.perform(put("/api/v1/tasks/" + parent).with(csrf()).session(admin)
				.header(HttpHeaders.ACCEPT_LANGUAGE, "uk").contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Parent\",\"deadline\":\"2031-05-10\"}"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.title").value("Дедлайн раніший за дедлайни підзадач"));

		// a move that leaves every subtask within the bound is fine; removing the deadline too
		putJson(admin, "/tasks/" + parent, "{\"title\":\"Parent\",\"deadline\":\"2031-05-25\"}")
				.andExpect(status().isOk());
		putJson(admin, "/tasks/" + parent, "{\"title\":\"Parent\"}").andExpect(status().isOk());
		assertThat(deadlineOf(parent)).isNull();
	}

	@Test
	void editsThatKeepTheDeadlineDoNotFailOnMigratedViolations() throws Exception {
		UUID space = space();
		UUID parent = createTask(admin, space, "Parent", ",\"deadline\":\"2031-05-10\"");
		UUID sub = subtask(admin, parent, "Migrated", ",\"deadline\":\"2031-05-10\"");
		// as left by the resolution conversion (INC-072): deadlines are not clamped
		jdbc.update("update task set deadline = date '2031-06-30' where id = ?", sub);

		putJson(admin, "/tasks/" + sub, "{\"title\":\"Migrated, renamed\",\"deadline\":\"2031-06-30\"}")
				.andExpect(status().isOk());
		putJson(admin, "/tasks/" + parent, "{\"title\":\"Parent, renamed\",\"deadline\":\"2031-05-10\"}")
				.andExpect(status().isOk());
		// moving the parent later keeps tolerating it; moving it earlier names it
		putJson(admin, "/tasks/" + parent, "{\"title\":\"Parent, renamed\",\"deadline\":\"2031-05-15\"}")
				.andExpect(status().isOk());
		putJson(admin, "/tasks/" + parent, "{\"title\":\"Parent, renamed\",\"deadline\":\"2031-05-12\"}")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.conflictingSubtasks[0].id").value(sub.toString()));
		// status changes are not affected by the deadline rule
		moveTo(admin, sub, statusId(space, "IN_PROGRESS"));
	}

	// ---- TM-4 ----

	@Test
	void archivingCascadesToSubtasksAndRestoreBringsBackOnlyThoseArchivedWithTheParent() throws Exception {
		UUID space = space();
		UUID parent = createTask(admin, space, "Parent", "");
		UUID first = subtask(admin, parent, "First", "");
		UUID second = subtask(admin, parent, "Second", "");
		UUID earlier = subtask(admin, parent, "Archived earlier", "");
		send(admin, "/tasks/" + earlier + "/archive", "").andExpect(status().isOk());
		events.all.clear();

		send(admin, "/tasks/" + parent + "/archive", "").andExpect(status().isOk())
				.andExpect(jsonPath("$.archived").value(true))
				.andExpect(jsonPath("$.subtasks.length()").value(3));
		assertThat(List.of(parent, first, second, earlier)).allSatisfy(t -> assertThat(archived(t)).isTrue());
		assertThat(audits(parent, "ARCHIVE")).isEqualTo(1);
		assertThat(audits(first, "ARCHIVE")).isEqualTo(1);
		assertThat(audits(second, "ARCHIVE")).isEqualTo(1);
		assertThat(audits(earlier, "ARCHIVE")).isEqualTo(1); // only its own archive
		assertThat(events.all.stream().filter(e -> e.kind() == TaskEvent.Kind.ARCHIVED).map(TaskEvent::id))
				.containsExactlyInAnyOrder(parent, first, second);
		// a subtask cannot come back while its parent is archived
		send(admin, "/tasks/" + first + "/restore", "").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TASK_PARENT_ARCHIVED"));
		events.all.clear();

		send(admin, "/tasks/" + parent + "/restore", "").andExpect(status().isOk())
				.andExpect(jsonPath("$.archived").value(false))
				.andExpect(jsonPath("$.subtasks.length()").value(2));
		assertThat(archived(parent)).isFalse();
		assertThat(archived(first)).isFalse();
		assertThat(archived(second)).isFalse();
		assertThat(archived(earlier)).isTrue();
		assertThat(audits(parent, "RESTORE")).isEqualTo(1);
		assertThat(audits(first, "RESTORE")).isEqualTo(1);
		assertThat(audits(second, "RESTORE")).isEqualTo(1);
		assertThat(audits(earlier, "RESTORE")).isZero();
		assertThat(events.all.stream().filter(e -> e.kind() == TaskEvent.Kind.RESTORED).map(TaskEvent::id))
				.containsExactlyInAnyOrder(parent, first, second);
		// no hard delete; the individually archived one can be restored on its own now
		assertThat(jdbc.queryForObject("select count(*) from task where parent_task_id = ?", Long.class, parent))
				.isEqualTo(3);
		send(admin, "/tasks/" + earlier + "/restore", "").andExpect(status().isOk());
	}

	// ---- ST-4, ST-5, spec §9.6 ----

	@Test
	void suggestCloseOnlyWhenEveryCountedSubtaskIsDoneAndTheParentNeverChangesByItself() throws Exception {
		UUID space = space();
		UUID todo = statusId(space, "TODO");
		UUID done = statusId(space, "DONE");
		UUID canceled = statusId(space, "CANCELED");
		UUID parent = createTask(admin, space, "Parent", "");
		read(admin, "/tasks/" + parent).andExpect(jsonPath("$.suggestClose").value(false))
				.andExpect(jsonPath("$.subtaskCount").value(0)).andExpect(jsonPath("$.openSubtaskCount").value(0));

		UUID a = subtask(admin, parent, "A", "");
		UUID b = subtask(admin, parent, "B", "");
		read(admin, "/tasks/" + parent).andExpect(jsonPath("$.suggestClose").value(false))
				.andExpect(jsonPath("$.subtaskCount").value(2)).andExpect(jsonPath("$.openSubtaskCount").value(2));

		moveTo(admin, a, done);
		read(admin, "/tasks/" + parent).andExpect(jsonPath("$.suggestClose").value(false))
				.andExpect(jsonPath("$.openSubtaskCount").value(1));

		moveTo(admin, b, canceled);
		read(admin, "/tasks/" + parent).andExpect(jsonPath("$.suggestClose").value(true))
				.andExpect(jsonPath("$.openSubtaskCount").value(0))
				.andExpect(jsonPath("$.statusId").value(todo.toString()));
		// the list shows the same hint
		read(admin, "/tasks?spaceId=" + space + "&size=50")
				.andExpect(jsonPath("$.content[?(@.id=='" + parent + "')].suggestClose").value(true))
				.andExpect(jsonPath("$.content[?(@.id=='" + parent + "')].subtaskCount").value(2));
		// never changed automatically
		assertThat(jdbc.queryForObject("select status_id from task where id = ?", UUID.class, parent)).isEqualTo(todo);
		assertThat(audits(parent, "UPDATE")).isZero();

		// an archived subtask does not count; a new open one blocks the hint
		UUID c = subtask(admin, parent, "C", "");
		read(admin, "/tasks/" + parent).andExpect(jsonPath("$.suggestClose").value(false))
				.andExpect(jsonPath("$.openSubtaskCount").value(1));
		send(admin, "/tasks/" + c + "/archive", "").andExpect(status().isOk());
		read(admin, "/tasks/" + parent).andExpect(jsonPath("$.suggestClose").value(true))
				.andExpect(jsonPath("$.subtaskCount").value(2));

		// ST-5: the parent may be closed with open subtasks; the DTO exposes the open count
		subtask(admin, parent, "D", "");
		send(admin, "/tasks/" + parent + "/status", "{\"statusId\":\"" + done + "\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.statusId").value(done.toString()))
				.andExpect(jsonPath("$.openSubtaskCount").value(1))
				.andExpect(jsonPath("$.suggestClose").value(false));
	}

	// ---- §7: one request; list endpoints ----

	@Test
	void theDetailCarriesAssigneesAndSubtasksAndTheListsFilterByParent() throws Exception {
		UUID space = space();
		Account viewer = account("tasks.view");
		UUID worker = user();
		UUID parent = createTask(admin, space, "Parent", ",\"deadline\":\"2031-05-31\"");
		send(admin, "/tasks/" + parent + "/assignees", "{\"userId\":\"" + worker + "\"}")
				.andExpect(status().isCreated());
		UUID first = subtask(admin, parent, "First", ",\"assigneeIds\":[\"" + worker + "\"]");
		UUID second = subtask(admin, parent, "Second", ",\"deadline\":\"2031-05-30\"");
		UUID gone = subtask(admin, parent, "Gone", "");
		send(admin, "/tasks/" + gone + "/archive", "").andExpect(status().isOk());
		createTask(admin, space, "Unrelated", "");

		read(viewer.session(), "/tasks/" + parent).andExpect(status().isOk())
				.andExpect(jsonPath("$.assignees.length()").value(1))
				.andExpect(jsonPath("$.assignees[0].userId").value(worker.toString()))
				.andExpect(jsonPath("$.subtasks.length()").value(2))
				.andExpect(jsonPath("$.subtasks[0].id").value(first.toString()))
				.andExpect(jsonPath("$.subtasks[0].assigneeIds[0]").value(worker.toString()))
				.andExpect(jsonPath("$.subtasks[1].id").value(second.toString()))
				.andExpect(jsonPath("$.subtasks[1].deadline").value("2031-05-30"))
				.andExpect(jsonPath("$.subtasks[1].parentId").value(parent.toString()));

		read(viewer.session(), "/tasks/" + parent + "/subtasks").andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2));
		read(viewer.session(), "/tasks/" + parent + "/subtasks?includeArchived=true")
				.andExpect(jsonPath("$.length()").value(3)).andExpect(jsonPath("$[2].archived").value(true));
		read(viewer.session(), "/tasks?parentId=" + parent).andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.content[?(@.id=='" + first + "')].assigneeIds[0]").value(worker.toString()));
		read(viewer.session(), "/tasks?parentId=" + parent + "&includeArchived=true")
				.andExpect(jsonPath("$.content.length()").value(3));
		read(viewer.session(), "/tasks/" + UUID.randomUUID() + "/subtasks").andExpect(status().isNotFound());
	}

}
