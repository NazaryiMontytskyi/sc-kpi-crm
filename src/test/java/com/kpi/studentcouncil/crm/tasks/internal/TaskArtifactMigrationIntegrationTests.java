package com.kpi.studentcouncil.crm.tasks.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.kpi.studentcouncil.crm.TestcontainersConfiguration;

/**
 * V29 (legacy attachments become FILE artifacts, AR-4, INC-075) on dedicated databases of the shared container,
 * stopped at V28 and filled with fake tasks, files and attachments. Re-running the migration statement must not create
 * duplicates nor revive a removed artifact.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TaskArtifactMigrationIntegrationTests {

	private static final String SCRIPT = "db/migration/V29__tasks_attachments_to_artifacts.sql";

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

	private static String script() throws IOException {
		try (InputStream in = TaskArtifactMigrationIntegrationTests.class.getClassLoader().getResourceAsStream(SCRIPT)) {
			assertThat(in).as(SCRIPT).isNotNull();
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Test
	void migratesAnEmptyDatabase() {
		DataSource ds = freshDatabase();
		flyway(ds, null).migrate();
		JdbcTemplate jdbc = new JdbcTemplate(ds);
		assertThat(jdbc.queryForObject("select success from flyway_schema_history where version = '29'",
				Boolean.class)).isTrue();
		assertThat(jdbc.queryForObject("select count(*) from task_artifact", Long.class)).isZero();
	}

	@Test
	void copiesAttachmentsIntoFileArtifactsWithoutDuplicatesOnRepeatedRuns() throws IOException {
		DataSource ds = freshDatabase();
		flyway(ds, "28").migrate();
		JdbcTemplate jdbc = new JdbcTemplate(ds);

		// fake V28-era data
		UUID creator = user(jdbc, "fake.creator");
		UUID uploader = user(jdbc, "fake.uploader");
		UUID space = UUID.randomUUID();
		jdbc.update("insert into task_space (id, name, is_general, created_at, updated_at) values (?, 'Fake space',"
				+ " true, now(), now())", space);
		UUID status = UUID.randomUUID();
		jdbc.update("insert into task_space_status (id, space_id, name, category, position, created_at, updated_at)"
				+ " values (?, ?, 'To do', 'TODO', 0, now(), now())", status, space);
		UUID first = task(jdbc, space, status, creator, false);
		UUID second = task(jdbc, space, status, creator, true);
		UUID untouched = task(jdbc, space, status, creator, false);
		UUID removedBefore = task(jdbc, space, status, creator, false);

		Instant uploadedAt = Instant.parse("2026-02-03T10:00:00Z");
		UUID agenda = file(jdbc, "Fake agenda.pdf", uploader, uploadedAt, false);
		UUID minutes = file(jdbc, "Fake minutes.pdf", uploader, uploadedAt.plusSeconds(60), false);
		UUID archivedFile = file(jdbc, "Fake archived.pdf", uploader, uploadedAt.plusSeconds(120), true);
		attach(jdbc, first, 0, agenda);
		attach(jdbc, first, 1, minutes);
		attach(jdbc, first, 2, agenda); // the same file twice (legacy data): one artifact
		attach(jdbc, second, 0, agenda); // the same file on another task: an artifact of its own
		attach(jdbc, second, 1, archivedFile);
		attach(jdbc, removedBefore, 0, minutes);
		// an artifact of this file existed and was removed before the migration ran: it must not come back
		UUID removed = UUID.randomUUID();
		jdbc.update("insert into task_artifact (id, task_id, artifact_type, file_id, added_by, added_at, created_at,"
				+ " updated_at, archived_at, archived_by) values (?, ?, 'FILE', ?, ?, now(), now(), now(), now(), ?)",
				removed, removedBefore, minutes, creator, creator);
		long attachmentsBefore = count(jdbc, "select count(*) from task_attachment");
		long auditBefore = count(jdbc, "select count(*) from audit_log");

		flyway(ds, null).migrate();

		// AR-4: one FILE artifact per distinct (task, file) attachment
		assertThat(artifacts(jdbc, first)).containsExactlyInAnyOrder(agenda, minutes);
		assertThat(artifacts(jdbc, second)).containsExactlyInAnyOrder(agenda, archivedFile);
		assertThat(artifacts(jdbc, untouched)).isEmpty();
		assertThat(count(jdbc, "select count(*) from task_artifact where task_id = '" + removedBefore + "'"))
				.isEqualTo(1);
		assertThat(count(jdbc, "select count(*) from task_artifact where id = '" + removed + "' and archived_at is"
				+ " not null")).isEqualTo(1);

		Map<String, Object> row = jdbc.queryForMap("select artifact_type, url, title, added_by, added_at, archived_at"
				+ " from task_artifact where task_id = ? and file_id = ?", first, agenda);
		assertThat(row.get("artifact_type")).isEqualTo("FILE");
		assertThat(row.get("url")).isNull();
		assertThat(row.get("title")).isNull();
		assertThat(row.get("added_by")).isEqualTo(uploader);
		assertThat(((Timestamp) row.get("added_at")).toInstant()).isEqualTo(uploadedAt);
		assertThat(row.get("archived_at")).isNull();
		// ids are deterministic
		assertThat(jdbc.queryForObject("select id from task_artifact where task_id = ? and file_id = ?", UUID.class,
				first, agenda)).isEqualTo(jdbc.queryForObject("select md5('task-attachment-artifact:' || ?::text"
						+ " || ':' || ?::text)::uuid", UUID.class, first.toString(), agenda.toString()));

		// legacy data and history untouched
		assertThat(count(jdbc, "select count(*) from task_attachment")).isEqualTo(attachmentsBefore);
		assertThat(count(jdbc, "select count(*) from audit_log")).isEqualTo(auditBefore);
		assertThat(count(jdbc, "select count(*) from file_object where archived_at is null")).isEqualTo(2);

		// idempotent: running the statement again (also after an artifact was removed) changes nothing
		long total = count(jdbc, "select count(*) from task_artifact");
		jdbc.update("update task_artifact set archived_at = now(), archived_by = ? where task_id = ? and file_id = ?",
				creator, first, minutes);
		jdbc.execute(script());
		jdbc.execute(script());
		assertThat(count(jdbc, "select count(*) from task_artifact")).isEqualTo(total);
		assertThat(count(jdbc, "select count(*) from task_artifact where task_id = '" + first + "' and file_id = '"
				+ minutes + "' and archived_at is null")).isZero();
		// a runtime artifact (random id) of an attached file also prevents a copy
		jdbc.update("delete from task_artifact where task_id = ? and file_id = ?", second, agenda);
		UUID runtime = UUID.randomUUID();
		jdbc.update("insert into task_artifact (id, task_id, artifact_type, file_id, added_by, added_at, created_at,"
				+ " updated_at) values (?, ?, 'FILE', ?, ?, now(), now(), now())", runtime, second, agenda, creator);
		jdbc.execute(script());
		assertThat(count(jdbc, "select count(*) from task_artifact where task_id = '" + second + "' and file_id = '"
				+ agenda + "'")).isEqualTo(1);
		flyway(ds, null).migrate(); // Flyway itself never re-runs it
		assertThat(count(jdbc, "select count(*) from task_artifact")).isEqualTo(total);
	}

	// ---- fixtures (fake data only) ----

	private static long count(JdbcTemplate jdbc, String sql) {
		return jdbc.queryForObject(sql, Long.class);
	}

	private static List<UUID> artifacts(JdbcTemplate jdbc, UUID task) {
		return jdbc.queryForList("select file_id from task_artifact where task_id = ? and artifact_type = 'FILE' and"
				+ " archived_at is null", UUID.class, task);
	}

	private static UUID user(JdbcTemplate jdbc, String login) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into users (id, login, password_hash, status, locale, must_change_password, created_at,"
				+ " updated_at) values (?, ?, 'not-a-real-hash', 'ACTIVE', 'en', false, now(), now())", id, login);
		return id;
	}

	private static UUID task(JdbcTemplate jdbc, UUID space, UUID status, UUID creator, boolean archived) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into task (id, space_id, title, status_id, priority, creator_id, issued_at, created_at,"
				+ " created_by, updated_at, archived_at) values (?, ?, 'Fake task', ?, 'NORMAL', ?, now(), now(), ?,"
				+ " now(), " + (archived ? "now()" : "null") + ")", id, space, status, creator, creator);
		return id;
	}

	private static UUID file(JdbcTemplate jdbc, String name, UUID uploader, Instant at, boolean archived) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into file_object (id, storage_key, file_name, mime_type, size_bytes, uploaded_by,"
				+ " created_at, updated_at, archived_at) values (?, ?, ?, 'application/pdf', 10, ?, ?, ?, "
				+ (archived ? "now()" : "null") + ")", id, "fake-" + id, name, uploader, Timestamp.from(at),
				Timestamp.from(at));
		return id;
	}

	private static void attach(JdbcTemplate jdbc, UUID task, int position, UUID file) {
		jdbc.update("insert into task_attachment (task_id, position, file_id) values (?, ?, ?)", task, position, file);
	}

}
