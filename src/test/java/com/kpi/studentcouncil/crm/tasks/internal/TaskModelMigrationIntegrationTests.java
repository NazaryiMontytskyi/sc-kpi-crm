package com.kpi.studentcouncil.crm.tasks.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.kpi.studentcouncil.crm.TestcontainersConfiguration;
import com.kpi.studentcouncil.crm.access.AccessService;
import com.kpi.studentcouncil.crmtest.SharedAdmin;

/**
 * INC-071 migrations (V25 task model, V26 tasks.assign grant) on a dedicated database of the shared container: once on
 * an empty database and once on a database stopped at V24 and filled with fake V24-era rows. The application database
 * is never touched, so the data shapes of the other tests do not matter here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TaskModelMigrationIntegrationTests {

	private static final UUID NIL = new UUID(0L, 0L);

	private static final List<String> CREATED = new java.util.concurrent.CopyOnWriteArrayList<>();

	private static PostgreSQLContainer container;

	@Autowired
	PostgreSQLContainer postgres;

	@Autowired
	JdbcTemplate appJdbc;

	@Autowired
	PasswordEncoder encoder;

	@Autowired
	AccessService access;

	@AfterAll
	static void dropDatabases() {
		if (container == null) {
			return;
		}
		JdbcTemplate admin = new JdbcTemplate(dataSource(container, container.getDatabaseName()));
		for (String name : CREATED) {
			admin.execute("drop database if exists " + name + " with (force)");
		}
	}

	private static DataSource dataSource(PostgreSQLContainer pg, String database) {
		String url = pg.getJdbcUrl().replace("/" + pg.getDatabaseName(), "/" + database);
		return new DriverManagerDataSource(url, pg.getUsername(), pg.getPassword());
	}

	/** Creates an empty database in the shared container and returns a data source for it. */
	private DataSource freshDatabase() {
		container = postgres;
		String name = "mig_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toLowerCase(Locale.ROOT);
		appJdbc.execute("create database " + name);
		CREATED.add(name);
		return dataSource(postgres, name);
	}

	private static Flyway flyway(DataSource ds, String target) {
		var config = Flyway.configure().dataSource(ds).locations("classpath:db/migration");
		if (target != null) {
			config.target(target);
		}
		return config.load();
	}

	@Test
	void migratesAnEmptyDatabase() {
		DataSource ds = freshDatabase();
		flyway(ds, null).migrate();
		JdbcTemplate jdbc = new JdbcTemplate(ds);
		assertThat(jdbc.queryForObject("select max(version::int) from flyway_schema_history where success",
				Integer.class)).isGreaterThanOrEqualTo(26);
		assertThat(columns(jdbc, "task")).contains("deadline", "creator_id", "issued_at", "parent_task_id", "version")
				.doesNotContain("due_date");
		assertThat(tables(jdbc)).contains("task_assignee", "task_artifact", "task_reminder", "resolution",
				"resolution_co_executor", "resolution_history", "resolution_reminder");
	}

	@Test
	void migratesV24DataPreservingDeadlinesAndBackfillingCreatorAndIssueDate() {
		DataSource ds = freshDatabase();
		flyway(ds, "24").migrate();
		JdbcTemplate jdbc = new JdbcTemplate(ds);

		// fake V24-era data
		UUID author = user(jdbc, "fake.author");
		UUID executor = user(jdbc, "fake.executor");
		UUID space = UUID.randomUUID();
		jdbc.update("insert into task_space (id, name, is_general, created_at, updated_at) values (?, 'Fake space',"
				+ " true, now(), now())", space);
		UUID status = UUID.randomUUID();
		jdbc.update("insert into task_space_status (id, space_id, name, category, position, created_at, updated_at)"
				+ " values (?, ?, 'To do', 'TODO', 0, now(), now())", status, space);
		Instant created = Instant.parse("2026-03-04T08:15:30Z");
		UUID withDeadline = task(jdbc, space, status, LocalDate.of(2026, 5, 31), author, created);
		UUID withoutDeadline = task(jdbc, space, status, null, author, created.plusSeconds(60));
		UUID systemTask = task(jdbc, space, status, LocalDate.of(2027, 1, 1), null, created.plusSeconds(120));
		UUID resolution = UUID.randomUUID();
		jdbc.update("insert into resolution (id, task_id, author_id, executor_id, instruction, due_date, state,"
				+ " created_at, updated_at) values (?, ?, ?, ?, 'Fake instruction', ?, 'NEW', now(), now())",
				resolution, withDeadline, author, executor, Date.valueOf(LocalDate.of(2026, 5, 20)));

		UUID head = role(jdbc, "Fake head", false, "tasks.view", "resolutions.create", "resolutions.redirect");
		UUID member = role(jdbc, "Fake member", false, "tasks.view", "tasks.create", "resolutions.create");
		UUID archived = role(jdbc, "Fake archived", true, "resolutions.create");
		UUID advisor = role(jdbc, "Fake advisor", false, "tasks.view", "kb.view");
		UUID redirectOnly = role(jdbc, "Fake redirect only", false, "resolutions.redirect");
		long permissionRowsBefore = jdbc.queryForObject("select count(*) from role_permission", Long.class);

		flyway(ds, null).migrate();

		// TM-1/TM-2: deadline preserved, creator and issue date backfilled, version starts at 0, no parent
		Map<String, Object> row = jdbc.queryForMap("select deadline, creator_id, issued_at, parent_task_id, version"
				+ " from task where id = ?", withDeadline);
		assertThat(((Date) row.get("deadline")).toLocalDate()).isEqualTo(LocalDate.of(2026, 5, 31));
		assertThat(row.get("creator_id")).isEqualTo(author);
		assertThat(((Timestamp) row.get("issued_at")).toInstant()).isEqualTo(created);
		assertThat(row.get("parent_task_id")).isNull();
		assertThat(row.get("version")).isEqualTo(0L);
		assertThat(jdbc.queryForObject("select deadline from task where id = ?", Date.class, withoutDeadline)).isNull();
		assertThat(jdbc.queryForObject("select creator_id from task where id = ?", UUID.class, systemTask))
				.isEqualTo(NIL);
		assertThat(jdbc.queryForObject("select count(*) from task where creator_id is null or issued_at is null",
				Long.class)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from task where issued_at <> created_at", Long.class)).isZero();

		// legacy resolution rows are untouched
		assertThat(jdbc.queryForObject("select due_date from resolution where id = ?", Date.class, resolution)
				.toLocalDate()).isEqualTo(LocalDate.of(2026, 5, 20));

		// spec 9.10: tasks.assign for every role that held resolutions.create, nobody else; old keys stay for now
		assertThat(keys(jdbc, head)).contains("tasks.assign", "resolutions.create", "resolutions.redirect");
		assertThat(keys(jdbc, member)).contains("tasks.assign", "resolutions.create");
		assertThat(keys(jdbc, archived)).contains("tasks.assign");
		assertThat(keys(jdbc, advisor)).doesNotContain("tasks.assign");
		assertThat(keys(jdbc, redirectOnly)).doesNotContain("tasks.assign");
		assertThat(jdbc.queryForObject("select count(*) from role_permission", Long.class))
				.isEqualTo(permissionRowsBefore + 3);

		// indexes of the new model (spec 7)
		assertThat(indexes(jdbc, "task")).contains("ix_task_parent", "ix_task_deadline", "ix_task_creator");
		assertThat(indexes(jdbc, "task_assignee")).contains("uq_task_assignee_active", "ix_task_assignee_user");
		assertThat(jdbc.queryForObject("select indexdef from pg_indexes where indexname = 'uq_task_assignee_active'",
				String.class)).contains("UNIQUE").contains("(task_id, user_id)").contains("removed_at IS NULL");
		assertThat(jdbc.queryForObject("select indexdef from pg_indexes where indexname = 'ix_task_assignee_user'",
				String.class)).contains("(user_id, task_id)");
		assertThat(indexes(jdbc, "task_artifact")).contains("ix_task_artifact_task");

		// AS-1: a user is an active assignee of a task at most once; a removed row does not block re-assigning
		UUID first = assignee(jdbc, withDeadline, executor, author);
		assertThatThrownBy(() -> assignee(jdbc, withDeadline, executor, author))
				.isInstanceOf(DataIntegrityViolationException.class);
		jdbc.update("update task_assignee set removed_at = now(), removed_by = ? where id = ?", author, first);
		assignee(jdbc, withDeadline, executor, author);
		assignee(jdbc, withDeadline, author, author);
		assertThat(jdbc.queryForObject("select count(*) from task_assignee where task_id = ?", Long.class,
				withDeadline)).isEqualTo(3);

		// subtask self-reference: the parent must exist and cannot be the task itself
		jdbc.update("update task set parent_task_id = ? where id = ?", withDeadline, withoutDeadline);
		assertThatThrownBy(() -> jdbc.update("update task set parent_task_id = id where id = ?", systemTask))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("update task set parent_task_id = ? where id = ?", UUID.randomUUID(),
				systemTask)).isInstanceOf(DataIntegrityViolationException.class);

		// artifacts: FILE needs a file, LINK an http(s) url
		jdbc.update("insert into task_artifact (id, task_id, artifact_type, url, title, added_by, added_at, created_at,"
				+ " updated_at) values (?, ?, 'LINK', 'https://example.org/fake', 'Fake link', ?, now(), now(), now())",
				UUID.randomUUID(), withDeadline, author);
		assertThatThrownBy(() -> jdbc.update("insert into task_artifact (id, task_id, artifact_type, url, added_by,"
				+ " added_at, created_at, updated_at) values (?, ?, 'LINK', 'javascript:alert(1)', ?, now(), now(),"
				+ " now())", UUID.randomUUID(), withDeadline, author)).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("insert into task_artifact (id, task_id, artifact_type, added_by,"
				+ " added_at, created_at, updated_at) values (?, ?, 'FILE', ?, now(), now(), now())", UUID.randomUUID(),
				withDeadline, author)).isInstanceOf(DataIntegrityViolationException.class);

		// NT-3: one reminder per (task, user, threshold, deadline); a new deadline re-arms
		reminder(jdbc, withDeadline, executor, "DUE_SOON_1D", LocalDate.of(2026, 5, 31));
		assertThatThrownBy(() -> reminder(jdbc, withDeadline, executor, "DUE_SOON_1D", LocalDate.of(2026, 5, 31)))
				.isInstanceOf(DataIntegrityViolationException.class);
		reminder(jdbc, withDeadline, executor, "DUE_SOON_1D", LocalDate.of(2026, 6, 15));
	}

	@Test
	void adminHasTasksAssign() {
		UUID admin = SharedAdmin.ensure(appJdbc, encoder);
		assertThat(access.effectivePermissions(admin)).contains("tasks.assign");
	}

	// ---- fixtures (fake data only) ----

	private static UUID user(JdbcTemplate jdbc, String login) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into users (id, login, password_hash, status, locale, must_change_password, created_at,"
				+ " updated_at) values (?, ?, 'not-a-real-hash', 'ACTIVE', 'en', false, now(), now())", id, login);
		return id;
	}

	private static UUID task(JdbcTemplate jdbc, UUID space, UUID status, LocalDate due, UUID createdBy,
			Instant createdAt) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into task (id, space_id, title, status_id, priority, due_date, created_at, created_by,"
				+ " updated_at) values (?, ?, 'Fake task', ?, 'NORMAL', ?, ?, ?, ?)", id, space, status,
				due == null ? null : Date.valueOf(due), Timestamp.from(createdAt), createdBy, Timestamp.from(createdAt));
		return id;
	}

	private static UUID role(JdbcTemplate jdbc, String name, boolean archived, String... keys) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into role (id, name, is_system, created_at, updated_at, archived_at) values (?, ?, false,"
				+ " now(), now(), " + (archived ? "now()" : "null") + ")", id, name);
		for (String key : keys) {
			jdbc.update("insert into role_permission (role_id, permission_key) values (?, ?)", id, key);
		}
		return id;
	}

	private static UUID assignee(JdbcTemplate jdbc, UUID task, UUID user, UUID by) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into task_assignee (id, task_id, user_id, assigned_by, assigned_at, created_at, updated_at)"
				+ " values (?, ?, ?, ?, now(), now(), now())", id, task, user, by);
		return id;
	}

	private static void reminder(JdbcTemplate jdbc, UUID task, UUID user, String threshold, LocalDate deadline) {
		jdbc.update("insert into task_reminder (task_id, user_id, threshold, deadline, sent_at) values (?, ?, ?, ?,"
				+ " now())", task, user, threshold, Date.valueOf(deadline));
	}

	private static List<String> keys(JdbcTemplate jdbc, UUID role) {
		return jdbc.queryForList("select permission_key from role_permission where role_id = ?", String.class, role);
	}

	private static List<String> columns(JdbcTemplate jdbc, String table) {
		return jdbc.queryForList("select column_name from information_schema.columns where table_schema = 'public'"
				+ " and table_name = ?", String.class, table);
	}

	private static List<String> tables(JdbcTemplate jdbc) {
		return jdbc.queryForList("select table_name from information_schema.tables where table_schema = 'public'",
				String.class);
	}

	private static List<String> indexes(JdbcTemplate jdbc, String table) {
		return jdbc.queryForList("select indexname from pg_indexes where schemaname = 'public' and tablename = ?",
				String.class, table);
	}

}
