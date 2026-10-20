package com.kpi.studentcouncil.crm.tasks.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.kpi.studentcouncil.crm.tasks.TaskEvent;
import com.kpi.studentcouncil.crmtest.SharedAdmin;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, TaskIntegrationTests.EventsConfig.class })
class TaskIntegrationTests {

	private static final String PASSWORD = "Sup3r-secret-pw";

	/** Collects the published task events. */
	static class CollectedEvents {

		final List<TaskEvent> all = new CopyOnWriteArrayList<>();

		@EventListener
		void on(TaskEvent event) {
			all.add(event);
		}

	}

	@TestConfiguration(proxyBeanMethods = false)
	static class EventsConfig {

		@Bean
		CollectedEvents collectedEvents() {
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

	MockHttpSession admin;

	private final List<UUID> spaces = new ArrayList<>();
	private final List<UUID> tasks = new ArrayList<>();
	private final List<UUID> files = new ArrayList<>();
	private final List<UUID> users = new ArrayList<>();
	private final List<UUID> roles = new ArrayList<>();

	@BeforeEach
	void setUp() throws Exception {
		SharedAdmin.ensure(jdbc, encoder);
		admin = signIn(SharedAdmin.LOGIN, SharedAdmin.PASSWORD);
		events.all.clear();
	}

	/** Removes everything this class created, children first (FK order). */
	@AfterEach
	void cleanUp() {
		for (UUID space : spaces) {
			jdbc.update("delete from task_link where task_low in (select id from task where space_id = ?)"
					+ " or task_high in (select id from task where space_id = ?)", space, space);
			jdbc.update("delete from task_label where task_id in (select id from task where space_id = ?)", space);
			jdbc.update("delete from task_attachment where task_id in (select id from task where space_id = ?)", space);
			jdbc.update("delete from task where space_id = ?", space);
			jdbc.update("delete from task_custom_field_option where field_id in"
					+ " (select id from task_custom_field where space_id = ?)", space);
			jdbc.update("delete from task_custom_field where space_id = ?", space);
			jdbc.update("delete from task_space_status where space_id = ?", space);
			jdbc.update("delete from task_space where id = ?", space);
		}
		for (UUID file : files) {
			jdbc.update("delete from file_object where id = ?", file);
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
		tasks.clear();
		files.clear();
		users.clear();
		roles.clear();
	}

	private MockHttpSession signIn(String login, String password) throws Exception {
		MvcResult result = mvc.perform(post("/api/v1/auth/login").with(csrf()).with(request -> {
			request.setRemoteAddr("10.9." + (int) (Math.random() * 250) + "." + (1 + (int) (Math.random() * 250)));
			return request;
		}).contentType(MediaType.APPLICATION_JSON)
				.content("{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}")).andReturn();
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	/** A user holding a role with the given permission keys; returns {userId, session}. */
	private record Account(UUID id, MockHttpSession session) {
	}

	private Account account(String... permissionKeys) throws Exception {
		UUID user = UUID.randomUUID();
		users.add(user);
		jdbc.update("insert into users (id, login, password_hash, status, locale, must_change_password, created_at,"
				+ " updated_at) values (?, ?, ?, 'ACTIVE', 'en', false, now(), now())", user,
				"task-user-" + user.toString().substring(0, 8), encoder.encode(PASSWORD));
		if (permissionKeys.length > 0) {
			UUID role = UUID.randomUUID();
			roles.add(role);
			jdbc.update("insert into role (id, name, is_system, created_at, updated_at) values (?, ?, false, now(),"
					+ " now())", role, "task-role-" + role);
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

	private static UUID id(ResultActions r) throws Exception {
		return UUID.fromString(JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id"));
	}

	private static String body(ResultActions r) throws Exception {
		return r.andReturn().getResponse().getContentAsString();
	}

	private UUID space() throws Exception {
		UUID id = id(send(admin, "/spaces", "{\"name\":\"Space " + UUID.randomUUID() + "\",\"general\":true}")
				.andExpect(status().isCreated()));
		spaces.add(id);
		return id;
	}

	private UUID statusId(UUID space, String category) throws Exception {
		List<String> ids = JsonPath.read(body(read(admin, "/spaces/" + space + "/statuses")),
				"$[?(@.category=='" + category + "')].id");
		return UUID.fromString(ids.get(0));
	}

	private UUID field(UUID space, String json) throws Exception {
		return id(send(admin, "/spaces/" + space + "/custom-fields", json).andExpect(status().isCreated()));
	}

	private UUID createTask(MockHttpSession s, UUID space, String extra) throws Exception {
		UUID id = id(send(s, "/tasks", "{\"spaceId\":\"" + space + "\",\"title\":\"Task " + UUID.randomUUID() + "\""
				+ extra + "}").andExpect(status().isCreated()));
		tasks.add(id);
		return id;
	}

	private UUID createTask(MockHttpSession s, UUID space) throws Exception {
		return createTask(s, space, "");
	}

	private UUID file() {
		UUID id = UUID.randomUUID();
		files.add(id);
		jdbc.update("insert into file_object (id, storage_key, file_name, mime_type, size_bytes, uploaded_by, created_at,"
				+ " updated_at) values (?, ?, 'plan.pdf', 'application/pdf', 10, ?, now(), now())", id,
				"test-" + id, UUID.randomUUID());
		return id;
	}

	private long audit(String type, UUID id, String action) {
		return jdbc.queryForObject(
				"select count(*) from audit_log where entity_type = ? and entity_id = ? and action = ?", Long.class,
				type, id, action);
	}

	@Test
	void createWithDefaultsAndRichDescription() throws Exception {
		UUID space = space();
		Account author = account("tasks.view", "tasks.create");
		UUID task = createTask(author.session(), space, ",\"description\":{\"type\":\"doc\",\"content\":[]},"
				+ "\"labels\":[\" Urgent \",\"urgent\",\"Finance\"],\"dueDate\":\"2026-12-01\",\"priority\":\"HIGH\"");

		read(admin, "/tasks/" + task).andExpect(status().isOk())
				.andExpect(jsonPath("$.spaceId").value(space.toString()))
				.andExpect(jsonPath("$.statusId").value(statusId(space, "TODO").toString()))
				.andExpect(jsonPath("$.priority").value("HIGH"))
				.andExpect(jsonPath("$.dueDate").value("2026-12-01"))
				.andExpect(jsonPath("$.description.type").value("doc"))
				.andExpect(jsonPath("$.labels[0]").value("finance")).andExpect(jsonPath("$.labels", hasSize(2)))
				.andExpect(jsonPath("$.authorId").value(author.id().toString()))
				.andExpect(jsonPath("$.archived").value(false)).andExpect(jsonPath("$.relatedTaskIds", hasSize(0)));
		assertThat(audit("Task", task, "CREATE")).isEqualTo(1);
		assertThat(jdbc.queryForObject("select after_state ->> 'title' from audit_log where entity_id = ? and action ="
				+ " 'CREATE'", String.class, task)).startsWith("Task ");
		assertThat(events.all).anySatisfy(e -> {
			assertThat(e.kind()).isEqualTo(TaskEvent.Kind.CREATED);
			assertThat(e.id()).isEqualTo(task);
			assertThat(e.authorId()).isEqualTo(author.id());
		});
		send(admin, "/tasks", "{\"spaceId\":\"" + space + "\",\"title\":\"  \"}").andExpect(status().isBadRequest());
	}

	@Test
	void permissionsAreEnforced() throws Exception {
		UUID space = space();
		Account plain = account();
		Account viewer = account("tasks.view");
		Account creator = account("tasks.view", "tasks.create");
		Account archiver = account("tasks.view", "tasks.archive");
		UUID task = createTask(creator.session(), space);

		mvc.perform(get("/api/v1/tasks")).andExpect(status().isUnauthorized());
		read(plain.session(), "/tasks").andExpect(status().isForbidden());
		read(plain.session(), "/tasks/" + task).andExpect(status().isForbidden());
		read(viewer.session(), "/tasks").andExpect(status().isOk());
		read(viewer.session(), "/tasks/" + task).andExpect(status().isOk());

		String create = "{\"spaceId\":\"" + space + "\",\"title\":\"X\"}";
		send(viewer.session(), "/tasks", create).andExpect(status().isForbidden());
		send(plain.session(), "/tasks", create).andExpect(status().isForbidden());
		send(viewer.session(), "/tasks/" + task + "/archive", "").andExpect(status().isForbidden());
		send(creator.session(), "/tasks/" + task + "/archive", "").andExpect(status().isForbidden());
		send(viewer.session(), "/tasks/" + task + "/restore", "").andExpect(status().isForbidden());
		send(archiver.session(), "/tasks/" + task + "/archive", "").andExpect(status().isOk());
	}

	@Test
	void statusMustBelongToTheTasksSpace() throws Exception {
		UUID space = space();
		UUID other = space();
		UUID foreign = statusId(other, "TODO");
		UUID done = statusId(space, "DONE");

		send(admin, "/tasks", "{\"spaceId\":\"" + space + "\",\"title\":\"X\",\"statusId\":\"" + foreign + "\"}")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("TASK_STATUS_NOT_IN_SPACE"));
		UUID task = createTask(admin, space, ",\"statusId\":\"" + done + "\"");
		read(admin, "/tasks/" + task).andExpect(jsonPath("$.statusId").value(done.toString()));

		send(admin, "/tasks/" + task + "/status", "{\"statusId\":\"" + foreign + "\"}")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("TASK_STATUS_NOT_IN_SPACE"));
		UUID progress = statusId(space, "IN_PROGRESS");
		send(admin, "/tasks/" + task + "/status", "{\"statusId\":\"" + progress + "\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.statusId").value(progress.toString()));
		assertThat(audit("Task", task, "UPDATE")).isEqualTo(1);
		assertThat(jdbc.queryForObject("select before_state ->> 'statusId' from audit_log where entity_id = ? and"
				+ " action = 'UPDATE'", String.class, task)).isEqualTo(done.toString());
		send(admin, "/tasks", "{\"spaceId\":\"" + UUID.randomUUID() + "\",\"title\":\"X\"}")
				.andExpect(status().isNotFound());
	}

	@Test
	void customFieldValuesAreValidatedByTypeAgainstTheSpace() throws Exception {
		UUID space = space();
		UUID text = field(space, "{\"name\":\"Note\",\"type\":\"TEXT\",\"required\":true}");
		UUID number = field(space, "{\"name\":\"Hours\",\"type\":\"NUMBER\"}");
		UUID date = field(space, "{\"name\":\"Review\",\"type\":\"DATE\"}");
		UUID select = field(space, "{\"name\":\"Size\",\"type\":\"SELECT\",\"options\":[\"S\",\"M\"]}");
		UUID person = field(space, "{\"name\":\"Reviewer\",\"type\":\"PERSON\"}");
		Account reviewer = account();
		String head = "{\"spaceId\":\"" + space + "\",\"title\":\"CF\",\"customFieldValues\":";

		send(admin, "/tasks", head + "{}}").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("CUSTOM_FIELD_REQUIRED"));
		send(admin, "/tasks", head + "{\"" + text + "\":\"ok\",\"" + number + "\":\"12\"}}")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("CUSTOM_FIELD_VALUE_INVALID"));
		send(admin, "/tasks", head + "{\"" + text + "\":\"ok\",\"" + date + "\":\"tomorrow\"}}")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("CUSTOM_FIELD_VALUE_INVALID"));
		send(admin, "/tasks", head + "{\"" + text + "\":\"ok\",\"" + select + "\":\"XL\"}}")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("CUSTOM_FIELD_VALUE_INVALID"));
		send(admin, "/tasks", head + "{\"" + text + "\":\"ok\",\"" + person + "\":\"" + UUID.randomUUID() + "\"}}")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("CUSTOM_FIELD_VALUE_INVALID"));
		send(admin, "/tasks", head + "{\"" + text + "\":\"ok\",\"" + UUID.randomUUID() + "\":\"x\"}}")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("CUSTOM_FIELD_UNKNOWN"));

		UUID task = id(send(admin, "/tasks", head + "{\"" + text + "\":\"ok\",\"" + number + "\":3.5,\"" + date
				+ "\":\"2026-11-30\",\"" + select + "\":\"M\",\"" + person + "\":\"" + reviewer.id() + "\"}}")
				.andExpect(status().isCreated()).andExpect(jsonPath("$.customFieldValues['" + number + "']").value(3.5))
				.andExpect(jsonPath("$.customFieldValues['" + person + "']").value(reviewer.id().toString())));
		tasks.add(task);

		putJson(admin, "/tasks/" + task, "{\"title\":\"CF\",\"customFieldValues\":{\"" + text + "\":\"changed\"}}")
				.andExpect(status().isOk()).andExpect(jsonPath("$.customFieldValues['" + text + "']").value("changed"))
				.andExpect(jsonPath("$.customFieldValues['" + number + "']").doesNotExist());
		putJson(admin, "/tasks/" + task, "{\"title\":\"CF\",\"customFieldValues\":{}}")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("CUSTOM_FIELD_REQUIRED"));
	}

	@Test
	void relatedTasksAreSymmetricAndThereIsNoHierarchy() throws Exception {
		UUID space = space();
		UUID a = createTask(admin, space);
		UUID b = createTask(admin, space);
		UUID c = createTask(admin, space);

		putJson(admin, "/tasks/" + a, "{\"title\":\"A\",\"relatedTaskIds\":[\"" + b + "\",\"" + c + "\"]}")
				.andExpect(status().isOk()).andExpect(jsonPath("$.relatedTaskIds", hasSize(2)));
		read(admin, "/tasks/" + b).andExpect(jsonPath("$.relatedTaskIds[0]").value(a.toString()))
				.andExpect(jsonPath("$.relatedTaskIds", hasSize(1))).andExpect(jsonPath("$.parentId").doesNotExist())
				.andExpect(jsonPath("$.subtasks").doesNotExist());
		assertThat(audit("Task", b, "UPDATE")).isEqualTo(1);
		assertThat(audit("Task", c, "UPDATE")).isEqualTo(1);

		putJson(admin, "/tasks/" + a, "{\"title\":\"A\",\"relatedTaskIds\":[\"" + c + "\"]}")
				.andExpect(status().isOk()).andExpect(jsonPath("$.relatedTaskIds", hasSize(1)));
		read(admin, "/tasks/" + b).andExpect(jsonPath("$.relatedTaskIds", hasSize(0)));
		read(admin, "/tasks?spaceId=" + space).andExpect(jsonPath("$.content[?(@.id=='" + c + "')].relatedCount").value(1));

		putJson(admin, "/tasks/" + a, "{\"title\":\"A\",\"relatedTaskIds\":[\"" + a + "\"]}")
				.andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("TASK_RELATED_SELF"));
		putJson(admin, "/tasks/" + a, "{\"title\":\"A\",\"relatedTaskIds\":[\"" + UUID.randomUUID() + "\"]}")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("TASK_RELATED_INVALID"));
		assertThat(jdbc.queryForObject("select count(*) from task_link where task_low = task_high", Long.class))
				.isZero();
	}

	@Test
	void onlyTheAuthorOrAnAdminCanEdit() throws Exception {
		UUID space = space();
		Account author = account("tasks.view", "tasks.create");
		Account other = account("tasks.view", "tasks.create", "tasks.edit.any", "tasks.archive");
		UUID task = createTask(author.session(), space);
		String edit = "{\"title\":\"Edited\"}";

		putJson(other.session(), "/tasks/" + task, edit).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("TASK_EDIT_FORBIDDEN"));
		send(other.session(), "/tasks/" + task + "/status", "{\"statusId\":\"" + statusId(space, "DONE") + "\"}")
				.andExpect(status().isForbidden());
		read(admin, "/tasks/" + task).andExpect(jsonPath("$.title").value(org.hamcrest.Matchers.startsWith("Task ")));

		putJson(author.session(), "/tasks/" + task, edit).andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Edited"));
		putJson(admin, "/tasks/" + task, "{\"title\":\"By admin\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("By admin")).andExpect(jsonPath("$.authorId").value(author.id().toString()));
		send(author.session(), "/tasks/" + task + "/status", "{\"statusId\":\"" + statusId(space, "DONE") + "\"}")
				.andExpect(status().isOk());

		assertThat(audit("Task", task, "UPDATE")).isEqualTo(3);
		assertThat(jdbc.queryForObject("select before_state ->> 'title' from audit_log where entity_id = ? and action"
				+ " = 'UPDATE' and after_state ->> 'title' = 'By admin' and before_state ->> 'title' <> 'By admin'",
				String.class, task)).isEqualTo("Edited");
	}

	@Test
	void archiveAndRestoreHideAndShowTasksAndAuditThem() throws Exception {
		UUID space = space();
		Account archiver = account("tasks.view", "tasks.create", "tasks.archive");
		UUID task = createTask(archiver.session(), space);
		UUID kept = createTask(archiver.session(), space);

		send(archiver.session(), "/tasks/" + task + "/archive", "").andExpect(status().isOk())
				.andExpect(jsonPath("$.archived").value(true));
		send(archiver.session(), "/tasks/" + task + "/archive", "").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ALREADY_ARCHIVED"));
		assertThat(jdbc.queryForObject("select count(*) from task where id = ?", Long.class, task)).isEqualTo(1);

		String listed = body(read(admin, "/tasks?spaceId=" + space));
		assertThat((List<?>) JsonPath.read(listed, "$.content[?(@.id=='" + task + "')]")).isEmpty();
		assertThat((List<?>) JsonPath.read(listed, "$.content[?(@.id=='" + kept + "')]")).hasSize(1);
		String all = body(read(admin, "/tasks?spaceId=" + space + "&includeArchived=true"));
		assertThat((List<?>) JsonPath.read(all, "$.content[?(@.id=='" + task + "')]")).hasSize(1);
		read(admin, "/tasks/" + task).andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(true));

		putJson(admin, "/tasks/" + task, "{\"title\":\"Edit\"}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TASK_ARCHIVED"));
		send(admin, "/tasks/" + task + "/status", "{\"statusId\":\"" + statusId(space, "DONE") + "\"}")
				.andExpect(status().isConflict());
		send(admin, "/tasks/" + kept + "/restore", "").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("NOT_ARCHIVED"));

		send(archiver.session(), "/tasks/" + task + "/restore", "").andExpect(status().isOk())
				.andExpect(jsonPath("$.archived").value(false));
		assertThat(audit("Task", task, "ARCHIVE")).isEqualTo(1);
		assertThat(audit("Task", task, "RESTORE")).isEqualTo(1);
		assertThat(events.all).extracting(TaskEvent::kind).contains(TaskEvent.Kind.ARCHIVED, TaskEvent.Kind.RESTORED);

		send(admin, "/tasks/" + task + "/archive", "").andExpect(status().isOk());
		send(admin, "/spaces/" + space + "/archive", "").andExpect(status().isOk());
		send(admin, "/tasks/" + task + "/restore", "").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SPACE_ARCHIVED"));
		send(admin, "/tasks", "{\"spaceId\":\"" + space + "\",\"title\":\"X\"}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SPACE_ARCHIVED"));
	}

	@Test
	void listFiltersAndPagination() throws Exception {
		UUID space = space();
		UUID otherSpace = space();
		UUID done = statusId(space, "DONE");
		UUID t1 = createTask(admin, space, ",\"labels\":[\"alpha\"],\"dueDate\":\"2026-11-01\"");
		UUID t2 = createTask(admin, space, ",\"labels\":[\"beta\",\"gamma\"],\"dueDate\":\"2026-11-15\"");
		UUID t3 = createTask(admin, space, ",\"labels\":[\"beta\"],\"dueDate\":\"2026-12-01\",\"statusId\":\"" + done + "\"");
		createTask(admin, otherSpace, ",\"labels\":[\"alpha\"]");

		assertThat(ids(read(admin, "/tasks?spaceId=" + space + "&size=50"))).containsExactlyInAnyOrder(t1, t2, t3);
		assertThat(ids(read(admin, "/tasks?spaceId=" + space + "&statusId=" + done))).containsExactly(t3);
		assertThat(ids(read(admin, "/tasks?spaceId=" + space + "&labels=beta"))).containsExactlyInAnyOrder(t2, t3);
		assertThat(ids(read(admin, "/tasks?spaceId=" + space + "&labels=Alpha&labels=gamma")))
				.containsExactlyInAnyOrder(t1, t2);
		assertThat(ids(read(admin, "/tasks?spaceId=" + space + "&dueFrom=2026-11-10&dueTo=2026-11-30")))
				.containsExactly(t2);
		assertThat(ids(read(admin, "/tasks?spaceId=" + space + "&dueFrom=2026-11-15"))).containsExactlyInAnyOrder(t2, t3);
		assertThat(ids(read(admin, "/tasks?spaceId=" + space + "&labels=beta&dueTo=2026-11-30"))).containsExactly(t2);

		read(admin, "/tasks?spaceId=" + space + "&size=2&page=0&sort=dueDate,asc")
				.andExpect(jsonPath("$.content", hasSize(2))).andExpect(jsonPath("$.content[0].id").value(t1.toString()))
				.andExpect(jsonPath("$.totalElements").value(3));
		read(admin, "/tasks?spaceId=" + space + "&size=2&page=1&sort=dueDate,asc")
				.andExpect(jsonPath("$.content", hasSize(1))).andExpect(jsonPath("$.content[0].id").value(t3.toString()));
		read(admin, "/tasks?sort=bogus,asc").andExpect(status().isUnprocessableContent());
	}

	private static List<UUID> ids(ResultActions r) throws Exception {
		List<String> raw = JsonPath.read(body(r), "$.content[*].id");
		return raw.stream().map(UUID::fromString).toList();
	}

	@Test
	void attachmentsAreFilesOfTheFilesModule() throws Exception {
		UUID space = space();
		UUID file = file();
		UUID task = createTask(admin, space, ",\"attachmentIds\":[\"" + file + "\",\"" + file + "\"]");
		read(admin, "/tasks/" + task).andExpect(jsonPath("$.attachments", hasSize(1)))
				.andExpect(jsonPath("$.attachments[0].id").value(file.toString()))
				.andExpect(jsonPath("$.attachments[0].fileName").value("plan.pdf"))
				.andExpect(jsonPath("$.attachments[0].mimeType").value("application/pdf"));
		read(admin, "/tasks?spaceId=" + space).andExpect(jsonPath("$.content[0].attachmentCount").value(1));

		send(admin, "/tasks", "{\"spaceId\":\"" + space + "\",\"title\":\"X\",\"attachmentIds\":[\"" + UUID.randomUUID()
				+ "\"]}").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("TASK_ATTACHMENT_INVALID"));
		putJson(admin, "/tasks/" + task, "{\"title\":\"X\",\"attachmentIds\":[]}").andExpect(status().isOk())
				.andExpect(jsonPath("$.attachments", hasSize(0)));
	}

	@Test
	void aStatusInUseCannotBeArchivedWithoutMappingItsTasksToAnother() throws Exception {
		UUID space = space();
		UUID progress = statusId(space, "IN_PROGRESS");
		UUID todo = statusId(space, "TODO");
		UUID active = createTask(admin, space, ",\"statusId\":\"" + progress + "\"");
		UUID hidden = createTask(admin, space, ",\"statusId\":\"" + progress + "\"");
		send(admin, "/tasks/" + hidden + "/archive", "").andExpect(status().isOk());

		send(admin, "/spaces/" + space + "/statuses/" + progress + "/archive", "").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SPACE_STATUS_IN_USE"));
		read(admin, "/spaces/" + space + "/statuses").andExpect(jsonPath("$", hasSize(4)));

		send(admin, "/spaces/" + space + "/statuses/" + progress + "/archive",
				"{\"replacementStatusId\":\"" + todo + "\"}").andExpect(status().isOk());
		read(admin, "/tasks/" + active).andExpect(jsonPath("$.statusId").value(todo.toString()));
		read(admin, "/tasks/" + hidden).andExpect(jsonPath("$.statusId").value(todo.toString()));
		assertThat(jdbc.queryForObject("select before_state ->> 'statusId' from audit_log where entity_id = ? and action"
				+ " = 'UPDATE'", String.class, active)).isEqualTo(progress.toString());
		assertThat(events.all).anySatisfy(e -> {
			assertThat(e.id()).isEqualTo(active);
			assertThat(e.statusId()).isEqualTo(todo);
		});

		// a status without tasks is archived without a replacement
		UUID extra = id(send(admin, "/spaces/" + space + "/statuses", "{\"name\":\"Review\",\"category\":\"IN_PROGRESS\"}")
				.andExpect(status().isCreated()));
		send(admin, "/spaces/" + space + "/statuses/" + extra + "/archive", "").andExpect(status().isOk());
		send(admin, "/tasks/" + active + "/status", "{\"statusId\":\"" + extra + "\"}")
				.andExpect(status().isUnprocessableContent());
	}

}
