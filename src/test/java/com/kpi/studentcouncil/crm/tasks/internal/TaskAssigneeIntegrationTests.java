package com.kpi.studentcouncil.crm.tasks.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import com.kpi.studentcouncil.crm.tasks.TaskEvent;
import com.kpi.studentcouncil.crmtest.SharedAdmin;

/**
 * Assignees API (INC-073): AS-1..AS-5, the 403/200 matrix of spec §9.4, audit and domain events (§6.4). Shares the
 * application context of {@link TaskIntegrationTests} (same imports) to reuse its event collector.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, TaskIntegrationTests.EventsConfig.class })
class TaskAssigneeIntegrationTests {

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
			request.setRemoteAddr("10.7." + (int) (Math.random() * 250) + "." + (1 + (int) (Math.random() * 250)));
			return request;
		}).contentType(MediaType.APPLICATION_JSON)
				.content("{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}")).andReturn();
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private UUID user(String status) {
		UUID user = UUID.randomUUID();
		users.add(user);
		jdbc.update("insert into users (id, login, password_hash, status, locale, must_change_password, created_at,"
				+ " updated_at) values (?, ?, ?, ?, 'en', false, now(), now())", user,
				"assignee-" + user.toString().substring(0, 8), encoder.encode(PASSWORD), status);
		return user;
	}

	/** An active user holding a role with the given permission keys, signed in. */
	private Account account(String... permissionKeys) throws Exception {
		UUID user = user("ACTIVE");
		if (permissionKeys.length > 0) {
			UUID role = UUID.randomUUID();
			roles.add(role);
			jdbc.update("insert into role (id, name, is_system, created_at, updated_at) values (?, ?, false, now(),"
					+ " now())", role, "assignee-role-" + role);
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

	private ResultActions remove(MockHttpSession s, UUID task, UUID user) throws Exception {
		return mvc.perform(delete("/api/v1/tasks/" + task + "/assignees/" + user).with(csrf()).session(s)
				.header(HttpHeaders.ACCEPT_LANGUAGE, "en"));
	}

	private ResultActions read(MockHttpSession s, String path) throws Exception {
		return mvc.perform(get("/api/v1" + path).session(s).header(HttpHeaders.ACCEPT_LANGUAGE, "en"));
	}

	private ResultActions assign(MockHttpSession s, UUID task, UUID user) throws Exception {
		return send(s, "/tasks/" + task + "/assignees", "{\"userId\":\"" + user + "\"}");
	}

	private UUID space() throws Exception {
		UUID id = UUID.fromString(JsonPath.read(send(admin, "/spaces", "{\"name\":\"Assignees " + UUID.randomUUID()
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

	private UUID createTask(MockHttpSession s, UUID space, String extra) throws Exception {
		return UUID.fromString(JsonPath.read(send(s, "/tasks", "{\"spaceId\":\"" + space + "\",\"title\":\"Task "
				+ UUID.randomUUID() + "\"" + extra + "}").andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString(), "$.id"));
	}

	private long auditUpdates(UUID task) {
		return jdbc.queryForObject("select count(*) from audit_log where entity_type = 'Task' and entity_id = ? and"
				+ " action = 'UPDATE'", Long.class, task);
	}

	private List<TaskEvent.Kind> kinds(UUID task) {
		return events.all.stream().filter(e -> e.id().equals(task)).map(TaskEvent::kind).toList();
	}

	// ---- AS-1 ----

	@Test
	void aTaskHoldsSeveralActiveAssigneesAndASecondActiveRowForAUserIsRejected() throws Exception {
		UUID space = space();
		Account assigner = account("tasks.view", "tasks.create", "tasks.assign");
		UUID task = createTask(assigner.session(), space, "");
		UUID first = user("ACTIVE");
		UUID second = user("ACTIVE");

		assign(assigner.session(), task, first).andExpect(status().isCreated())
				.andExpect(jsonPath("$.userId").value(first.toString()))
				.andExpect(jsonPath("$.assignedBy").value(assigner.id().toString()))
				.andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.assignedAt").isNotEmpty());
		assign(assigner.session(), task, second).andExpect(status().isCreated());
		assign(assigner.session(), task, first).andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("TASK_ASSIGNEE_DUPLICATE"));

		Account viewer = account("tasks.view");
		read(viewer.session(), "/tasks/" + task + "/assignees").andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].userId").value(first.toString()))
				.andExpect(jsonPath("$[1].userId").value(second.toString()));
		assertThat(jdbc.queryForObject("select count(*) from task_assignee where task_id = ? and user_id = ?",
				Long.class, task, first)).isEqualTo(1);
		// the database is the last line of defence (partial unique index)
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("insert into task_assignee (id, task_id,"
				+ " user_id, assigned_by, assigned_at, created_at, updated_at) values (?, ?, ?, ?, now(), now(), now())",
				UUID.randomUUID(), task, first, assigner.id()))
				.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
	}

	// ---- AS-3, spec §9.4: the 403/200 matrix ----

	@Test
	void selfAssignNeedsTasksCreateAsCreatorOrTasksEditAnyAndOthersNeedTasksAssign() throws Exception {
		UUID space = space();
		Account creator = account("tasks.view", "tasks.create");
		Account colleague = account("tasks.view", "tasks.create");
		Account editAny = account("tasks.view", "tasks.edit.any");
		Account assigner = account("tasks.view", "tasks.assign");
		Account viewer = account("tasks.view");
		Account nobody = account();
		UUID target = user("ACTIVE");
		UUID task = createTask(creator.session(), space, "");

		// coarse gate: one of tasks.create / tasks.edit.any / tasks.assign
		assign(viewer.session(), task, viewer.id()).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		read(nobody.session(), "/tasks/" + task + "/assignees").andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/tasks/" + task + "/assignees")).andExpect(status().isUnauthorized());

		// the creator with tasks.create: self yes, another user no
		assign(creator.session(), task, target).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("TASK_ASSIGN_FORBIDDEN"))
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Assign tasks to others")));
		assign(creator.session(), task, creator.id()).andExpect(status().isCreated());
		// tasks.create on somebody else's task is not enough to self-assign
		assign(colleague.session(), task, colleague.id()).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("TASK_ASSIGN_FORBIDDEN"));
		// tasks.edit.any: self yes, another user no
		assign(editAny.session(), task, target).andExpect(status().isForbidden());
		assign(editAny.session(), task, editAny.id()).andExpect(status().isCreated());
		// tasks.assign: anybody
		assign(assigner.session(), task, target).andExpect(status().isCreated());
		// Admin: anybody
		assign(admin, task, colleague.id()).andExpect(status().isCreated());

		// removal follows the same rule
		remove(creator.session(), task, target).andExpect(status().isForbidden());
		remove(viewer.session(), task, viewer.id()).andExpect(status().isForbidden());
		remove(editAny.session(), task, editAny.id()).andExpect(status().isNoContent());
		remove(assigner.session(), task, target).andExpect(status().isNoContent());
		remove(assigner.session(), task, target).andExpect(status().isNotFound());
		remove(creator.session(), task, creator.id()).andExpect(status().isNoContent());

		read(viewer.session(), "/tasks/" + task + "/assignees").andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].userId").value(colleague.id().toString()));
		// a 404 for an unknown task
		assign(admin, UUID.randomUUID(), target).andExpect(status().isNotFound());
	}

	// ---- AS-3: who can be assigned ----

	@Test
	void archivedBlockedAndUnknownUsersCannotBeAssigned() throws Exception {
		UUID space = space();
		UUID task = createTask(admin, space, "");
		UUID archived = user("ARCHIVED");
		UUID blocked = user("BLOCKED");
		Account english = account("tasks.view", "tasks.assign");
		Account ukrainian = account("tasks.view", "tasks.assign");
		jdbc.update("update users set locale = 'uk' where id = ?", ukrainian.id());

		for (UUID user : List.of(archived, blocked, UUID.randomUUID())) {
			assign(english.session(), task, user).andExpect(status().isUnprocessableContent())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
					.andExpect(jsonPath("$.code").value("TASK_ASSIGNEE_INACTIVE"))
					.andExpect(jsonPath("$.title").value("This user cannot be assigned"));
		}
		String login = jdbc.queryForObject("select login from users where id = ?", String.class, ukrainian.id());
		assign(signIn(login, PASSWORD), task, blocked).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.title").value("Цього користувача не можна призначити"));
		send(admin, "/tasks/" + task + "/assignees", "{}").andExpect(status().isBadRequest());
		assertThat(jdbc.queryForObject("select count(*) from task_assignee where task_id = ?", Long.class, task))
				.isZero();
		assertThat(auditUpdates(task)).isZero();
	}

	// ---- AS-4 ----

	@Test
	void removalKeepsTheRowWithRemovedAtAndAuditsBeforeAndAfter() throws Exception {
		UUID space = space();
		UUID task = createTask(admin, space, "");
		UUID user = user("ACTIVE");
		assign(admin, task, user).andExpect(status().isCreated());
		remove(admin, task, user).andExpect(status().isNoContent());

		assertThat(jdbc.queryForObject("select count(*) from task_assignee where task_id = ? and user_id = ? and"
				+ " removed_at is not null and removed_by is not null", Long.class, task, user)).isEqualTo(1);
		read(admin, "/tasks/" + task + "/assignees").andExpect(jsonPath("$.length()").value(0));
		read(admin, "/tasks/" + task + "/assignees?includeRemoved=true").andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].active").value(false)).andExpect(jsonPath("$[0].removedAt").isNotEmpty());

		// assign + unassign: two task audit entries with before/after
		assertThat(auditUpdates(task)).isEqualTo(2);
		String removal = "select %s from audit_log where entity_type = 'Task' and entity_id = ? and action = 'UPDATE'"
				+ " and after_state -> 'assignee' ->> 'removedAt' is not null";
		assertThat(jdbc.queryForObject(removal.formatted("before_state -> 'assignee' ->> 'removedAt'"), String.class,
				task)).isNull();
		assertThat(jdbc.queryForObject(removal.formatted("before_state ->> 'assigneeIds'"), String.class, task))
				.contains(user.toString());
		assertThat(jdbc.queryForObject(removal.formatted("after_state ->> 'assigneeIds'"), String.class, task))
				.isEqualTo("[]");
		assertThat(kinds(task)).containsSubsequence(TaskEvent.Kind.ASSIGNED, TaskEvent.Kind.UNASSIGNED);
		assertThat(events.all).anySatisfy(e -> {
			assertThat(e.kind()).isEqualTo(TaskEvent.Kind.UNASSIGNED);
			assertThat(e.assigneeId()).isEqualTo(user);
		});

		// re-assigning after removal creates a new active row; history keeps the old one
		assign(admin, task, user).andExpect(status().isCreated());
		assertThat(jdbc.queryForObject("select count(*) from task_assignee where task_id = ? and user_id = ?",
				Long.class, task, user)).isEqualTo(2);
	}

	// ---- AS-5 ----

	@Test
	void anAssigneeMayChangeTheStatusAndLosesThatRightWhenRemoved() throws Exception {
		UUID space = space();
		Account creator = account("tasks.view", "tasks.create", "tasks.assign");
		Account worker = account("tasks.view");
		Account outsider = account("tasks.view", "tasks.create");
		UUID task = createTask(creator.session(), space, "");
		String toProgress = "{\"statusId\":\"" + statusId(space, "IN_PROGRESS") + "\"}";

		send(worker.session(), "/tasks/" + task + "/status", toProgress).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("TASK_CONTRIBUTE_FORBIDDEN"));
		assign(creator.session(), task, worker.id()).andExpect(status().isCreated());
		send(worker.session(), "/tasks/" + task + "/status", toProgress).andExpect(status().isOk())
				.andExpect(jsonPath("$.statusId").value(statusId(space, "IN_PROGRESS").toString()));
		// assignees still cannot edit the fields (creator or Admin only)
		putJson(worker.session(), "/tasks/" + task, "{\"title\":\"Hijacked\"}").andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("TASK_EDIT_FORBIDDEN"));
		send(outsider.session(), "/tasks/" + task + "/status", "{\"statusId\":\"" + statusId(space, "DONE") + "\"}")
				.andExpect(status().isForbidden());

		remove(creator.session(), task, worker.id()).andExpect(status().isNoContent());
		send(worker.session(), "/tasks/" + task + "/status", "{\"statusId\":\"" + statusId(space, "DONE") + "\"}")
				.andExpect(status().isForbidden());
	}

	// ---- §6.4: audit and domain events, none for a no-op ----

	@Test
	void deadlineAndStatusChangesEmitAuditAndEventsAndNoOpsEmitNeither() throws Exception {
		UUID space = space();
		Account creator = account("tasks.view", "tasks.create");
		UUID task = createTask(creator.session(), space, ",\"deadline\":\"2031-05-01\"");
		String title = JsonPath.read(read(admin, "/tasks/" + task).andReturn().getResponse().getContentAsString(),
				"$.title");
		events.all.clear();

		// no-op edit and no-op status change
		putJson(creator.session(), "/tasks/" + task, "{\"title\":\"" + title + "\",\"deadline\":\"2031-05-01\"}")
				.andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0));
		send(creator.session(), "/tasks/" + task + "/status", "{\"statusId\":\"" + statusId(space, "TODO") + "\"}")
				.andExpect(status().isOk());
		assertThat(auditUpdates(task)).isZero();
		assertThat(kinds(task)).isEmpty();

		// deadline change
		putJson(creator.session(), "/tasks/" + task, "{\"title\":\"" + title + "\",\"deadline\":\"2031-06-01\"}")
				.andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
		assertThat(auditUpdates(task)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select (before_state ->> 'deadline') || '>' || (after_state ->> 'deadline')"
				+ " from audit_log where entity_id = ? and action = 'UPDATE'", String.class, task))
				.isEqualTo("2031-05-01>2031-06-01");
		assertThat(kinds(task)).containsExactly(TaskEvent.Kind.UPDATED, TaskEvent.Kind.DEADLINE_CHANGED);
		assertThat(events.all).anySatisfy(e -> {
			assertThat(e.kind()).isEqualTo(TaskEvent.Kind.DEADLINE_CHANGED);
			assertThat(e.previousDeadline()).isEqualTo(LocalDate.of(2031, 5, 1));
			assertThat(e.deadline()).isEqualTo(LocalDate.of(2031, 6, 1));
			assertThat(e.actorId()).isEqualTo(creator.id());
		});

		// status change
		events.all.clear();
		UUID done = statusId(space, "DONE");
		send(creator.session(), "/tasks/" + task + "/status", "{\"statusId\":\"" + done + "\"}")
				.andExpect(status().isOk());
		assertThat(auditUpdates(task)).isEqualTo(2);
		assertThat(kinds(task)).containsExactly(TaskEvent.Kind.STATUS_CHANGED);
		assertThat(events.all).anySatisfy(e -> {
			assertThat(e.previousStatusId()).isEqualTo(statusId(space, "TODO"));
			assertThat(e.statusId()).isEqualTo(done);
		});

		// assign and unassign
		events.all.clear();
		assign(creator.session(), task, creator.id()).andExpect(status().isCreated());
		remove(creator.session(), task, creator.id()).andExpect(status().isNoContent());
		assertThat(auditUpdates(task)).isEqualTo(4);
		assertThat(kinds(task)).containsExactly(TaskEvent.Kind.ASSIGNED, TaskEvent.Kind.UNASSIGNED);

		// rejected changes emit nothing
		events.all.clear();
		remove(creator.session(), task, creator.id()).andExpect(status().isNotFound());
		assertThat(auditUpdates(task)).isEqualTo(4);
		assertThat(kinds(task)).isEmpty();
	}

	@Test
	void assigneesOfAnArchivedTaskCannotChangeButCanBeRead() throws Exception {
		UUID space = space();
		UUID task = createTask(admin, space, "");
		UUID user = user("ACTIVE");
		assign(admin, task, user).andExpect(status().isCreated());
		send(admin, "/tasks/" + task + "/archive", "").andExpect(status().isOk());

		assign(admin, task, user("ACTIVE")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TASK_ARCHIVED"));
		remove(admin, task, user).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TASK_ARCHIVED"));
		read(admin, "/tasks/" + task + "/assignees").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
	}

}
