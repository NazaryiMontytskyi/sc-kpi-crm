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

/** The personal dashboard: counts equal the personal lists, only the caller's data, permission enforced. */
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
	private final List<UUID> tasks = new ArrayList<>();
	private UUID fullRole;
	private UUID tasksOnlyRole;
	private UUID space;
	private UUID statusId;
	private Account me;
	private Account other;

	@BeforeEach
	void setUp() throws Exception {
		fullRole = role("tasks.view", "dashboard.view");
		tasksOnlyRole = role("tasks.view");
		space = UUID.randomUUID();
		jdbc.update("insert into task_space (id, name, is_general, created_at, updated_at) values (?, ?, true, now(),"
				+ " now())", space, "Dashboard " + space);
		statusId = UUID.randomUUID();
		jdbc.update("insert into task_space_status (id, space_id, name, category, position, created_at, updated_at)"
				+ " values (?, ?, 'Todo', 'TODO', 0, now(), now())", statusId, space);
		me = account(fullRole);
		other = account(fullRole);
	}

	@AfterEach
	void cleanUp() {
		for (UUID task : tasks) {
			for (UUID id : jdbc.queryForList("select id from resolution where task_id = ?", UUID.class, task)) {
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
		for (UUID r : List.of(fullRole, tasksOnlyRole)) {
			jdbc.update("delete from role_permission where role_id = ?", r);
			jdbc.update("delete from role where id = ?", r);
		}
		for (UUID user : users) {
			jdbc.update("delete from users where id = ?", user);
		}
		users.clear();
		tasks.clear();
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

	private void resolution(String title, UUID author, UUID executor, String state, LocalDate due, boolean archived) {
		UUID taskId = UUID.randomUUID();
		tasks.add(taskId);
		jdbc.update("insert into task (id, space_id, title, status_id, priority, created_at, updated_at) values (?, ?,"
				+ " ?, ?, 'NORMAL', now(), now())", taskId, space, title, statusId);
		jdbc.update("insert into resolution (id, task_id, author_id, executor_id, instruction, due_date, state,"
				+ " created_at, updated_at, archived_at) values (?, ?, ?, ?, 'Do it', ?, ?, now(), now(), "
				+ (archived ? "now()" : "null") + ")", UUID.randomUUID(), taskId, author, executor, due, state);
	}

	/** "me" executes 9 live (1 overdue, 1 SUBMITTED, 6 later ones) and issued 1 SUBMITTED + 1 NEW. */
	private void seed() {
		resolution("a-overdue", other.id(), me.id(), "IN_PROGRESS", TODAY.minusDays(2), false);
		resolution("b-future", other.id(), me.id(), "NEW", TODAY.plusDays(4), false);
		resolution("c-accepted", other.id(), me.id(), "ACCEPTED", TODAY.minusDays(9), false);
		resolution("d-archived", other.id(), me.id(), "NEW", TODAY.minusDays(3), true);
		resolution("e-issued-submitted", me.id(), other.id(), "SUBMITTED", TODAY.plusDays(1), false);
		resolution("f-issued-new", me.id(), other.id(), "NEW", TODAY.plusDays(1), false);
		resolution("g-submitted-by-me", other.id(), me.id(), "SUBMITTED", TODAY.plusDays(2), false);
		for (int i = 0; i < 6; i++) {
			resolution("many-" + i, other.id(), me.id(), "NEW", TODAY.plusDays(10 + i), false);
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
	void countsEqualThePersonalLists() throws Exception {
		seed();
		String base = "/members/" + me.id() + "/resolutions";
		api(me, "/dashboard/me").andExpect(status().isOk())
				.andExpect(jsonPath("$.resolutions.active").value(total(me, base + "?active=true")))
				.andExpect(jsonPath("$.resolutions.awaitingAcceptance")
						.value(total(me, base + "?role=AUTHOR&state=SUBMITTED")))
				.andExpect(jsonPath("$.resolutions.active").value(9))
				.andExpect(jsonPath("$.resolutions.overdue").value(1))
				.andExpect(jsonPath("$.resolutions.awaitingAcceptance").value(1));
		String list = api(me, base + "?active=true&size=50").andReturn().getResponse().getContentAsString();
		List<Boolean> flags = JsonPath.read(list, "$.content[?(@.overdue == true)].overdue");
		assertThat(flags).hasSize(1);
	}

	@Test
	void upcomingIsTheHeadOfMyListLimitedToFive() throws Exception {
		seed();
		String dash = api(me, "/dashboard/me").andReturn().getResponse().getContentAsString();
		List<String> upcoming = JsonPath.read(dash, "$.resolutions.upcoming[*].taskTitle");
		String list = api(me, "/members/" + me.id() + "/resolutions?active=true&size=5").andReturn().getResponse()
				.getContentAsString();
		List<String> head = JsonPath.read(list, "$.content[*].taskTitle");
		assertThat(upcoming).hasSize(5).isEqualTo(head).startsWith("a-overdue");
		api(me, "/dashboard/me").andExpect(jsonPath("$.resolutions.upcoming[0].overdue").value(true));
	}

	@Test
	void showsOnlyTheCallersData() throws Exception {
		seed();
		// "other" executes e and f (2 live, none overdue) and issued exactly one SUBMITTED resolution (g)
		api(other, "/dashboard/me").andExpect(status().isOk()).andExpect(jsonPath("$.resolutions.active").value(2))
				.andExpect(jsonPath("$.resolutions.overdue").value(0))
				.andExpect(jsonPath("$.resolutions.awaitingAcceptance").value(1));
		// an id parameter is ignored
		api(me, "/dashboard/me?userId=" + other.id()).andExpect(jsonPath("$.resolutions.active").value(9));
	}

	@Test
	void requiresDashboardViewAndAuthentication() throws Exception {
		Account noDashboard = account(tasksOnlyRole);
		api(noDashboard, "/dashboard/me").andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/dashboard/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void emptyDashboardForUserWithoutResolutions() throws Exception {
		api(me, "/dashboard/me").andExpect(status().isOk()).andExpect(jsonPath("$.resolutions.active").value(0))
				.andExpect(jsonPath("$.resolutions.upcoming.length()").value(0));
	}

}
