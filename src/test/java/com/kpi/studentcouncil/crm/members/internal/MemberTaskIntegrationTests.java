package com.kpi.studentcouncil.crm.members.internal;

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
 * Member-profile task section (RR-5): counters and the list of active tasks come from tasks via active assignments
 * (DONE/CANCELED categories, archived tasks and removed assignments excluded), read through the public
 * {@code tasks} APIs; {@code tasks.view} enforced; unknown member 404.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class MemberTaskIntegrationTests {

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
	private final List<UUID> roles = new ArrayList<>();
	private UUID space;
	private UUID todo;
	private UUID inProgress;
	private UUID done;
	private UUID canceled;
	private Account viewer;
	private UUID member;

	@BeforeEach
	void setUp() throws Exception {
		space = UUID.randomUUID();
		jdbc.update("insert into task_space (id, name, is_general, created_at, updated_at) values (?, ?, true, now(),"
				+ " now())", space, "Member tasks " + space);
		todo = statusRow("Todo", "TODO", 0);
		inProgress = statusRow("Doing", "IN_PROGRESS", 1);
		done = statusRow("Done", "DONE", 2);
		canceled = statusRow("Dropped", "CANCELED", 3);
		viewer = account(role("tasks.view"));
		member = user();
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("delete from task_assignee where task_id in (select id from task where space_id = ?)", space);
		jdbc.update("delete from task where space_id = ? and parent_task_id is not null", space);
		jdbc.update("delete from task where space_id = ?", space);
		jdbc.update("delete from task_space_status where space_id = ?", space);
		jdbc.update("delete from task_space where id = ?", space);
		for (UUID user : users) {
			jdbc.update("delete from role_assignment where user_id = ?", user);
		}
		for (UUID r : roles) {
			jdbc.update("delete from role_permission where role_id = ?", r);
			jdbc.update("delete from role where id = ?", r);
		}
		for (UUID user : users) {
			jdbc.update("delete from users where id = ?", user);
		}
		users.clear();
		roles.clear();
	}

	private UUID role(String... keys) {
		UUID id = UUID.randomUUID();
		roles.add(id);
		jdbc.update("insert into role (id, name, is_system, created_at, updated_at) values (?, ?, false, now(), now())",
				id, "mtask-role-" + id);
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

	private UUID user() {
		UUID user = UUID.randomUUID();
		users.add(user);
		jdbc.update("insert into users (id, login, password_hash, status, locale, must_change_password, created_at,"
				+ " updated_at) values (?, ?, ?, 'ACTIVE', 'en', false, now(), now())", user,
				"mtask-" + user.toString().substring(0, 8), encoder.encode(PASSWORD));
		return user;
	}

	private Account account(UUID role) throws Exception {
		UUID user = user();
		if (role != null) {
			access.grantRole(user, role);
		}
		String login = "mtask-" + user.toString().substring(0, 8);
		MvcResult result = mvc.perform(post("/api/v1/auth/login").with(csrf()).with(request -> {
			request.setRemoteAddr("10.8." + (int) (Math.random() * 250) + "." + (1 + (int) (Math.random() * 250)));
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
				+ " now(), ?, ?, " + (archived ? "now()" : "null") + ")", id, space, title, statusId, viewer.id(),
				deadline, parentId);
		return id;
	}

	private void assign(UUID taskId, UUID userId, boolean removed) {
		jdbc.update("insert into task_assignee (id, task_id, user_id, assigned_by, assigned_at, removed_at, created_at,"
				+ " updated_at) values (?, ?, ?, ?, now(), " + (removed ? "now()" : "null") + ", now(), now())",
				UUID.randomUUID(), taskId, userId, viewer.id());
	}

	private UUID memberTask(String title, UUID statusId, LocalDate deadline) {
		UUID id = task(title, statusId, deadline, false, null);
		assign(id, member, false);
		return id;
	}

	/**
	 * The member is assigned to 4 active tasks (one overdue, one subtask, one without deadline), 2 DONE, 1 CANCELED,
	 * an archived one and a removed assignment; another user has an overdue task of their own.
	 */
	private void seed() {
		memberTask("a-overdue", inProgress, TODAY.minusDays(3));
		UUID parent = memberTask("c-later", todo, TODAY.plusDays(10));
		UUID subtask = task("b-subtask", todo, TODAY.plusDays(1), false, parent);
		assign(subtask, member, false);
		memberTask("d-no-deadline", todo, null);
		memberTask("x-done", done, TODAY.minusDays(5));
		memberTask("y-done", done, null);
		memberTask("z-canceled", canceled, TODAY.minusDays(5));
		UUID archived = task("archived", todo, TODAY.minusDays(1), true, null);
		assign(archived, member, false);
		UUID removed = task("removed", todo, TODAY.minusDays(1), false, null);
		assign(removed, member, true);
		UUID foreign = task("foreign", todo, TODAY.minusDays(1), false, null);
		assign(foreign, viewer.id(), false);
	}

	private ResultActions api(Account who, String path) throws Exception {
		return mvc.perform(get("/api/v1" + path).session(who.session()));
	}

	@Test
	void countsComeFromTasksViaAssignees() throws Exception {
		seed();
		api(viewer, "/members/" + member + "/tasks/counts").andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(4))
				.andExpect(jsonPath("$.overdue").value(1))
				.andExpect(jsonPath("$.done").value(2));
	}

	@Test
	void listHoldsActiveTasksEarliestDeadlineFirstAndNoDeadlineLast() throws Exception {
		seed();
		String body = api(viewer, "/members/" + member + "/tasks").andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(4))
				.andExpect(jsonPath("$.content[0].overdue").value(true))
				.andExpect(jsonPath("$.content[0].statusName").value("Doing"))
				.andExpect(jsonPath("$.content[0].statusCategory").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.content[0].spaceId").value(space.toString()))
				.andExpect(jsonPath("$.content[1].parentId").isNotEmpty())
				.andExpect(jsonPath("$.content[1].overdue").value(false))
				.andExpect(jsonPath("$.content[3].deadline").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		List<String> titles = JsonPath.read(body, "$.content[*].title");
		assertThat(titles).containsExactly("a-overdue", "b-subtask", "c-later", "d-no-deadline");
	}

	@Test
	void listIsPaginated() throws Exception {
		seed();
		String body = api(viewer, "/members/" + member + "/tasks?page=1&size=2").andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(4)).andReturn().getResponse().getContentAsString();
		List<String> titles = JsonPath.read(body, "$.content[*].title");
		assertThat(titles).containsExactly("c-later", "d-no-deadline");
	}

	@Test
	void memberWithoutTasksHasZeroCountsAndAnEmptyList() throws Exception {
		api(viewer, "/members/" + member + "/tasks/counts").andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(0)).andExpect(jsonPath("$.overdue").value(0))
				.andExpect(jsonPath("$.done").value(0));
		api(viewer, "/members/" + member + "/tasks").andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void unknownMemberIs404() throws Exception {
		UUID unknown = UUID.randomUUID();
		api(viewer, "/members/" + unknown + "/tasks").andExpect(status().isNotFound());
		api(viewer, "/members/" + unknown + "/tasks/counts").andExpect(status().isNotFound());
	}

	@Test
	void requiresTasksViewAndAuthentication() throws Exception {
		Account noTasks = account(role("kb.view"));
		api(noTasks, "/members/" + member + "/tasks").andExpect(status().isForbidden());
		api(noTasks, "/members/" + member + "/tasks/counts").andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/members/" + member + "/tasks")).andExpect(status().isUnauthorized());
	}

}
