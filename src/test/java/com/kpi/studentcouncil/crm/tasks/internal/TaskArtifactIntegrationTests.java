package com.kpi.studentcouncil.crm.tasks.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.kpi.studentcouncil.crm.TestcontainersConfiguration;
import com.kpi.studentcouncil.crm.access.AccessService;
import com.kpi.studentcouncil.crm.files.FileService;
import com.kpi.studentcouncil.crmtest.SharedAdmin;

/**
 * Artifacts API (INC-075): AR-1..AR-5 and spec §9.7 — files and links, URL validation, the 403 matrix, archive on
 * removal (the file stays), audit before/after and the mirror of legacy attachments. Shares the application context
 * of {@link TaskIntegrationTests} (same imports).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, TaskIntegrationTests.EventsConfig.class })
class TaskArtifactIntegrationTests {

	private static final String PASSWORD = "Sup3r-secret-pw";

	private static final byte[] PDF = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n".getBytes();

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PasswordEncoder encoder;

	@Autowired
	AccessService access;

	@Autowired
	FileService files;

	MockHttpSession admin;

	private final List<UUID> spaces = new ArrayList<>();
	private final List<UUID> fileIds = new ArrayList<>();
	private final List<UUID> users = new ArrayList<>();
	private final List<UUID> roles = new ArrayList<>();

	private record Account(UUID id, MockHttpSession session) {
	}

	@BeforeEach
	void setUp() throws Exception {
		SharedAdmin.ensure(jdbc, encoder);
		admin = signIn(SharedAdmin.LOGIN, SharedAdmin.PASSWORD);
	}

	@AfterEach
	void cleanUp() {
		for (UUID space : spaces) {
			String tasksOf = " where task_id in (select id from task where space_id = ?)";
			jdbc.update("delete from task_artifact" + tasksOf, space);
			jdbc.update("delete from task_attachment" + tasksOf, space);
			jdbc.update("delete from task_assignee" + tasksOf, space);
			jdbc.update("delete from task_label" + tasksOf, space);
			jdbc.update("delete from task where space_id = ?", space);
			jdbc.update("delete from task_space_status where space_id = ?", space);
			jdbc.update("delete from task_space where id = ?", space);
		}
		for (UUID file : fileIds) {
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
		fileIds.clear();
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

	/** An active fake user holding a role with the given permission keys, signed in. */
	private Account account(String... permissionKeys) throws Exception {
		UUID user = UUID.randomUUID();
		users.add(user);
		String login = "artifact-" + user.toString().substring(0, 8);
		jdbc.update("insert into users (id, login, password_hash, status, locale, must_change_password, created_at,"
				+ " updated_at) values (?, ?, ?, 'ACTIVE', 'en', false, now(), now())", user, login,
				encoder.encode(PASSWORD));
		if (permissionKeys.length > 0) {
			UUID role = UUID.randomUUID();
			roles.add(role);
			jdbc.update("insert into role (id, name, is_system, created_at, updated_at) values (?, ?, false, now(),"
					+ " now())", role, "artifact-role-" + role);
			for (String key : permissionKeys) {
				jdbc.update("insert into role_permission (role_id, permission_key) values (?, ?)", role, key);
			}
			access.grantRole(user, role);
		}
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

	private ResultActions remove(MockHttpSession s, UUID task, UUID artifact) throws Exception {
		return mvc.perform(delete("/api/v1/tasks/" + task + "/artifacts/" + artifact).with(csrf()).session(s)
				.header(HttpHeaders.ACCEPT_LANGUAGE, "en"));
	}

	private ResultActions addLink(MockHttpSession s, UUID task, String url, String title) throws Exception {
		String json = "{\"type\":\"LINK\",\"url\":" + quote(url) + (title == null ? "" : ",\"title\":" + quote(title))
				+ "}";
		return send(s, "/tasks/" + task + "/artifacts", json);
	}

	private ResultActions addFile(MockHttpSession s, UUID task, UUID file) throws Exception {
		return send(s, "/tasks/" + task + "/artifacts", "{\"type\":\"FILE\",\"fileId\":\"" + file + "\"}");
	}

	private static String quote(String value) {
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	private static UUID id(ResultActions result) throws Exception {
		return UUID.fromString(JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id"));
	}

	private UUID space() throws Exception {
		UUID id = id(send(admin, "/spaces", "{\"name\":\"Artifacts " + UUID.randomUUID() + "\",\"general\":true}")
				.andExpect(status().isCreated()));
		spaces.add(id);
		return id;
	}

	private UUID createTask(MockHttpSession s, UUID space, String extra) throws Exception {
		return id(send(s, "/tasks", "{\"spaceId\":\"" + space + "\",\"title\":\"Fake task " + UUID.randomUUID() + "\""
				+ extra + "}").andExpect(status().isCreated()));
	}

	/** Uploads a fake PDF through the files API (real storage, ADR-0005 checks). */
	private UUID upload(MockHttpSession s, String name) throws Exception {
		UUID id = id(mvc.perform(multipart("/api/v1/files")
				.file(new MockMultipartFile("file", name, "application/pdf", PDF)).with(csrf()).session(s)
				.header(HttpHeaders.ACCEPT_LANGUAGE, "en")).andExpect(status().isCreated()));
		fileIds.add(id);
		return id;
	}

	private long auditUpdates(UUID task) {
		return jdbc.queryForObject("select count(*) from audit_log where entity_type = 'Task' and entity_id = ? and"
				+ " action = 'UPDATE'", Long.class, task);
	}

	private long artifactRows(UUID task) {
		return jdbc.queryForObject("select count(*) from task_artifact where task_id = ?", Long.class, task);
	}

	// ---- AR-1, AR-3, AR-5, spec §9.7 ----

	@Test
	void aFileAndAnHttpsLinkCanBeAttachedListedAndRemovedWhileTheFileStays() throws Exception {
		UUID space = space();
		Account creator = account("tasks.view", "tasks.create");
		UUID task = createTask(creator.session(), space, "");
		UUID file = upload(creator.session(), "Fake agenda.pdf");

		UUID fileArtifact = id(addFile(creator.session(), task, file).andExpect(status().isCreated())
				.andExpect(jsonPath("$.type").value("FILE")).andExpect(jsonPath("$.name").value("Fake agenda.pdf"))
				.andExpect(jsonPath("$.fileId").value(file.toString()))
				.andExpect(jsonPath("$.fileAvailable").value(true))
				.andExpect(jsonPath("$.mimeType").value("application/pdf"))
				.andExpect(jsonPath("$.size").value(PDF.length))
				.andExpect(jsonPath("$.addedBy").value(creator.id().toString()))
				.andExpect(jsonPath("$.addedAt").isNotEmpty()).andExpect(jsonPath("$.archived").value(false)));
		UUID link = id(addLink(creator.session(), task, "https://example.org/fake-plan", "  Fake plan  ")
				.andExpect(status().isCreated()).andExpect(jsonPath("$.type").value("LINK"))
				.andExpect(jsonPath("$.name").value("Fake plan")).andExpect(jsonPath("$.title").value("Fake plan"))
				.andExpect(jsonPath("$.url").value("https://example.org/fake-plan"))
				.andExpect(jsonPath("$.fileId").doesNotExist()));
		// an untitled link is named by its URL; surrounding whitespace is stripped, the scheme case is kept
		addLink(creator.session(), task, "  HTTP://example.org/untitled ", null).andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("HTTP://example.org/untitled"))
				.andExpect(jsonPath("$.title").doesNotExist());

		// AR-5: the task detail lists the artifacts (one request) with name, type, adder and timestamp
		read(admin, "/tasks/" + task).andExpect(status().isOk()).andExpect(jsonPath("$.artifacts.length()").value(3))
				.andExpect(jsonPath("$.artifacts[0].id").value(fileArtifact.toString()))
				.andExpect(jsonPath("$.artifacts[0].type").value("FILE"))
				.andExpect(jsonPath("$.artifacts[0].name").value("Fake agenda.pdf"))
				.andExpect(jsonPath("$.artifacts[1].type").value("LINK"))
				.andExpect(jsonPath("$.artifacts[1].name").value("Fake plan"))
				.andExpect(jsonPath("$.artifacts[1].addedBy").value(creator.id().toString()))
				.andExpect(jsonPath("$.artifacts[1].addedAt").isNotEmpty())
				// artifacts are separate from the legacy attachments
				.andExpect(jsonPath("$.attachments.length()").value(0));

		// AR-3: removal archives the artifact record; the file stays in storage and can still be downloaded
		remove(creator.session(), task, fileArtifact).andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject("select count(*) from task_artifact where id = ? and archived_at is not null"
				+ " and archived_by = ?", Long.class, fileArtifact, creator.id())).isEqualTo(1);
		assertThat(jdbc.queryForObject("select archived_at is null from file_object where id = ?", Boolean.class,
				file)).isTrue();
		assertThat(files.find(file)).isPresent();
		mvc.perform(get("/api/v1/files/" + file).session(admin)).andExpect(status().isOk())
				.andExpect(content().bytes(PDF));
		remove(creator.session(), task, link).andExpect(status().isNoContent());
		remove(creator.session(), task, link).andExpect(status().isNotFound());
		remove(creator.session(), task, UUID.randomUUID()).andExpect(status().isNotFound());

		read(admin, "/tasks/" + task + "/artifacts").andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
		read(admin, "/tasks/" + task + "/artifacts?includeArchived=true").andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].archived").value(true)).andExpect(jsonPath("$[0].archivedAt").isNotEmpty())
				.andExpect(jsonPath("$[0].archivedBy").value(creator.id().toString()));
		read(admin, "/tasks/" + task).andExpect(jsonPath("$.artifacts.length()").value(1));
		// nothing was hard-deleted
		assertThat(artifactRows(task)).isEqualTo(3);

		// the same file can be attached again after removal (a new record; history keeps the old one)
		addFile(creator.session(), task, file).andExpect(status().isCreated());
		assertThat(artifactRows(task)).isEqualTo(4);
		read(admin, "/tasks/" + UUID.randomUUID() + "/artifacts").andExpect(status().isNotFound());
	}

	// ---- AR-2 ----

	@Test
	void unsafeOrOverlongUrlsAndMismatchedFieldsAreRejectedWithProblems() throws Exception {
		UUID space = space();
		Account english = account("tasks.view", "tasks.create");
		UUID task = createTask(english.session(), space, "");
		String tooLong = "https://example.org/" + "a".repeat(2048 - 19);

		for (String url : List.of("javascript:alert(1)", "JAVASCRIPT:alert(1)", "ftp://example.org/fake.txt",
				"data:text/html;base64,PHNjcmlwdD4=", "//example.org/x", "https://", "https://exa mple.org")) {
			addLink(english.session(), task, url, null).andExpect(status().isUnprocessableContent())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
					.andExpect(jsonPath("$.code").value("TASK_ARTIFACT_URL_INVALID"))
					.andExpect(jsonPath("$.title").value("Invalid link"))
					.andExpect(jsonPath("$.type").value("urn:crm:problem:task-artifact-url-invalid"));
		}
		addLink(english.session(), task, tooLong, null).andExpect(status().isUnprocessableContent())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("TASK_ARTIFACT_URL_TOO_LONG"))
				.andExpect(jsonPath("$.detail").value("A link can be at most 2048 characters long."));
		Account ukrainian = account("tasks.view", "tasks.edit.any");
		jdbc.update("update users set locale = 'uk' where id = ?", ukrainian.id());
		String login = jdbc.queryForObject("select login from users where id = ?", String.class, ukrainian.id());
		addLink(signIn(login, PASSWORD), task, "ftp://example.org", null).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.title").value("Некоректне посилання"))
				.andExpect(jsonPath("$.detail").value("Дозволені лише повні посилання http або https з іменем хоста, без"
						+ " пробілів."));

		UUID file = upload(admin, "fake.pdf");
		send(admin, "/tasks/" + task + "/artifacts", "{\"type\":\"LINK\"}").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("TASK_ARTIFACT_URL_INVALID"));
		send(admin, "/tasks/" + task + "/artifacts", "{\"type\":\"LINK\",\"url\":\"https://example.org\",\"fileId\":\""
				+ file + "\"}").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("TASK_ARTIFACT_INVALID"));
		send(admin, "/tasks/" + task + "/artifacts", "{\"type\":\"FILE\"}").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("TASK_ARTIFACT_INVALID"));
		send(admin, "/tasks/" + task + "/artifacts", "{\"type\":\"FILE\",\"fileId\":\"" + file
				+ "\",\"url\":\"https://example.org\"}").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("TASK_ARTIFACT_INVALID"));
		addFile(admin, task, UUID.randomUUID()).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("TASK_ARTIFACT_FILE_INVALID"));
		send(admin, "/tasks/" + task + "/artifacts", "{\"url\":\"https://example.org\"}")
				.andExpect(status().isBadRequest());
		send(admin, "/tasks/" + task + "/artifacts", "{\"type\":\"LINK\",\"url\":\"https://example.org\",\"title\":\""
				+ "t".repeat(301) + "\"}").andExpect(status().isBadRequest());
		assertThat(artifactRows(task)).isZero();
		assertThat(auditUpdates(task)).isZero();

		// an archived file cannot be attached; an already attached file not twice
		addFile(admin, task, file).andExpect(status().isCreated());
		addFile(admin, task, file).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TASK_ARTIFACT_DUPLICATE"));
		UUID archivedFile = upload(admin, "archived.pdf");
		files.archive(archivedFile);
		addFile(admin, task, archivedFile).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("TASK_ARTIFACT_FILE_INVALID"));
		assertThat(artifactRows(task)).isEqualTo(1);
		assertThat(auditUpdates(task)).isEqualTo(1);
	}

	// ---- AR-3: the 403 matrix ----

	@Test
	void onlyTheCreatorAssigneesEditAnyAndAdminMayAddOrRemoveArtifacts() throws Exception {
		UUID space = space();
		Account creator = account("tasks.view", "tasks.create", "tasks.assign");
		Account worker = account("tasks.view");
		Account editAny = account("tasks.view", "tasks.edit.any");
		Account viewer = account("tasks.view", "tasks.create", "tasks.assign");
		Account nobody = account();
		UUID task = createTask(creator.session(), space, "");
		send(creator.session(), "/tasks/" + task + "/assignees", "{\"userId\":\"" + worker.id() + "\"}")
				.andExpect(status().isCreated());

		UUID byCreator = id(addLink(creator.session(), task, "https://example.org/creator", null)
				.andExpect(status().isCreated()));
		UUID byWorker = id(addLink(worker.session(), task, "https://example.org/worker", null)
				.andExpect(status().isCreated()));
		UUID byEditAny = id(addLink(editAny.session(), task, "https://example.org/edit-any", null)
				.andExpect(status().isCreated()));
		UUID byAdmin = id(addLink(admin, task, "https://example.org/admin", null).andExpect(status().isCreated()));

		// neither creator nor assignee nor tasks.edit.any (tasks.create/tasks.assign do not help)
		addLink(viewer.session(), task, "https://example.org/viewer", null).andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("TASK_CONTRIBUTE_FORBIDDEN"));
		remove(viewer.session(), task, byCreator).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("TASK_CONTRIBUTE_FORBIDDEN"));
		addLink(nobody.session(), task, "https://example.org/nobody", null).andExpect(status().isForbidden());
		read(nobody.session(), "/tasks/" + task + "/artifacts").andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/tasks/" + task + "/artifacts")).andExpect(status().isUnauthorized());
		read(viewer.session(), "/tasks/" + task + "/artifacts").andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(4));

		// any contributor may remove any artifact of the task
		remove(worker.session(), task, byCreator).andExpect(status().isNoContent());
		remove(editAny.session(), task, byWorker).andExpect(status().isNoContent());
		remove(admin, task, byEditAny).andExpect(status().isNoContent());

		// a removed assignee loses the right
		mvc.perform(delete("/api/v1/tasks/" + task + "/assignees/" + worker.id()).with(csrf())
				.session(creator.session())).andExpect(status().isNoContent());
		remove(worker.session(), task, byAdmin).andExpect(status().isForbidden());
		addLink(worker.session(), task, "https://example.org/late", null).andExpect(status().isForbidden());
		remove(creator.session(), task, byAdmin).andExpect(status().isNoContent());
	}

	@Test
	void artifactsOfAnArchivedTaskCannotChangeButCanBeRead() throws Exception {
		UUID space = space();
		UUID task = createTask(admin, space, "");
		UUID link = id(addLink(admin, task, "https://example.org/kept", null).andExpect(status().isCreated()));
		send(admin, "/tasks/" + task + "/archive", "").andExpect(status().isOk());

		addLink(admin, task, "https://example.org/new", null).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TASK_ARCHIVED"));
		remove(admin, task, link).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TASK_ARCHIVED"));
		read(admin, "/tasks/" + task + "/artifacts").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
		read(admin, "/tasks/" + task).andExpect(jsonPath("$.artifacts.length()").value(1));
	}

	// ---- §6.4: audit with before/after ----

	@Test
	void addingAndRemovingAreAuditedOnTheTaskWithBeforeAndAfter() throws Exception {
		UUID space = space();
		UUID task = createTask(admin, space, "");
		UUID link = id(addLink(admin, task, "https://example.org/audited", "Audited").andExpect(status().isCreated()));
		assertThat(auditUpdates(task)).isEqualTo(1);
		String added = "select %s from audit_log where entity_type = 'Task' and entity_id = ? and action = 'UPDATE'"
				+ " and before_state -> 'artifact' = 'null'::jsonb";
		assertThat(jdbc.queryForObject(added.formatted("before_state ->> 'artifactIds'"), String.class, task))
				.isEqualTo("[]");
		assertThat(jdbc.queryForObject(added.formatted("after_state ->> 'artifactIds'"), String.class, task))
				.contains(link.toString());
		assertThat(jdbc.queryForObject(added.formatted("after_state -> 'artifact' ->> 'url'"), String.class, task))
				.isEqualTo("https://example.org/audited");
		assertThat(jdbc.queryForObject(added.formatted("after_state -> 'artifact' ->> 'type'"), String.class, task))
				.isEqualTo("LINK");

		remove(admin, task, link).andExpect(status().isNoContent());
		assertThat(auditUpdates(task)).isEqualTo(2);
		String removed = "select %s from audit_log where entity_type = 'Task' and entity_id = ? and action = 'UPDATE'"
				+ " and after_state -> 'artifact' ->> 'archivedAt' is not null";
		assertThat(jdbc.queryForObject(removed.formatted("before_state -> 'artifact' ->> 'archivedAt'"), String.class,
				task)).isNull();
		assertThat(jdbc.queryForObject(removed.formatted("before_state ->> 'artifactIds'"), String.class, task))
				.contains(link.toString());
		assertThat(jdbc.queryForObject(removed.formatted("after_state ->> 'artifactIds'"), String.class, task))
				.isEqualTo("[]");

		// rejected changes are not audited
		remove(admin, task, link).andExpect(status().isNotFound());
		addLink(admin, task, "javascript:alert(1)", null).andExpect(status().isUnprocessableContent());
		assertThat(auditUpdates(task)).isEqualTo(2);
	}

	// ---- legacy attachments are mirrored until INC-082 ----

	@Test
	void attachmentsOfTheOldTaskApiAreMirroredAsFileArtifacts() throws Exception {
		UUID space = space();
		UUID file = upload(admin, "legacy.pdf");
		UUID other = upload(admin, "other.pdf");
		UUID task = createTask(admin, space, ",\"attachmentIds\":[\"" + file + "\"]");
		read(admin, "/tasks/" + task).andExpect(jsonPath("$.attachments.length()").value(1))
				.andExpect(jsonPath("$.artifacts.length()").value(1))
				.andExpect(jsonPath("$.artifacts[0].type").value("FILE"))
				.andExpect(jsonPath("$.artifacts[0].fileId").value(file.toString()))
				.andExpect(jsonPath("$.artifacts[0].name").value("legacy.pdf"));
		String title = JsonPath.read(read(admin, "/tasks/" + task).andReturn().getResponse().getContentAsString(),
				"$.title");

		// a file attached through the new API is not touched by the old one, and vice versa no duplicates
		addFile(admin, task, other).andExpect(status().isCreated());
		putJson(admin, "/tasks/" + task, "{\"title\":\"" + title + "\",\"attachmentIds\":[\"" + file + "\",\"" + other
				+ "\"]}").andExpect(status().isOk()).andExpect(jsonPath("$.artifacts.length()").value(2));
		// detaching through the old API archives the mirrored artifact
		putJson(admin, "/tasks/" + task, "{\"title\":\"" + title + "\",\"attachmentIds\":[\"" + other + "\"]}")
				.andExpect(status().isOk()).andExpect(jsonPath("$.artifacts.length()").value(1))
				.andExpect(jsonPath("$.artifacts[0].fileId").value(other.toString()));
		assertThat(jdbc.queryForObject("select count(*) from task_artifact where task_id = ? and file_id = ? and"
				+ " archived_at is not null", Long.class, task, file)).isEqualTo(1);
		assertThat(artifactRows(task)).isEqualTo(2);
	}

}
