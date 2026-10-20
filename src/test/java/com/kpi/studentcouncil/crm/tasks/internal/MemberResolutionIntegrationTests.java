package com.kpi.studentcouncil.crm.tasks.internal;

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
import com.kpi.studentcouncil.crm.tasks.MemberResolutionCounts;

/** Resolutions of a member: lists, counters (overdue per CONTEXT.md 5.3, archived excluded) and openness. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class MemberResolutionIntegrationTests {

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

	@Autowired
	MemberResolutionCounts publicCounts;

	private record Account(UUID id, MockHttpSession session) {
	}

	private final List<UUID> users = new ArrayList<>();
	private final List<UUID> tasks = new ArrayList<>();
	private UUID role;
	private UUID space;
	private UUID statusId;
	private Account me;
	private Account other;

	@BeforeEach
	void setUp() throws Exception {
		role = UUID.randomUUID();
		jdbc.update("insert into role (id, name, is_system, created_at, updated_at) values (?, ?, false, now(), now())",
				role, "mr-role-" + role);
		jdbc.update("insert into role_permission (role_id, permission_key) values (?, 'tasks.view')", role);
		space = UUID.randomUUID();
		jdbc.update("insert into task_space (id, name, is_general, created_at, updated_at) values (?, ?, true, now(),"
				+ " now())", space, "Member resolutions " + space);
		statusId = UUID.randomUUID();
		jdbc.update("insert into task_space_status (id, space_id, name, category, position, created_at, updated_at)"
				+ " values (?, ?, 'Todo', 'TODO', 0, now(), now())", statusId, space);
		me = account(true);
		other = account(true);
	}

	@AfterEach
	void cleanUp() {
		for (UUID task : tasks) {
			List<UUID> ids = jdbc.queryForList("select id from resolution where task_id = ?", UUID.class, task);
			for (UUID id : ids) {
				jdbc.update("delete from resolution_history where resolution_id = ?", id);
				jdbc.update("delete from resolution_co_executor where resolution_id = ?", id);
			}
			jdbc.update("delete from resolution where task_id = ?", task);
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

	private Account account(boolean withPermission) throws Exception {
		UUID user = UUID.randomUUID();
		users.add(user);
		String login = "mr-" + user.toString().substring(0, 8);
		jdbc.update("insert into users (id, login, password_hash, status, locale, must_change_password, created_at,"
				+ " updated_at) values (?, ?, ?, 'ACTIVE', 'en', false, now(), now())", user, login,
				encoder.encode(PASSWORD));
		if (withPermission) {
			access.grantRole(user, role);
		}
		MvcResult result = mvc.perform(post("/api/v1/auth/login").with(csrf()).with(request -> {
			request.setRemoteAddr("10.8." + (int) (Math.random() * 250) + "." + (1 + (int) (Math.random() * 250)));
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

	private UUID resolution(String title, UUID author, UUID executor, String state, LocalDate due, boolean archived) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into resolution (id, task_id, author_id, executor_id, instruction, due_date, state,"
				+ " created_at, updated_at, archived_at) values (?, ?, ?, ?, 'Do it', ?, ?, now(), now(), "
				+ (archived ? "now()" : "null") + ")", id, task(title), author, executor, due, state);
		return id;
	}

	/** Seed: "me" has 3 active (one overdue, one as co-executor), 1 accepted, 1 canceled, 1 archived, 1 issued. */
	private void seed() {
		resolution("r1-in-progress-overdue", other.id(), me.id(), "IN_PROGRESS", TODAY.minusDays(2), false);
		resolution("r2-new-future", other.id(), me.id(), "NEW", TODAY.plusDays(4), false);
		UUID r3 = resolution("r3-co-executor", other.id(), other.id(), "REWORK", TODAY, false);
		jdbc.update("insert into resolution_co_executor (resolution_id, user_id) values (?, ?)", r3, me.id());
		resolution("r4-accepted", other.id(), me.id(), "ACCEPTED", TODAY.minusDays(9), false);
		resolution("r5-canceled-past", other.id(), me.id(), "CANCELED", TODAY.minusDays(9), false);
		resolution("r6-archived-overdue", other.id(), me.id(), "NEW", TODAY.minusDays(3), true);
		resolution("r7-issued-by-me", me.id(), other.id(), "SUBMITTED", TODAY.minusDays(1), false);
	}

	private ResultActions api(Account who, String path) throws Exception {
		return mvc.perform(get("/api/v1" + path).session(who.session()));
	}

	private List<String> titles(Account who, String path) throws Exception {
		String body = api(who, path).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		List<String> result = JsonPath.read(body, "$.content[*].taskTitle");
		return result;
	}

	@Test
	void countsFollowTheFixturesIncludingOverdueAcceptedAndArchived() throws Exception {
		seed();
		api(me, "/members/" + me.id() + "/resolutions/counts").andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(3)).andExpect(jsonPath("$.overdue").value(1))
				.andExpect(jsonPath("$.accepted").value(1));
		// the same numbers through the public module interface
		assertThat(publicCounts.countsFor(me.id())).isEqualTo(new MemberResolutionCounts.Counts(3, 1, 1));
		// due today is not overdue; only the executor side counts
		api(me, "/members/" + other.id() + "/resolutions/counts").andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(2)).andExpect(jsonPath("$.overdue").value(1))
				.andExpect(jsonPath("$.accepted").value(0));
	}

	@Test
	void activeListHoldsLiveResolutionsOfTheExecutorSortedByDueDate() throws Exception {
		seed();
		assertThat(titles(other, "/members/" + me.id() + "/resolutions?active=true")).containsExactly(
				"r1-in-progress-overdue", "r3-co-executor", "r2-new-future");
		api(other, "/members/" + me.id() + "/resolutions?active=true")
				.andExpect(jsonPath("$.content[0].overdue").value(true))
				.andExpect(jsonPath("$.content[1].overdue").value(false)).andExpect(jsonPath("$.totalElements").value(3));
	}

	@Test
	void stateFilterAndAuthorRole() throws Exception {
		seed();
		assertThat(titles(me, "/members/" + me.id() + "/resolutions?state=ACCEPTED")).containsExactly("r4-accepted");
		assertThat(titles(me, "/members/" + me.id() + "/resolutions?state=NEW")).containsExactly("r2-new-future");
		assertThat(titles(me, "/members/" + me.id() + "/resolutions?role=AUTHOR")).containsExactly("r7-issued-by-me");
		assertThat(titles(me, "/members/" + me.id() + "/resolutions?role=AUTHOR&active=true"))
				.containsExactly("r7-issued-by-me");
		assertThat(titles(me, "/members/" + me.id() + "/resolutions?role=AUTHOR&state=NEW")).isEmpty();
	}

	@Test
	void listIsPaginatedAndExcludesArchived() throws Exception {
		seed();
		api(me, "/members/" + me.id() + "/resolutions?size=2&page=1").andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(2)).andExpect(jsonPath("$.totalElements").value(5))
				.andExpect(jsonPath("$.totalPages").value(3));
	}

	@Test
	void anotherMembersProfileIsVisibleToEveryUserWithTasksView() throws Exception {
		seed();
		api(other, "/members/" + me.id() + "/resolutions").andExpect(status().isOk());
		api(other, "/members/" + me.id() + "/resolutions/counts").andExpect(status().isOk());
		Account noPermission = account(false);
		api(noPermission, "/members/" + me.id() + "/resolutions").andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/members/" + me.id() + "/resolutions/counts")).andExpect(status().isUnauthorized());
	}

	@Test
	void unknownMemberIsNotFound() throws Exception {
		api(me, "/members/" + UUID.randomUUID() + "/resolutions").andExpect(status().isNotFound());
		api(me, "/members/" + UUID.randomUUID() + "/resolutions/counts").andExpect(status().isNotFound());
	}

}
