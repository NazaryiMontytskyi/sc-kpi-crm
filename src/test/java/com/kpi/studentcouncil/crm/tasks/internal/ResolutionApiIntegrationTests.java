package com.kpi.studentcouncil.crm.tasks.internal;

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
import java.util.concurrent.CopyOnWriteArrayList;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
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
import com.kpi.studentcouncil.crm.tasks.ResolutionEvent;

/** REST API of resolutions: permission and actor matrix, issue directions, validation, lists and events. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, ResolutionApiIntegrationTests.EventsConfig.class })
class ResolutionApiIntegrationTests {

	private static final String PASSWORD = "Sup3r-secret-pw";

	static class CollectedEvents {

		final List<ResolutionEvent> all = new CopyOnWriteArrayList<>();

		@EventListener
		void on(ResolutionEvent event) {
			all.add(event);
		}

	}

	@TestConfiguration(proxyBeanMethods = false)
	static class EventsConfig {

		@Bean
		CollectedEvents collectedResolutionEvents() {
			return new CollectedEvents();
		}

	}

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PasswordEncoder encoder;

	@Autowired
	AccessService access;

	@Autowired
	CollectedEvents events;

	private final List<UUID> users = new ArrayList<>();
	private final List<UUID> roles = new ArrayList<>();
	private UUID space;
	private UUID task;

	private record Account(UUID id, MockHttpSession session) {
	}

	@BeforeEach
	void setUp() {
		events.all.clear();
		space = UUID.randomUUID();
		jdbc.update("insert into task_space (id, name, is_general, created_at, updated_at) values (?, ?, true, now(),"
				+ " now())", space, "Resolution API " + space);
		UUID statusId = UUID.randomUUID();
		jdbc.update("insert into task_space_status (id, space_id, name, category, position, created_at, updated_at)"
				+ " values (?, ?, 'Todo', 'TODO', 0, now(), now())", statusId, space);
		task = UUID.randomUUID();
		jdbc.update("insert into task (id, space_id, title, status_id, priority, created_at, updated_at) values (?, ?,"
				+ " 'Fixture', ?, 'NORMAL', now(), now())", task, space, statusId);
	}

	@AfterEach
	void cleanUp() {
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

	private Account account(String status, String... permissionKeys) throws Exception {
		UUID user = UUID.randomUUID();
		users.add(user);
		String login = "res-api-" + user.toString().substring(0, 8);
		jdbc.update("insert into users (id, login, password_hash, status, locale, must_change_password, created_at,"
				+ " updated_at) values (?, ?, ?, ?, 'en', false, now(), now())", user, login,
				encoder.encode(PASSWORD), status);
		if (permissionKeys.length > 0) {
			UUID role = UUID.randomUUID();
			roles.add(role);
			jdbc.update("insert into role (id, name, is_system, created_at, updated_at) values (?, ?, false, now(),"
					+ " now())", role, "res-role-" + role);
			for (String key : permissionKeys) {
				jdbc.update("insert into role_permission (role_id, permission_key) values (?, ?)", role, key);
			}
			access.grantRole(user, role);
		}
		if (!"ACTIVE".equals(status)) {
			return new Account(user, null);
		}
		MvcResult result = mvc.perform(post("/api/v1/auth/login").with(csrf()).with(request -> {
			request.setRemoteAddr("10.8." + (int) (Math.random() * 250) + "." + (1 + (int) (Math.random() * 250)));
			return request;
		}).contentType(MediaType.APPLICATION_JSON)
				.content("{\"login\":\"" + login + "\",\"password\":\"" + PASSWORD + "\"}")).andReturn();
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		return new Account(user, (MockHttpSession) result.getRequest().getSession(false));
	}

	/** An active user who may issue resolutions and view tasks. */
	private Account issuer() throws Exception {
		return account("ACTIVE", "tasks.view", "resolutions.create");
	}

	/** An active user with view access only. */
	private Account viewer() throws Exception {
		return account("ACTIVE", "tasks.view");
	}

	private ResultActions send(Account who, String path, String json) throws Exception {
		return mvc.perform(post("/api/v1" + path).with(csrf()).session(who.session())
				.header(HttpHeaders.ACCEPT_LANGUAGE, "en").contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions read(Account who, String path) throws Exception {
		return mvc.perform(get("/api/v1" + path).session(who.session()).header(HttpHeaders.ACCEPT_LANGUAGE, "en"));
	}

	private static String issueJson(UUID executor, List<UUID> co, String instruction, LocalDate due) {
		String coJson = co.stream().map(u -> "\"" + u + "\"").reduce((a, b) -> a + "," + b).orElse("");
		return "{\"executorId\":\"" + executor + "\",\"coExecutorIds\":[" + coJson + "],\"instruction\":\""
				+ instruction + "\",\"dueDate\":\"" + due + "\"}";
	}

	private UUID issue(Account author, UUID executor) throws Exception {
		ResultActions r = send(author, "/tasks/" + task + "/resolutions",
				issueJson(executor, List.of(), "Do it", LocalDate.now().plusDays(7))).andExpect(status().isCreated());
		return UUID.fromString(JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id"));
	}

	private void expectCode(ResultActions r, int status, String code) throws Exception {
		r.andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code));
	}

	@Test
	void userWithoutResolutionsCreateGetsForbiddenOnIssue() throws Exception {
		Account noPermission = viewer();
		Account executor = viewer();
		send(noPermission, "/tasks/" + task + "/resolutions",
				issueJson(executor.id(), List.of(), "Do it", LocalDate.now().plusDays(3)))
				.andExpect(status().isForbidden());
		assertThat(jdbc.queryForObject("select count(*) from resolution where task_id = ?", Long.class, task))
				.isZero();
	}

	@Test
	void issueWorksTopDownHorizontallyAndBottomUpWithoutHierarchyCheck() throws Exception {
		Account chair = issuer();
		Account deputy = issuer();
		Account member = issuer();
		LocalDate due = LocalDate.now().plusDays(5);
		// top-down: chair -> member
		send(chair, "/tasks/" + task + "/resolutions", issueJson(member.id(), List.of(deputy.id()), "Top-down", due))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.state").value("NEW"))
				.andExpect(jsonPath("$.authorId").value(chair.id().toString()))
				.andExpect(jsonPath("$.executorId").value(member.id().toString()))
				.andExpect(jsonPath("$.coExecutorIds[0]").value(deputy.id().toString()))
				.andExpect(jsonPath("$.history", hasSize(1)));
		// horizontal: deputy -> deputy's peer (chair here stands for any peer)
		send(deputy, "/tasks/" + task + "/resolutions", issueJson(chair.id(), List.of(), "Horizontal", due))
				.andExpect(status().isCreated());
		// bottom-up: member -> chair
		send(member, "/tasks/" + task + "/resolutions", issueJson(chair.id(), List.of(), "Bottom-up", due))
				.andExpect(status().isCreated());

		read(chair, "/tasks/" + task + "/resolutions").andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(3)));
		assertThat(events.all).extracting(ResolutionEvent::kind).containsOnly(ResolutionEvent.Kind.ISSUED);
		assertThat(events.all).hasSize(3);
		ResolutionEvent first = events.all.stream().filter(e -> e.authorId().equals(chair.id())).findFirst()
				.orElseThrow();
		assertThat(first.executorId()).isEqualTo(member.id());
		assertThat(first.coExecutorIds()).containsExactly(deputy.id());
		assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_type = 'Resolution' and"
				+ " action = 'CREATE' and entity_id in (select id from resolution where task_id = ?)", Long.class,
				task)).isEqualTo(3);
	}

	@Test
	void issueValidatesExecutorsDueDateAndTask() throws Exception {
		Account author = issuer();
		Account ok = viewer();
		Account blocked = account("BLOCKED");
		String path = "/tasks/" + task + "/resolutions";
		LocalDate due = LocalDate.now().plusDays(2);

		expectCode(send(author, path, issueJson(blocked.id(), List.of(), "x", due)), 422,
				"RESOLUTION_EXECUTOR_INVALID");
		expectCode(send(author, path, issueJson(ok.id(), List.of(blocked.id()), "x", due)), 422,
				"RESOLUTION_CO_EXECUTOR_INVALID");
		expectCode(send(author, path, issueJson(UUID.randomUUID(), List.of(), "x", due)), 422,
				"RESOLUTION_EXECUTOR_INVALID");
		expectCode(send(author, path, issueJson(ok.id(), List.of(), "x", LocalDate.now().minusDays(2))), 422,
				"RESOLUTION_DUE_DATE_IN_PAST");
		send(author, path, issueJson(ok.id(), List.of(), "   ", due)).andExpect(status().isBadRequest());
		send(author, "/tasks/" + UUID.randomUUID() + "/resolutions", issueJson(ok.id(), List.of(), "x", due))
				.andExpect(status().isNotFound());
		// due today is accepted
		send(author, path, issueJson(ok.id(), List.of(), "x", LocalDate.now(java.time.ZoneId.of("Europe/Kyiv"))))
				.andExpect(status().isCreated());
	}

	@Test
	void aTaskCanHaveSeveralResolutions() throws Exception {
		Account author = issuer();
		Account e1 = viewer();
		Account e2 = viewer();
		issue(author, e1.id());
		issue(author, e2.id());
		issue(author, e1.id());
		read(author, "/tasks/" + task + "/resolutions").andExpect(jsonPath("$.content", hasSize(3)))
				.andExpect(jsonPath("$.totalElements").value(3));
	}

	@Test
	void actorMatrixOnlyExecutorStartsAndSubmitsOnlyAuthorAcceptsReworksAndCancels() throws Exception {
		Account author = issuer();
		Account executor = viewer();
		Account stranger = viewer();
		UUID id = issue(author, executor.id());
		String base = "/resolutions/" + id;

		// non-executor (incl. the author) cannot start
		expectCode(send(stranger, base + "/start", ""), 403, "RESOLUTION_NOT_EXECUTOR");
		expectCode(send(author, base + "/start", ""), 403, "RESOLUTION_NOT_EXECUTOR");
		send(executor, base + "/start", "").andExpect(status().isOk()).andExpect(jsonPath("$.state").value("IN_PROGRESS"));

		// submit: needs the executor and a completion note
		expectCode(send(stranger, base + "/submit", "{\"completionNote\":\"done\"}"), 403, "RESOLUTION_NOT_EXECUTOR");
		expectCode(send(author, base + "/submit", "{\"completionNote\":\"done\"}"), 403, "RESOLUTION_NOT_EXECUTOR");
		send(executor, base + "/submit", "{\"completionNote\":\"  \"}").andExpect(status().isBadRequest());
		send(executor, base + "/submit", "{}").andExpect(status().isBadRequest());
		send(executor, base + "/submit", "{\"completionNote\":\"All done\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.state").value("SUBMITTED")).andExpect(jsonPath("$.completionNote").value("All done"));

		// accept / rework: only the author
		expectCode(send(executor, base + "/accept", ""), 403, "RESOLUTION_NOT_AUTHOR");
		expectCode(send(stranger, base + "/accept", ""), 403, "RESOLUTION_NOT_AUTHOR");
		expectCode(send(executor, base + "/rework", "{\"comment\":\"redo\"}"), 403, "RESOLUTION_NOT_AUTHOR");
		send(author, base + "/rework", "{\"comment\":\"\"}").andExpect(status().isBadRequest());
		send(author, base + "/rework", "{\"comment\":\"Add numbers\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.state").value("REWORK"));

		// executor resumes and resubmits, author accepts
		expectCode(send(author, base + "/resume", ""), 403, "RESOLUTION_NOT_EXECUTOR");
		send(executor, base + "/resume", "").andExpect(jsonPath("$.state").value("IN_PROGRESS"));
		send(executor, base + "/submit", "{\"completionNote\":\"Numbers added\"}").andExpect(status().isOk());
		send(author, base + "/accept", "").andExpect(status().isOk()).andExpect(jsonPath("$.state").value("ACCEPTED"))
				.andExpect(jsonPath("$.history", hasSize(7)));

		// final: nothing else works
		expectCode(send(author, base + "/cancel", ""), 422, "RESOLUTION_TRANSITION_NOT_ALLOWED");
		expectCode(send(author, base + "/accept", ""), 422, "RESOLUTION_TRANSITION_NOT_ALLOWED");

		assertThat(events.all).extracting(ResolutionEvent::kind).containsExactly(ResolutionEvent.Kind.ISSUED,
				ResolutionEvent.Kind.ACCEPTED, ResolutionEvent.Kind.SUBMITTED, ResolutionEvent.Kind.REWORKED,
				ResolutionEvent.Kind.ACCEPTED, ResolutionEvent.Kind.SUBMITTED, ResolutionEvent.Kind.CLOSED);
		assertThat(events.all).allSatisfy(e -> {
			assertThat(e.authorId()).isEqualTo(author.id());
			assertThat(e.executorId()).isEqualTo(executor.id());
		});
	}

	@Test
	void cancelIsAuthorOnlyAndCascadesToNonFinalDescendants() throws Exception {
		Account author = issuer();
		Account executor = viewer();
		Account stranger = viewer();
		UUID root = issue(author, executor.id());
		// a redirected chain does not exist via API yet (INC-034): insert a child directly
		UUID child = UUID.randomUUID();
		jdbc.update("insert into resolution (id, task_id, author_id, executor_id, instruction, due_date,"
				+ " parent_resolution_id, state, created_at, updated_at) values (?, ?, ?, ?, 'child', ?, ?, 'IN_PROGRESS',"
				+ " now(), now())", child, task, executor.id(), stranger.id(), LocalDate.now().plusDays(3), root);
		jdbc.update("insert into resolution_history (resolution_id, position, actor_id, from_state, to_state,"
				+ " occurred_at) values (?, 0, ?, null, 'NEW', now())", child, executor.id());

		expectCode(send(executor, "/resolutions/" + root + "/cancel", ""), 403, "RESOLUTION_NOT_AUTHOR");
		expectCode(send(stranger, "/resolutions/" + root + "/cancel", "{\"comment\":\"x\"}"), 403,
				"RESOLUTION_NOT_AUTHOR");
		send(author, "/resolutions/" + root + "/cancel", "{\"comment\":\"No longer needed\"}")
				.andExpect(status().isOk()).andExpect(jsonPath("$.state").value("CANCELED"));

		assertThat(jdbc.queryForObject("select state from resolution where id = ?", String.class, child))
				.isEqualTo("CANCELED");
		assertThat(events.all).extracting(ResolutionEvent::kind).containsExactlyInAnyOrder(
				ResolutionEvent.Kind.ISSUED, ResolutionEvent.Kind.CANCELED, ResolutionEvent.Kind.CANCELED);
		jdbc.update("update resolution set parent_resolution_id = null where task_id = ?", task);
	}

	@Test
	void listsFilterByTaskExecutorAuthorStateAndMine() throws Exception {
		Account a1 = issuer();
		Account a2 = issuer();
		Account e1 = viewer();
		Account e2 = viewer();
		UUID r1 = issue(a1, e1.id());
		UUID r2 = issue(a2, e2.id());
		send(e1, "/resolutions/" + r1 + "/start", "").andExpect(status().isOk());

		read(a1, "/resolutions?taskId=" + task).andExpect(jsonPath("$.content", hasSize(2)));
		read(a1, "/resolutions?taskId=" + task + "&executorId=" + e2.id()).andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].id").value(r2.toString()));
		read(a1, "/resolutions?taskId=" + task + "&authorId=" + a1.id()).andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].id").value(r1.toString()));
		read(a1, "/resolutions?taskId=" + task + "&state=IN_PROGRESS").andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].id").value(r1.toString()));
		read(e2, "/resolutions?mine=true").andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].id").value(r2.toString())).andExpect(jsonPath("$.content[0].overdue").value(false));
		read(a1, "/resolutions?mine=true").andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].id").value(r1.toString()));
		read(a1, "/resolutions?sort=bogus").andExpect(status().isUnprocessableEntity());
		read(a1, "/resolutions/" + r1).andExpect(status().isOk()).andExpect(jsonPath("$.history", hasSize(2)))
				.andExpect(jsonPath("$.history[1].to").value("IN_PROGRESS"));
		read(a1, "/resolutions/" + UUID.randomUUID()).andExpect(status().isNotFound());
	}

}
