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

/** Resolution filters and built-in quick views of the task list, and the privacy of saved filters. */
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
			List<UUID> ids = jdbc.queryForList("select id from resolution where task_id = ?", UUID.class, task);
			for (UUID id : ids) {
				jdbc.update("delete from resolution_history where resolution_id = ?", id);
				jdbc.update("delete from resolution_co_executor where resolution_id = ?", id);
			}
			jdbc.update("delete from resolution where task_id = ?", task);
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
		jdbc.update("insert into task (id, space_id, title, status_id, priority, created_at, updated_at) values (?, ?,"
				+ " ?, ?, 'NORMAL', now(), now())", id, space, title, statusId);
		return id;
	}

	private UUID resolution(UUID task, UUID author, UUID executor, String state, LocalDate due, boolean archived) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into resolution (id, task_id, author_id, executor_id, instruction, due_date, state,"
				+ " created_at, updated_at, archived_at) values (?, ?, ?, ?, 'Do it', ?, ?, now(), now(), "
				+ (archived ? "now()" : "null") + ")", id, task, author, executor, due, state);
		return id;
	}

	/** Seed (viewed as "me"): see the expectations in the tests. */
	private void seed() {
		UUID t1 = task("t1-mine-in-progress");
		resolution(t1, other.id(), me.id(), "IN_PROGRESS", TODAY.plusDays(5), false);
		UUID t2 = task("t2-mine-co-executor");
		UUID r2 = resolution(t2, other.id(), third.id(), "NEW", TODAY.plusDays(5), false);
		jdbc.update("insert into resolution_co_executor (resolution_id, user_id) values (?, ?)", r2, me.id());
		UUID t3 = task("t3-issued-overdue");
		resolution(t3, me.id(), other.id(), "SUBMITTED", TODAY.minusDays(3), false);
		UUID t4 = task("t4-accepted-in-the-past");
		resolution(t4, other.id(), me.id(), "ACCEPTED", TODAY.minusDays(10), false);
		UUID t5 = task("t5-issued-canceled");
		resolution(t5, me.id(), other.id(), "CANCELED", TODAY.minusDays(10), false);
		UUID t6 = task("t6-archived-resolution");
		resolution(t6, me.id(), me.id(), "NEW", TODAY.minusDays(2), true);
		task("t7-no-resolution");
		UUID t8 = task("t8-due-today");
		resolution(t8, other.id(), me.id(), "IN_PROGRESS", TODAY, false);
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
	void myResolutionsListsTasksWhereIAmExecutorOrCoExecutorOfALiveResolution() throws Exception {
		seed();
		assertThat(titles(me, "&view=MY_RESOLUTIONS")).containsExactly("t1-mine-in-progress", "t2-mine-co-executor",
				"t8-due-today");
		// another user's quick view is evaluated for that user
		assertThat(titles(other, "&view=MY_RESOLUTIONS")).containsExactly("t3-issued-overdue");
		assertThat(titles(third, "&view=MY_RESOLUTIONS")).containsExactly("t2-mine-co-executor");
	}

	@Test
	void issuedByMeListsTasksWithAResolutionIAuthoredIgnoringArchivedOnes() throws Exception {
		seed();
		assertThat(titles(me, "&view=ISSUED_BY_ME")).containsExactly("t3-issued-overdue", "t5-issued-canceled");
		assertThat(titles(other, "&view=ISSUED_BY_ME")).containsExactly("t1-mine-in-progress", "t2-mine-co-executor",
				"t4-accepted-in-the-past", "t8-due-today");
	}

	@Test
	void overdueMeansDueDatePassedAndStateNotAcceptedOrCanceled() throws Exception {
		seed();
		// t4 is accepted, t5 canceled, t6 archived, t8 is due today (not yet overdue)
		assertThat(titles(me, "&view=OVERDUE")).containsExactly("t3-issued-overdue");
		assertThat(titles(other, "&view=OVERDUE")).containsExactly("t3-issued-overdue");
	}

	@Test
	void resolutionFiltersCombineOnTheSameResolutionAndWorkWithLabels() throws Exception {
		seed();
		assertThat(titles(me, "&executorId=" + me.id())).containsExactly("t1-mine-in-progress", "t2-mine-co-executor",
				"t4-accepted-in-the-past", "t8-due-today");
		assertThat(titles(me, "&authorId=" + me.id())).containsExactly("t3-issued-overdue", "t5-issued-canceled");
		assertThat(titles(me, "&resolutionState=ACCEPTED")).containsExactly("t4-accepted-in-the-past");
		// both conditions on one resolution: executor me and state NEW exists only as co-executor of t2
		assertThat(titles(me, "&executorId=" + me.id() + "&resolutionState=NEW")).containsExactly("t2-mine-co-executor");
		assertThat(titles(me, "&authorId=" + me.id() + "&executorId=" + me.id())).isEmpty();
		jdbc.update("insert into task_label (task_id, label) select id, 'urgent' from task where title = 't3-issued-overdue'");
		assertThat(titles(me, "&view=OVERDUE&labels=urgent")).containsExactly("t3-issued-overdue");
		assertThat(titles(me, "&labels=urgent&authorId=" + other.id())).isEmpty();
	}

	@Test
	void withoutResolutionFiltersEveryTaskIsListed() throws Exception {
		seed();
		assertThat(titles(me, "")).hasSize(8);
	}

	// ---- saved filters ----

	private static final String BODY = "{\"name\":\"%s\",\"criteria\":{\"view\":\"OVERDUE\",\"labels\":[\"Urgent\"],"
			+ "\"resolutionState\":\"NEW\"}}";

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
				.andExpect(jsonPath("$.content[0].criteria.resolutionState").value("NEW"));
		send(me, org.springframework.http.HttpMethod.PUT, "/task-filters/" + id,
				"{\"name\":\"Renamed\",\"criteria\":{\"view\":\"MY_RESOLUTIONS\"}}").andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Renamed")).andExpect(jsonPath("$.criteria.view").value("MY_RESOLUTIONS"))
				.andExpect(jsonPath("$.criteria.labels.length()").value(0));
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
