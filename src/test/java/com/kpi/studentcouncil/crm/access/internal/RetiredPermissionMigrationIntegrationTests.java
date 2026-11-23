package com.kpi.studentcouncil.crm.access.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Date;
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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.kpi.studentcouncil.crm.TestcontainersConfiguration;
import com.kpi.studentcouncil.crm.access.Permission;

/**
 * V31 (INC-081, TASK_TRACKING_FEATURE RR-3, RR-6, RR-7) on a dedicated database of the shared container, stopped at V30
 * and filled with fake roles carrying the permission sets the template roles had before the retired keys were removed,
 * plus roles edited in the role builder after V26. The application database is never touched.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RetiredPermissionMigrationIntegrationTests {

	private static final String CREATE = "resolutions.create";
	private static final String REDIRECT = "resolutions.redirect";

	private static final List<String> CREATED = new java.util.concurrent.CopyOnWriteArrayList<>();

	private static PostgreSQLContainer container;

	@Autowired
	PostgreSQLContainer postgres;

	@Autowired
	JdbcTemplate appJdbc;

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

	@Test
	void removesTheRetiredKeysAndKeepsTasksAssignForEveryRoleThatCouldIssue() {
		DataSource ds = freshDatabase();
		flyway(ds, "30").migrate();
		JdbcTemplate jdbc = new JdbcTemplate(ds);

		// template-like roles as seeded before INC-081: every issuing role also holds tasks.assign (V26)
		UUID head = role(jdbc, "Fake Head", false, "tasks.view", "tasks.create", "tasks.assign", "kb.edit", CREATE,
				REDIRECT, "audit.view");
		UUID member = role(jdbc, "Fake Member", false, "tasks.view", "tasks.create", "tasks.assign", CREATE, REDIRECT);
		UUID advisor = role(jdbc, "Fake Advisor", false, "tasks.view", "kb.view");
		// edited in the role builder after V26: resolutions.create without tasks.assign
		UUID lateIssuer = role(jdbc, "Fake late issuer", false, "tasks.view", CREATE);
		UUID archivedIssuer = role(jdbc, "Fake archived issuer", true, CREATE, REDIRECT);
		UUID redirectOnly = role(jdbc, "Fake redirect only", false, "tasks.view", REDIRECT);

		// legacy data that must stay as it is (RR-6, RR-7)
		UUID user = user(jdbc, "fake.legacy.author");
		UUID task = task(jdbc, user);
		UUID legacy = UUID.randomUUID();
		jdbc.update("insert into resolution (id, task_id, author_id, executor_id, instruction, due_date, state,"
				+ " created_at, updated_at) values (?, ?, ?, ?, 'Fake legacy instruction', ?, 'NEW', now(), now())",
				legacy, task, user, user, Date.valueOf(LocalDate.of(2026, 5, 20)));
		UUID auditRow = UUID.randomUUID();
		jdbc.update("insert into audit_log (id, actor_id, action, entity_type, entity_id, before_state, after_state, at)"
				+ " values (?, ?, 'UPDATE', 'Role', ?, '{\"permissions\":[\"resolutions.create\"]}'::jsonb,"
				+ " '{\"permissions\":[\"resolutions.create\",\"tasks.assign\"]}'::jsonb, now())", auditRow, user, head);
		List<Map<String, Object>> auditBefore = jdbc.queryForList("select * from audit_log order by id");
		long resolutionsBefore = jdbc.queryForObject("select count(*) from resolution", Long.class);

		flyway(ds, null).migrate();

		assertThat(jdbc.queryForObject("select count(*) from role_permission where permission_key in (?, ?)",
				Long.class, CREATE, REDIRECT)).isZero();
		assertThat(keys(jdbc, head)).containsExactlyInAnyOrder("tasks.view", "tasks.create", "tasks.assign", "kb.edit",
				"audit.view");
		assertThat(keys(jdbc, member)).containsExactlyInAnyOrder("tasks.view", "tasks.create", "tasks.assign");
		assertThat(keys(jdbc, advisor)).containsExactlyInAnyOrder("tasks.view", "kb.view");
		assertThat(keys(jdbc, lateIssuer)).containsExactlyInAnyOrder("tasks.view", "tasks.assign");
		assertThat(keys(jdbc, archivedIssuer)).containsExactly("tasks.assign");
		assertThat(keys(jdbc, redirectOnly)).containsExactly("tasks.view");
		// every stored key is known to the catalog again
		assertThat(jdbc.queryForList("select distinct permission_key from role_permission", String.class))
				.allMatch(Permission::isKnown);

		// legacy tables kept and unchanged, past audit rows untouched, no audit rows written by the migration
		assertThat(jdbc.queryForObject("select count(*) from resolution", Long.class)).isEqualTo(resolutionsBefore);
		assertThat(jdbc.queryForObject("select instruction from resolution where id = ?", String.class, legacy))
				.isEqualTo("Fake legacy instruction");
		assertThat(jdbc.queryForList("select * from audit_log order by id")).isEqualTo(auditBefore);
	}

	// ---- fixtures (fake data only) ----

	private static DataSource dataSource(PostgreSQLContainer pg, String database) {
		String url = pg.getJdbcUrl().replace("/" + pg.getDatabaseName(), "/" + database);
		return new DriverManagerDataSource(url, pg.getUsername(), pg.getPassword());
	}

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

	private static UUID role(JdbcTemplate jdbc, String name, boolean archived, String... keys) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into role (id, name, is_system, created_at, updated_at, archived_at) values (?, ?, false,"
				+ " now(), now(), " + (archived ? "now()" : "null") + ")", id, name);
		for (String key : keys) {
			jdbc.update("insert into role_permission (role_id, permission_key) values (?, ?)", id, key);
		}
		return id;
	}

	private static UUID user(JdbcTemplate jdbc, String login) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into users (id, login, password_hash, status, locale, must_change_password, created_at,"
				+ " updated_at) values (?, ?, 'not-a-real-hash', 'ACTIVE', 'en', false, now(), now())", id, login);
		return id;
	}

	private static UUID task(JdbcTemplate jdbc, UUID creator) {
		UUID space = UUID.randomUUID();
		jdbc.update("insert into task_space (id, name, is_general, created_at, updated_at) values (?, 'Fake space',"
				+ " false, now(), now())", space);
		UUID status = UUID.randomUUID();
		jdbc.update("insert into task_space_status (id, space_id, name, category, position, created_at, updated_at)"
				+ " values (?, ?, 'To do', 'TODO', 0, now(), now())", status, space);
		UUID task = UUID.randomUUID();
		jdbc.update("insert into task (id, space_id, title, status_id, priority, creator_id, issued_at, created_at,"
				+ " created_by, updated_at) values (?, ?, 'Fake task', ?, 'NORMAL', ?, now(), now(), ?, now())", task,
				space, status, creator, creator);
		return task;
	}

	private static List<String> keys(JdbcTemplate jdbc, UUID role) {
		return jdbc.queryForList("select permission_key from role_permission where role_id = ?", String.class, role);
	}

}
