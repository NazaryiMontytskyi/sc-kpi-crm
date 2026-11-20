package com.kpi.studentcouncil.crm.analytics.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
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
 * The personal dashboard ("My metrics", "My tasks"): built on tasks via active assignments (RR-5), counts equal the
 * "My tasks" quick view, DONE/CANCELED categories and archived tasks excluded, only the caller's data, permission
 * enforced. The deprecated resolution-shaped section mirrors the same task data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DashboardIntegrationTests {

	private static final String PASSWORD = "Sup3r-secret-pw";
	private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Europe/Kyiv"));

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PasswordEncoder encoder;

	@Autowired
	AccessService access;

	private record Account(UUID id, MockHttpSession session) {
	}

	private final List<UUID> users = new ArrayList<>();
	private UUID fullRole;
	private UUID tasksOnlyRole;
	private UUID space;
	private UUID todo;
	private UUID inProgress;
	private UUID done;
	private UUID canceled;
	private Account me;
	private Account other;
	private UUID overdueTask;

	@BeforeEach
	void setUp() throws Exception {
		fullRole = role("tasks.view", "dashboard.view");
		tasksOnlyRole = role("tasks.view");
		space = UUID.randomUUID();
		jdbc.update("insert into task_space (id, name, is_general, created_at, updated_at) values (?, ?, true, now(),"
				+ " now())", space, "Dashboard " + space);
		todo = statusRow("Todo", "TODO", 0);
		inProgress = statusRow("Doing", "IN_PROGRESS", 1);
		done = statusRow("Done", "DONE", 2);
		canceled = statusRow("Dropped", "CANCELED", 3);
		me = account(fullRole);
		other = account(fullRole);
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("delete from task_assignee where task_id in (select id from task where space_id = ?)", space);
		jdbc.update("delete from task where space_id = ?", space);
		jdbc.update("delete from task_space_status where space_id = ?", space);
		jdbc.update("delete from task_space where id = ?", space);
		for (UUID user : users) {
			jdbc.update("delete from role_assignment where user_id = ?", user);
		}
		for (UUID r : List.of(fullRole, tasksOnlyRole)) {
			jdbc.update("delete from role_permission where role_id = ?", r);
			jdbc.update("delete from role where id = ?", r);
		}
		for (UUID user : users) {
			jdbc.update("delete from users where id = ?", user);
		}
		users.clear();
	}

	private UUID role(String... keys) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into role (id, name, is_system, created_at, updated_at) values (?, ?, false, now(), now())",
				id, "dash-role-" + id);
		for (String key : keys) {
			jdbc.update("insert into role_permission (role_id, permission_key) values (?, ?)", id, key);
		}
		return id;
	}

	private UUID statusRow(String name, String category, int position) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into task_space_status (id, space_id, name, category, position, created_at, updated_at)"
				+ " values (?, ?, ?, ?, ?, now(), now())", id, space, name, category, position);
		return id;
	}

	private Account account(UUID role) throws Exception {
		UUID user = UUID.randomUUID();
		users.add(user);
		String login = "dash-" + user.toString().substring(0, 8);
		jdbc.update("insert into users (id, login, password_hash, status, locale, must_change_password, created_at,"
				+ " updated_at) values (?, ?, ?, 'ACTIVE', 'en', false, now(), now())", user, login,
				encoder.encode(PASSWORD));
		access.grantRole(user, role);
		MvcResult result = mvc.perform(post("/api/v1/auth/login").with(csrf()).with(request -> {
			request.setRemoteAddr("10.9." + (int) (Math.random() * 250) + "." + (1 + (int) (Math.random() * 250)));
			return request;
		}).contentType(MediaType.APPLICATION_JSON)
				.content("{\"login\":\"" + login + "\",\"password\":\"" + PASSWORD + "\"}")).andReturn();
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		return new Account(user, (MockHttpSession) result.getRequest().getSession(false));
	}

	private UUID task(String title, UUID statusId, LocalDate deadline, boolean archived, UUID parentId) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into task (id, space_id, title, status_id, priority, created_at, updated_at, creator_id,"
				+ " issued_at, deadline, parent_task_id, archived_at) values (?, ?, ?, ?, 'NORMAL', now(), now(), ?,"
				+ " now(), ?, ?, " + (archived ? "now()" : "null") + ")", id, space, title, statusId, other.id(),
				deadline, parentId);
		return id;
	}

	private void assign(UUID taskId, UUID userId, boolean removed) {
		jdbc.update("insert into task_assignee (id, task_id, user_id, assigned_by, assigned_at, removed_at, created_at,"
				+ " updated_at) values (?, ?, ?, ?, now(), " + (removed ? "now()" : "null") + ", now(), now())",
				UUID.randomUUID(), taskId, userId, other.id());
	}

	private UUID mine(String title, UUID statusId, LocalDate deadline) {
		UUID id = task(title, statusId, deadline, false, null);
		assign(id, me.id(), false);
		return id;
	}

	/**
	 * "me" is assigned to 11 active tasks (1 overdue, 1 without deadline, 1 subtask, 1 shared with "other") plus a
	 * DONE, a CANCELED, an archived and a removed-assignment task; "other" to the shared one and one overdue task.
	 */
	private void seed() {
		overdueTask = mine("a-overdue", inProgress, TODAY.minusDays(2));
		UUID future = mine("b-future", todo, TODAY.plusDays(4));
		mine("c-done", done, TODAY.minusDays(9));
		mine("e-canceled", canceled, TODAY.minusDays(5));
		UUID archived = task("d-archived", todo, TODAY.minusDays(3), true, null);
		assign(archived, me.id(), false);
		UUID removed = task("f-removed", todo, TODAY.minusDays(1), false, null);
		assign(removed, me.id(), true);
		UUID othersOverdue = task("g-other", todo, TODAY.minusDays(1), false, null);
		assign(othersOverdue, other.id(), false);
		UUID subtask = task("h-subtask", todo, TODAY.plusDays(2), false, future);
		assign(subtask, me.id(), false);
		mine("i-no-deadline", todo, null);
		UUID shared = mine("j-shared", todo, TODAY.plusDays(3));
		assign(shared, other.id(), false);
		for (int i = 0; i < 6; i++) {
			mine("many-" + i, todo, TODAY.plusDays(10 + i));
		}
	}

	private ResultActions api(Account who, String path) throws Exception {
		return mvc.perform(get("/api/v1" + path).session(who.session()));
	}

	private int total(Account who, String path) throws Exception {
		String body = api(who, path).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.totalElements");
	}

	@Test
	void myMetricsComeFromTasksViaAssigneesAndEqualTheMyTasksView() throws Exception {
		seed();
		int myTasksView = total(me, "/tasks?view=MY_TASKS");
		int overdueFilter = total(me, "/tasks?assignee=" + me.id() + "&overdue=true");
		api(me, "/dashboard/me").andExpect(status().isOk())
				.andExpect(jsonPath("$.tasks.active").value(11))
				.andExpect(jsonPath("$.tasks.active").value(myTasksView))
				.andExpect(jsonPath("$.tasks.overdue").value(1))
				.andExpect(jsonPath("$.tasks.overdue").value(overdueFilter));
	}

	@Test
	void upcomingAreTheFiveEarliestDeadlinesOverdueFirst() throws Exception {
		seed();
		String dash = api(me, "/dashboard/me").andReturn().getResponse().getContentAsString();
		List<String> titles = JsonPath.read(dash, "$.tasks.upcoming[*].title");
		assertThat(titles).containsExactly("a-overdue", "h-subtask", "j-shared", "b-future", "many-0");
		api(me, "/dashboard/me").andExpect(jsonPath("$.tasks.upcoming[0].taskId").value(overdueTask.toString()))
				.andExpect(jsonPath("$.tasks.upcoming[0].overdue").value(true))
				.andExpect(jsonPath("$.tasks.upcoming[0].statusName").value("Doing"))
				.andExpect(jsonPath("$.tasks.upcoming[0].statusCategory").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.tasks.upcoming[0].deadline").value(TODAY.minusDays(2).toString()))
				.andExpect(jsonPath("$.tasks.upcoming[0].spaceId").value(space.toString()))
				.andExpect(jsonPath("$.tasks.upcoming[1].parentId").isNotEmpty())
				.andExpect(jsonPath("$.tasks.upcoming[1].overdue").value(false));
	}

	@Test
	void legacyResolutionSectionMirrorsTheTaskData() throws Exception {
		seed();
		api(me, "/dashboard/me").andExpect(jsonPath("$.resolutions.active").value(11))
				.andExpect(jsonPath("$.resolutions.overdue").value(1))
				.andExpect(jsonPath("$.resolutions.awaitingAcceptance").value(0))
				.andExpect(jsonPath("$.resolutions.upcoming.length()").value(5))
				.andExpect(jsonPath("$.resolutions.upcoming[0].resolutionId").value(overdueTask.toString()))
				.andExpect(jsonPath("$.resolutions.upcoming[0].taskId").value(overdueTask.toString()))
				.andExpect(jsonPath("$.resolutions.upcoming[0].taskTitle").value("a-overdue"))
				.andExpect(jsonPath("$.resolutions.upcoming[0].state").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.resolutions.upcoming[1].state").value("NEW"));
	}

	@Test
	void showsOnlyTheCallersData() throws Exception {
		seed();
		api(other, "/dashboard/me").andExpect(status().isOk()).andExpect(jsonPath("$.tasks.active").value(2))
				.andExpect(jsonPath("$.tasks.overdue").value(1));
		// an id parameter is ignored
		api(me, "/dashboard/me?userId=" + other.id()).andExpect(jsonPath("$.tasks.active").value(11));
	}

	@Test
	void requiresDashboardViewAndAuthentication() throws Exception {
		Account noDashboard = account(tasksOnlyRole);
		api(noDashboard, "/dashboard/me").andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/dashboard/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void emptyDashboardForUserWithoutTasks() throws Exception {
		api(me, "/dashboard/me").andExpect(status().isOk()).andExpect(jsonPath("$.tasks.active").value(0))
				.andExpect(jsonPath("$.tasks.overdue").value(0))
				.andExpect(jsonPath("$.tasks.upcoming.length()").value(0))
				.andExpect(jsonPath("$.resolutions.active").value(0))
				.andExpect(jsonPath("$.resolutions.upcoming.length()").value(0));
	}

}
