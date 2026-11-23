package com.kpi.studentcouncil.crm.tasks.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * Built-in quick views ("My tasks", "Issued by me", "Overdue") and the assignee/creator/overdue/deadline filters of
 * the task list (INC-073), and saved filters.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TaskQuickViewIntegrationTests {

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
	private final List<UUID> tasks = new ArrayList<>();
	private UUID role;
	private UUID space;
	private UUID statusId;
	private UUID progress;
	private UUID done;
	private UUID canceled;
	private Account me;
	private Account other;
	private Account third;

	@BeforeEach
	void setUp() throws Exception {
		role = UUID.randomUUID();
		jdbc.update("insert into role (id, name, is_system, created_at, updated_at) values (?, ?, false, now(), now())",
				role, "qv-role-" + role);
		jdbc.update("insert into role_permission (role_id, permission_key) values (?, 'tasks.view')", role);
		space = UUID.randomUUID();
		jdbc.update("insert into task_space (id, name, is_general, created_at, updated_at) values (?, ?, true, now(),"
				+ " now())", space, "Quick views " + space);
		statusId = UUID.randomUUID();
		jdbc.update("insert into task_space_status (id, space_id, name, category, position, created_at, updated_at)"
				+ " values (?, ?, 'Todo', 'TODO', 0, now(), now())", statusId, space);
		progress = spaceStatus("Doing", "IN_PROGRESS", 1);
		done = spaceStatus("Done", "DONE", 2);
		canceled = spaceStatus("Dropped", "CANCELED", 3);
		me = account();
		other = account();
		third = account();
	}

	@AfterEach
	void cleanUp() {
		for (UUID user : users) {
			jdbc.update("delete from task_saved_filter_label where filter_id in (select id from task_saved_filter where"
					+ " owner_id = ?)", user);
			jdbc.update("delete from task_saved_filter where owner_id = ?", user);
		}
		for (UUID task : tasks) {
			jdbc.update("delete from task_assignee where task_id = ?", task);
			jdbc.update("delete from task_label where task_id = ?", task);
			jdbc.update("delete from task where id = ?", task);
		}
		jdbc.update("delete from task_space_status where space_id = ?", space);
		jdbc.update("delete from task_space where id = ?", space);
		for (UUID user : users) {
			jdbc.update("delete from role_assignment where user_id = ?", user);
		}
		jdbc.update("delete from role_permission where role_id = ?", role);
		jdbc.update("delete from role where id = ?", role);
		for (UUID user : users) {
			jdbc.update("delete from users where id = ?", user);
		}
		users.clear();
		tasks.clear();
	}

	private Account account() throws Exception {
		UUID user = UUID.randomUUID();
		users.add(user);
		String login = "qv-" + user.toString().substring(0, 8);
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

	private UUID task(String title) {
		UUID id = UUID.randomUUID();
		tasks.add(id);
		jdbc.update("insert into task (id, space_id, title, status_id, priority, created_at, updated_at, creator_id, issued_at) values (?, ?,"
				+ " ?, ?, 'NORMAL', now(), now(), '00000000-0000-0000-0000-000000000000', now())", id, space, title, statusId);
		return id;
	}

	private UUID spaceStatus(String name, String category, int position) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into task_space_status (id, space_id, name, category, position, created_at, updated_at)"
				+ " values (?, ?, ?, ?, ?, now(), now())", id, space, name, category, position);
		return id;
	}

	/** A task created by {@code creator} in the given status. */
	private UUID task(String title, UUID creator, UUID status, LocalDate deadline, boolean archived) {
		UUID id = UUID.randomUUID();
		tasks.add(id);
		jdbc.update("insert into task (id, space_id, title, status_id, priority, deadline, created_at, updated_at,"
				+ " creator_id, issued_at, archived_at) values (?, ?, ?, ?, 'NORMAL', ?, now(), now(), ?, now(), "
				+ (archived ? "now()" : "null") + ")", id, space, title, status, deadline, creator);
		return id;
	}

	private void assign(UUID task, UUID user, boolean removed) {
		jdbc.update("insert into task_assignee (id, task_id, user_id, assigned_by, assigned_at, removed_at, created_at,"
				+ " updated_at) values (?, ?, ?, ?, now(), " + (removed ? "now()" : "null") + ", now(), now())",
				UUID.randomUUID(), task, user, user);
	}

	/** Assignment seed (viewed as "me"): see the expectations in the tests. */
	private void seedAssignments() {
		UUID t1 = task("t1-mine-todo", other.id(), statusId, TODAY.plusDays(5), false);
		assign(t1, me.id(), false);
		UUID t2 = task("t2-mine-and-third", other.id(), progress, null, false);
		assign(t2, me.id(), false);
		assign(t2, third.id(), false);
		UUID t3 = task("t3-issued-overdue", me.id(), statusId, TODAY.minusDays(3), false);
		assign(t3, other.id(), false);
		UUID t4 = task("t4-mine-done-past", other.id(), done, TODAY.minusDays(10), false);
		assign(t4, me.id(), false);
		UUID t5 = task("t5-issued-canceled-past", me.id(), canceled, TODAY.minusDays(10), false);
		assign(t5, other.id(), false);
		UUID t6 = task("t6-archived-overdue", me.id(), statusId, TODAY.minusDays(2), true);
		assign(t6, me.id(), false);
		assign(t6, other.id(), false);
		task("t7-no-assignee-overdue", me.id(), statusId, TODAY.minusDays(1), false);
		UUID t8 = task("t8-due-today", other.id(), progress, TODAY, false);
		assign(t8, me.id(), false);
		UUID t9 = task("t9-removed-assignee", other.id(), statusId, null, false);
		assign(t9, me.id(), true);
		UUID t10 = task("t10-self-assigned", me.id(), statusId, null, false);
		assign(t10, me.id(), false);
	}

	private ResultActions list(Account who, String query) throws Exception {
		return mvc.perform(get("/api/v1/tasks?spaceId=" + space + "&size=50" + query).session(who.session()));
	}

	private List<String> titles(Account who, String query) throws Exception {
		String body = list(who, query).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		List<String> result = JsonPath.read(body, "$.content[*].title");
		return result.stream().sorted().toList();
	}

	@Test
	void myTasksListsOpenTasksWhereIAmAnActiveAssignee() throws Exception {
		seedAssignments();
		// t4 is done, t6 archived, t9 no longer assigned to me
		assertThat(titles(me, "&view=MY_TASKS")).containsExactly("t1-mine-todo", "t10-self-assigned",
				"t2-mine-and-third", "t8-due-today");
		// another user's quick view is evaluated for that user; t5 is canceled
		assertThat(titles(other, "&view=MY_TASKS")).containsExactly("t3-issued-overdue");
		assertThat(titles(third, "&view=MY_TASKS")).containsExactly("t2-mine-and-third");
	}

	@Test
	void issuedByMeListsTasksICreatedAndAssignedToSomeoneElse() throws Exception {
		seedAssignments();
		// t6 archived, t7 has no assignee, t10 is assigned to me only
		assertThat(titles(me, "&view=ISSUED_BY_ME")).containsExactly("t3-issued-overdue", "t5-issued-canceled-past");
		// t9's only assignment was removed
		assertThat(titles(other, "&view=ISSUED_BY_ME")).containsExactly("t1-mine-todo", "t2-mine-and-third",
				"t4-mine-done-past", "t8-due-today");
	}

	@Test
	void overdueMeansDeadlinePassedOpenCategoryAndNotArchived() throws Exception {
		seedAssignments();
		// t4 is done, t5 canceled, t6 archived, t8 is due today (not yet overdue)
		assertThat(titles(me, "&view=OVERDUE")).containsExactly("t3-issued-overdue", "t7-no-assignee-overdue");
		assertThat(titles(other, "&overdue=true")).containsExactly("t3-issued-overdue", "t7-no-assignee-overdue");
		// archived tasks are never overdue, even when archived ones are requested
		assertThat(titles(me, "&overdue=true&includeArchived=true")).containsExactly("t3-issued-overdue",
				"t7-no-assignee-overdue");
		assertThat(titles(me, "&overdue=false")).hasSize(9);
	}

	@Test
	void assigneeCreatorAndDeadlineFiltersCombine() throws Exception {
		seedAssignments();
		assertThat(titles(third, "&assignee=" + me.id())).containsExactly("t1-mine-todo", "t10-self-assigned",
				"t2-mine-and-third", "t4-mine-done-past", "t8-due-today");
		assertThat(titles(third, "&assignee=" + me.id() + "&includeArchived=true")).contains("t6-archived-overdue")
				.hasSize(6);
		assertThat(titles(third, "&creator=" + me.id())).containsExactly("t10-self-assigned", "t3-issued-overdue",
				"t5-issued-canceled-past", "t7-no-assignee-overdue");
		assertThat(titles(third, "&creator=" + me.id() + "&assignee=" + other.id())).containsExactly(
				"t3-issued-overdue", "t5-issued-canceled-past");
		assertThat(titles(third, "&assignee=" + other.id() + "&overdue=true")).containsExactly("t3-issued-overdue");
		assertThat(titles(third, "&deadlineFrom=" + TODAY + "&deadlineTo=" + TODAY.plusDays(5)))
				.containsExactly("t1-mine-todo", "t8-due-today");
		assertThat(titles(third, "&assignee=" + me.id() + "&deadlineTo=" + TODAY)).containsExactly(
				"t4-mine-done-past", "t8-due-today");
		assertThat(titles(third, "&assignee=" + third.id())).containsExactly("t2-mine-and-third");
	}

	@Test
	void assigneeFilterIsPaginated() throws Exception {
		seedAssignments();
		String base = "/api/v1/tasks?spaceId=" + space + "&assignee=" + me.id() + "&sort=title,asc&size=2";
		mvc.perform(get(base + "&page=0").session(me.session())).andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(2)).andExpect(jsonPath("$.totalElements").value(5))
				.andExpect(jsonPath("$.totalPages").value(3));
		mvc.perform(get(base + "&page=2").session(me.session())).andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1));
	}

	@Test
	void theRetiredMyResolutionsViewIsRejected() throws Exception {
		mvc.perform(get("/api/v1/tasks?view=MY_RESOLUTIONS").session(me.session())).andExpect(status().isBadRequest());
	}

	// ---- saved filters ----

	private static final String BODY = "{\"name\":\"%s\",\"criteria\":{\"view\":\"OVERDUE\",\"labels\":[\"Urgent\"]}}";

	private ResultActions send(Account who, org.springframework.http.HttpMethod method, String path, String json)
			throws Exception {
		var builder = switch (method.name()) {
			case "POST" -> post("/api/v1" + path);
			case "PUT" -> put("/api/v1" + path);
			default -> delete("/api/v1" + path);
		};
		builder = builder.with(csrf()).session(who.session()).header(HttpHeaders.ACCEPT_LANGUAGE, "en");
		if (json != null) {
			builder = builder.contentType(MediaType.APPLICATION_JSON).content(json);
		}
		return mvc.perform(builder);
	}

	private UUID save(Account who, String name) throws Exception {
		ResultActions r = send(who, org.springframework.http.HttpMethod.POST, "/task-filters", BODY.formatted(name))
				.andExpect(status().isCreated());
		return UUID.fromString(JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id"));
	}

	@Test
	void savedFiltersAreStoredNormalizedListedAndAudited() throws Exception {
		UUID id = save(me, "Overdue urgent");
		mvc.perform(get("/api/v1/task-filters").session(me.session())).andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(id.toString()))
				.andExpect(jsonPath("$.content[0].name").value("Overdue urgent"))
				.andExpect(jsonPath("$.content[0].criteria.view").value("OVERDUE"))
				.andExpect(jsonPath("$.content[0].criteria.labels[0]").value("urgent"))
				.andExpect(jsonPath("$.content[0].criteria.executorId").doesNotExist());
		send(me, org.springframework.http.HttpMethod.PUT, "/task-filters/" + id,
				"{\"name\":\"Renamed\",\"criteria\":{\"view\":\"MY_TASKS\",\"assignee\":\"" + other.id()
						+ "\",\"creator\":\"" + me.id() + "\",\"overdue\":true,\"deadlineFrom\":\"2031-01-01\","
						+ "\"deadlineTo\":\"2031-02-01\"}}").andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Renamed")).andExpect(jsonPath("$.criteria.view").value("MY_TASKS"))
				.andExpect(jsonPath("$.criteria.assignee").value(other.id().toString()))
				.andExpect(jsonPath("$.criteria.creator").value(me.id().toString()))
				.andExpect(jsonPath("$.criteria.overdue").value(true))
				.andExpect(jsonPath("$.criteria.deadlineFrom").value("2031-01-01"))
				.andExpect(jsonPath("$.criteria.deadlineTo").value("2031-02-01"))
				.andExpect(jsonPath("$.criteria.labels.length()").value(0));
		send(me, org.springframework.http.HttpMethod.PUT, "/task-filters/" + id,
				"{\"name\":\"Old\",\"criteria\":{\"view\":\"MY_RESOLUTIONS\"}}").andExpect(status().isBadRequest());
		send(me, org.springframework.http.HttpMethod.DELETE, "/task-filters/" + id, null).andExpect(status().isNoContent());
		mvc.perform(get("/api/v1/task-filters").session(me.session())).andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(0));
		// archived, not deleted
		assertThat(jdbc.queryForObject("select count(*) from task_saved_filter where id = ? and archived_at is not null",
				Long.class, id)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_type = 'SavedFilter' and entity_id = ?",
				Long.class, id)).isEqualTo(3);
	}

	@Test
	void savedFiltersArePrivateToTheirOwner() throws Exception {
		UUID id = save(me, "Mine only");
		mvc.perform(get("/api/v1/task-filters").session(other.session())).andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(0));
		mvc.perform(get("/api/v1/task-filters/" + id).session(other.session())).andExpect(status().isNotFound());
		send(other, org.springframework.http.HttpMethod.PUT, "/task-filters/" + id, BODY.formatted("Hijack"))
				.andExpect(status().isNotFound());
		send(other, org.springframework.http.HttpMethod.DELETE, "/task-filters/" + id, null)
				.andExpect(status().isNotFound());
		// untouched for the owner
		mvc.perform(get("/api/v1/task-filters/" + id).session(me.session())).andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Mine only"));
		// the same name is free for another user
		save(other, "Mine only");
	}

	@Test
	void savedFiltersNeedAuthenticationAndUniqueNames() throws Exception {
		mvc.perform(get("/api/v1/task-filters")).andExpect(status().isUnauthorized());
		save(me, "Same");
		send(me, org.springframework.http.HttpMethod.POST, "/task-filters", BODY.formatted("same"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SAVED_FILTER_NAME_TAKEN"));
		send(me, org.springframework.http.HttpMethod.POST, "/task-filters", BODY.formatted(" "))
				.andExpect(status().isBadRequest());
	}

}
