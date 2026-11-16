package com.kpi.studentcouncil.crm.tasks.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
import com.kpi.studentcouncil.crm.tasks.StatusCategory;
import com.kpi.studentcouncil.crm.tasks.internal.ResolutionMigrationMapping.MigratedAssignee;
import com.kpi.studentcouncil.crm.tasks.internal.ResolutionMigrationMapping.MigratedComment;
import com.kpi.studentcouncil.crm.tasks.internal.ResolutionMigrationMapping.MigratedReminder;
import com.kpi.studentcouncil.crm.tasks.internal.ResolutionMigrationMapping.MigratedSubtask;
import com.kpi.studentcouncil.crm.tasks.internal.ResolutionMigrationMapping.ParentTask;
import com.kpi.studentcouncil.crm.tasks.internal.ResolutionMigrationMapping.SourceHistory;
import com.kpi.studentcouncil.crm.tasks.internal.ResolutionMigrationMapping.SourceReminder;
import com.kpi.studentcouncil.crm.tasks.internal.ResolutionMigrationMapping.SourceResolution;
import com.kpi.studentcouncil.crm.tasks.internal.ResolutionMigrationMapping.SpaceStatusOption;

import tools.jackson.databind.ObjectMapper;

/**
 * V28 (resolutions become subtasks, INC-072) on dedicated databases of the shared container, stopped at V27 and filled
 * with fake resolutions covering every state, co-executors, redirect chains, a cascade cancel, completion notes, rework
 * comments, reminders and a Space lacking IN_PROGRESS/CANCELED statuses. Every row V28 writes is compared with what
 * {@link ResolutionMigrationMapping} computes for the same input, so the SQL and its Java specification agree.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ResolutionMigrationIntegrationTests {

	private static final List<String> CREATED = new java.util.concurrent.CopyOnWriteArrayList<>();

	private static final Instant T = Instant.parse("2026-01-10T08:00:00Z");

	private static final ObjectMapper JSON = new ObjectMapper();

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

	private static UUID id(String name) {
		return UUID.nameUUIDFromBytes(("fake:" + name).getBytes(StandardCharsets.UTF_8));
	}

	// ---- tests ----

	@Test
	void migratesAnEmptyDatabase() {
		DataSource ds = freshDatabase();
		flyway(ds, null).migrate();
		JdbcTemplate jdbc = new JdbcTemplate(ds);
		assertThat(jdbc.queryForObject("select success from flyway_schema_history where version = '28'",
				Boolean.class)).isTrue();
		assertThat(jdbc.queryForObject("select count(*) from task", Long.class)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from comment", Long.class)).isZero();
		// the helper functions were temporary: none is left in the schema
		assertThat(jdbc.queryForObject("select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace"
				+ " where p.proname like 'v28_%' and n.nspname = 'public'", Long.class)).isZero();
	}

	@Test
	void convertsResolutionsIntoSubtasksAsSpecified() {
		DataSource ds = freshDatabase();
		flyway(ds, "27").migrate();
		JdbcTemplate jdbc = new JdbcTemplate(ds);
		Fixture.insert(jdbc);

		List<Expected> expected = expectations(jdbc);
		Map<String, String> legacyBefore = legacySnapshot(jdbc);
		long resolutions = count(jdbc, "select count(*) from resolution");
		long executorsAndCoExecutors = count(jdbc, "select count(*) from (select id, executor_id from resolution"
				+ " union select resolution_id, user_id from resolution_co_executor) a");
		long historyEntries = count(jdbc, "select count(*) from resolution_history");
		long redirectedChildren = count(jdbc, "select count(*) from resolution where parent_resolution_id is not null");
		long tasksBefore = count(jdbc, "select count(*) from task");
		assertThat(resolutions).isEqualTo(15);
		assertThat(executorsAndCoExecutors).isEqualTo(19);
		assertThat(historyEntries).isEqualTo(33);
		assertThat(redirectedChildren).isEqualTo(3);

		flyway(ds, null).migrate();

		// AC 6: legacy tables still exist and are byte-for-byte unchanged; audit rows untouched
		assertThat(legacySnapshot(jdbc)).isEqualTo(legacyBefore);

		// AC 2: counts
		assertThat(count(jdbc, "select count(*) from task")).isEqualTo(tasksBefore + resolutions);
		assertThat(count(jdbc, "select count(*) from task where parent_task_id is not null")).isEqualTo(resolutions);
		assertThat(count(jdbc, "select count(*) from task_assignee")).isEqualTo(executorsAndCoExecutors);
		long orphanNotes = 1; // fixture: one completion note without a SUBMITTED history entry
		assertThat(count(jdbc, "select count(*) from comment"))
				.isEqualTo(resolutions + historyEntries + orphanNotes + redirectedChildren);
		assertThat(count(jdbc, "select count(*) from comment where target_type <> 'TASK'")).isZero();

		// AC 1, 3, 5, 7: every row equals the Java specification
		for (Expected e : expected) {
			assertSubtask(jdbc, e.subtask());
		}

		// AC 3: original task as parent, the parent's space, the mapped category or the ADR-0010 fallback
		assertThat(count(jdbc, "select count(*) from task c join task p on p.id = c.parent_task_id"
				+ " where c.space_id <> p.space_id")).isZero();
		assertStatus(jdbc, "res-new", "a-todo");
		assertStatus(jdbc, "res-progress", "a-progress");
		assertStatus(jdbc, "res-submitted", "a-progress");
		assertStatus(jdbc, "res-rework", "a-progress");
		assertStatus(jdbc, "res-accepted", "a-done");
		assertStatus(jdbc, "res-canceled", "a-canceled");
		assertStatus(jdbc, "res-chain-root", "a-canceled");
		assertStatus(jdbc, "res-chain-middle", "a-canceled");
		assertStatus(jdbc, "res-chain-leaf", "a-progress");
		assertStatus(jdbc, "res-cascade-child", "a-canceled");
		assertStatus(jdbc, "res-b-progress", "b-todo"); // no IN_PROGRESS status -> TODO
		assertStatus(jdbc, "res-b-canceled", "b-done"); // only an archived CANCELED status -> DONE
		assertStatus(jdbc, "res-b-redirected", "b-done");
		assertStatus(jdbc, "res-b-orphan-note", "b-done");
		for (String name : List.of("res-new", "res-progress", "res-accepted", "res-canceled", "res-chain-leaf")) {
			assertThat(jdbc.queryForObject("select parent_task_id from task where id = ?", UUID.class, id(name)))
					.isEqualTo(id("task-a"));
		}

		// AC 4: redirect chains are flat siblings with a "Redirected from" comment
		assertThat(count(jdbc, "select count(*) from task c join task p on p.id = c.parent_task_id"
				+ " where p.parent_task_id is not null")).isZero();
		assertThat(firstLine(jdbc, ResolutionMigrationMapping.id("comment:" + id("res-chain-middle") + ":redirect")))
				.isEqualTo("[Перенесено з резолюції] Переадресовано з «Organise the fake open day».");
		assertThat(firstLine(jdbc, ResolutionMigrationMapping.id("comment:" + id("res-chain-leaf") + ":redirect")))
				.startsWith("[Перенесено з резолюції] Переадресовано з «Fake middle instruction")
				.endsWith("…».");

		// AC 1: content details with original authors and timestamps
		Map<String, Object> rework = jdbc.queryForMap("select created_by, created_at from comment where id = ?",
				ResolutionMigrationMapping.id("comment:" + id("res-rework") + ":history:3"));
		assertThat(rework.get("created_by")).isEqualTo(id("author"));
		assertThat(((Timestamp) rework.get("created_at")).toInstant()).isEqualTo(T.plus(Duration.ofHours(4))
				.plus(Duration.ofMinutes(30)));
		assertThat(count(jdbc, "select count(*) from comment where target_id = '" + id("res-rework")
				+ "' and body::text like '%Коментар до доопрацювання:%Add the fake budget%'")).isEqualTo(1);
		assertThat(count(jdbc, "select count(*) from comment where target_id = '" + id("res-submitted")
				+ "' and body::text like '%Звіт про виконання:%Fake report attached%'")).isEqualTo(1);
		assertThat(count(jdbc, "select count(*) from comment where target_id = '" + id("res-submitted")
				+ "' and body::text like '%очікує прийняття автором%'")).isEqualTo(1);
		assertThat(count(jdbc, "select count(*) from comment where body::text like '%очікує прийняття автором%'"))
				.isEqualTo(1);
		assertThat(count(jdbc, "select count(*) from comment where target_id = '" + id("res-cascade-child")
				+ "' and body::text like '%скасовано разом із попередньою резолюцією ланцюжка%'")).isEqualTo(1);
		// deadline is not clamped to the parent's
		assertThat(jdbc.queryForObject("select deadline from task where id = ?", Date.class, id("res-new"))
				.toLocalDate()).isEqualTo(LocalDate.of(2026, 6, 30));
		assertThat(jdbc.queryForObject("select deadline from task where id = ?", Date.class, id("task-a"))
				.toLocalDate()).isEqualTo(LocalDate.of(2026, 3, 31));
		// executor and co-executors, assigned by the author
		assertThat(jdbc.queryForList("select user_id from task_assignee where task_id = ? and assigned_by = ?",
				UUID.class, id("res-new"), id("author"))).containsExactlyInAnyOrder(id("exec1"), id("co1"), id("co2"));
		// archived resolution -> archived subtask
		assertThat(jdbc.queryForObject("select archived_at from task where id = ?", Timestamp.class,
				id("res-b-orphan-note"))).isNotNull();

		// AC 5: sent reminders -> task_reminder for every recipient of the old job
		assertThat(jdbc.queryForList("select user_id from task_reminder where task_id = ? and threshold = 'OVERDUE'",
				UUID.class, id("res-submitted"))).containsExactlyInAnyOrder(id("exec1"), id("author"));
		assertThat(jdbc.queryForList("select user_id from task_reminder where task_id = ? and threshold = 'DUE_SOON'",
				UUID.class, id("res-new"))).containsExactlyInAnyOrder(id("exec1"), id("co1"), id("co2"));
		assertThat(count(jdbc, "select count(*) from task_reminder")).isEqualTo(3 + 1 + 2);

		// the task without resolutions has no subtasks
		assertThat(count(jdbc, "select count(*) from task where parent_task_id = '" + id("task-empty") + "'")).isZero();
	}

	@Test
	void isDeterministic() {
		List<Map<String, String>> dumps = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			DataSource ds = freshDatabase();
			flyway(ds, "27").migrate();
			JdbcTemplate jdbc = new JdbcTemplate(ds);
			Fixture.insert(jdbc);
			flyway(ds, null).migrate();
			dumps.add(resultSnapshot(jdbc));
		}
		assertThat(dumps.get(0)).isEqualTo(dumps.get(1));
		assertThat(dumps.get(0).get("comment")).contains("Перенесено з резолюції");
	}

	// ---- comparison with the Java specification ----

	private record Expected(MigratedSubtask subtask) {
	}

	/** Reads the legacy rows (before V28) and computes the expected subtasks with the Java mapping. */
	private static List<Expected> expectations(JdbcTemplate jdbc) {
		ResolutionMigrationMapping mapping = ResolutionMigrationMapping.stored();
		List<Expected> result = new ArrayList<>();
		for (Map<String, Object> row : jdbc.queryForList("select * from resolution order by id")) {
			UUID rid = (UUID) row.get("id");
			Set<UUID> co = new LinkedHashSet<>(jdbc.queryForList(
					"select user_id from resolution_co_executor where resolution_id = ?", UUID.class, rid));
			List<SourceHistory> history = jdbc.query("select * from resolution_history where resolution_id = ?"
					+ " order by position", (rs, n) -> new SourceHistory(rs.getInt("position"),
							rs.getObject("actor_id", UUID.class), state(rs.getString("from_state")),
							state(rs.getString("to_state")), rs.getString("comment"),
							rs.getTimestamp("occurred_at").toInstant(),
							rs.getObject("caused_by_resolution_id", UUID.class)), rid);
			List<SourceReminder> reminders = jdbc.query("select * from resolution_reminder where resolution_id = ?",
					(rs, n) -> new SourceReminder(rs.getString("threshold"), rs.getDate("due_date").toLocalDate(),
							rs.getTimestamp("sent_at").toInstant()), rid);
			SourceResolution source = new SourceResolution(rid, (UUID) row.get("task_id"), (UUID) row.get("author_id"),
					(UUID) row.get("executor_id"), co, (String) row.get("instruction"),
					((Date) row.get("due_date")).toLocalDate(), (UUID) row.get("parent_resolution_id"),
					state((String) row.get("state")), (String) row.get("completion_note"),
					instant(row.get("created_at")), (UUID) row.get("created_by"), instant(row.get("updated_at")),
					(UUID) row.get("updated_by"), instant(row.get("archived_at")), (UUID) row.get("archived_by"),
					history, reminders);
			ParentTask parent = jdbc.queryForObject("select * from task where id = ?",
					(rs, n) -> new ParentTask(rs.getObject("id", UUID.class), rs.getObject("space_id", UUID.class),
							rs.getObject("status_id", UUID.class), rs.getObject("parent_task_id", UUID.class),
							rs.getTimestamp("archived_at") == null ? null : rs.getTimestamp("archived_at").toInstant(),
							rs.getObject("archived_by", UUID.class)), source.taskId());
			List<SpaceStatusOption> statuses = jdbc.query("select * from task_space_status where space_id = ?",
					(rs, n) -> new SpaceStatusOption(rs.getObject("id", UUID.class),
							StatusCategory.valueOf(rs.getString("category")), rs.getInt("position"),
							rs.getTimestamp("archived_at") != null), parent.spaceId());
			String redirectedFrom = source.parentResolutionId() == null ? null : jdbc.queryForObject(
					"select instruction from resolution where id = ?", String.class, source.parentResolutionId());
			result.add(new Expected(mapping.migrate(source, parent, statuses, redirectedFrom)));
		}
		return result;
	}

	private static void assertSubtask(JdbcTemplate jdbc, MigratedSubtask e) {
		Map<String, Object> row = jdbc.queryForMap("select * from task where id = ?", e.id());
		assertThat(row.get("parent_task_id")).isEqualTo(e.parentTaskId());
		assertThat(row.get("space_id")).isEqualTo(e.spaceId());
		assertThat(row.get("title")).isEqualTo(e.title());
		assertThat(row.get("status_id")).isEqualTo(e.statusId());
		assertThat(((Date) row.get("deadline")).toLocalDate()).isEqualTo(e.deadline());
		assertThat(row.get("creator_id")).isEqualTo(e.creatorId());
		assertThat(instant(row.get("issued_at"))).isEqualTo(e.issuedAt());
		assertThat(instant(row.get("created_at"))).isEqualTo(e.createdAt());
		assertThat(row.get("created_by")).isEqualTo(e.createdBy());
		assertThat(instant(row.get("updated_at"))).isEqualTo(e.updatedAt());
		assertThat(row.get("updated_by")).isEqualTo(e.updatedBy());
		assertThat(instant(row.get("archived_at"))).isEqualTo(e.archivedAt());
		assertThat(row.get("archived_by")).isEqualTo(e.archivedBy());
		assertThat(row.get("priority")).isEqualTo("NORMAL");
		assertThat(row.get("version")).isEqualTo(0L);
		assertThat(jsonEquals(jdbc, "task", "description", e.id(), e.description()))
				.as("description of %s", e.title()).isTrue();

		Set<MigratedAssignee> assignees = new HashSet<>(jdbc.query("select * from task_assignee where task_id = ?",
				(rs, n) -> {
					assertThat(rs.getTimestamp("removed_at")).isNull();
					return new MigratedAssignee(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
							rs.getObject("assigned_by", UUID.class), rs.getTimestamp("assigned_at").toInstant());
				}, e.id()));
		assertThat(assignees).isEqualTo(new HashSet<>(e.assignees()));

		Set<List<Object>> comments = new HashSet<>(jdbc.query("select * from comment where target_id = ?",
				(rs, n) -> List.<Object>of(rs.getObject("id", UUID.class), rs.getObject("created_by", UUID.class),
						rs.getTimestamp("created_at").toInstant(), rs.getString("target_type")), e.id()));
		Set<List<Object>> expectedComments = new HashSet<>();
		for (MigratedComment c : e.comments()) {
			expectedComments.add(List.of(c.id(), c.authorId(), c.at(), "TASK"));
			assertThat(jsonEquals(jdbc, "comment", "body", c.id(), c.body())).as("comment %s", c.text()).isTrue();
		}
		assertThat(comments).isEqualTo(expectedComments);

		Set<MigratedReminder> reminders = new HashSet<>(jdbc.query("select * from task_reminder where task_id = ?",
				(rs, n) -> new MigratedReminder(rs.getObject("user_id", UUID.class), rs.getString("threshold"),
						rs.getDate("deadline").toLocalDate(), rs.getTimestamp("sent_at").toInstant()), e.id()));
		assertThat(reminders).isEqualTo(new HashSet<>(e.reminders()));
	}

	private static boolean jsonEquals(JdbcTemplate jdbc, String table, String column, UUID id, Object expected) {
		return Boolean.TRUE.equals(jdbc.queryForObject("select " + column + " = ?::jsonb from " + table
				+ " where id = ?", Boolean.class, JSON.writeValueAsString(expected), id));
	}

	private static void assertStatus(JdbcTemplate jdbc, String resolution, String status) {
		assertThat(jdbc.queryForObject("select status_id from task where id = ?", UUID.class, id(resolution)))
				.as("status of %s", resolution).isEqualTo(id(status));
	}

	private static String firstLine(JdbcTemplate jdbc, UUID comment) {
		return jdbc.queryForObject("select body->'content'->0->'content'->0->>'text' from comment where id = ?",
				String.class, comment);
	}

	private static Map<String, String> legacySnapshot(JdbcTemplate jdbc) {
		return Map.of(
				"resolution", dump(jdbc, "resolution", "id"),
				"resolution_co_executor", dump(jdbc, "resolution_co_executor", "resolution_id, user_id"),
				"resolution_history", dump(jdbc, "resolution_history", "resolution_id, position"),
				"resolution_reminder", dump(jdbc, "resolution_reminder", "resolution_id, threshold, due_date"),
				"audit_log", dump(jdbc, "audit_log", "id"));
	}

	private static Map<String, String> resultSnapshot(JdbcTemplate jdbc) {
		return Map.of(
				"task", dump(jdbc, "task", "id"),
				"task_assignee", dump(jdbc, "task_assignee", "id"),
				"comment", dump(jdbc, "comment", "id"),
				"task_reminder", dump(jdbc, "task_reminder", "task_id, user_id, threshold, deadline"));
	}

	private static String dump(JdbcTemplate jdbc, String table, String order) {
		return jdbc.queryForObject("select coalesce(jsonb_agg(to_jsonb(x) order by " + order + ")::text, '[]') from "
				+ table + " x", String.class);
	}

	private static long count(JdbcTemplate jdbc, String sql) {
		return jdbc.queryForObject(sql, Long.class);
	}

	private static ResolutionState state(String s) {
		return s == null ? null : ResolutionState.valueOf(s);
	}

	private static Instant instant(Object timestamp) {
		return timestamp == null ? null : ((Timestamp) timestamp).toInstant();
	}

	// ---- fake data (fixed ids and times, so two databases receive identical input) ----

	private static final class Fixture {

		private final JdbcTemplate jdbc;

		private Fixture(JdbcTemplate jdbc) {
			this.jdbc = jdbc;
		}

		static void insert(JdbcTemplate jdbc) {
			new Fixture(jdbc).run();
		}

		private static Instant h(int hours, int minutes) {
			return T.plus(Duration.ofHours(hours)).plus(Duration.ofMinutes(minutes));
		}

		private void run() {
			for (String user : List.of("author", "exec1", "exec2", "exec3", "co1", "co2", "archiver")) {
				user(user);
			}
			// Space A: every category; an archived IN_PROGRESS status sorts before the active one
			space("space-a");
			status("a-todo", "space-a", "TODO", 0, false);
			status("a-progress-archived", "space-a", "IN_PROGRESS", 1, true);
			status("a-progress", "space-a", "IN_PROGRESS", 2, false);
			status("a-todo-2", "space-a", "TODO", 3, false);
			status("a-done", "space-a", "DONE", 4, false);
			status("a-canceled", "space-a", "CANCELED", 5, false);
			// Space B: no IN_PROGRESS status, the CANCELED one is archived
			space("space-b");
			status("b-todo", "space-b", "TODO", 0, false);
			status("b-done", "space-b", "DONE", 1, false);
			status("b-canceled-archived", "space-b", "CANCELED", 2, true);

			task("task-a", "space-a", "a-todo", LocalDate.of(2026, 3, 31));
			task("task-b", "space-b", "b-todo", null);
			task("task-empty", "space-a", "a-todo", LocalDate.of(2026, 4, 1));

			UUID author = id("author");
			UUID exec1 = id("exec1");
			UUID exec2 = id("exec2");
			UUID exec3 = id("exec3");

			// NEW with two co-executors; deadline later than the task's (not clamped); a sent DUE_SOON reminder
			resolution("res-new", "task-a", author, exec1, "Book a fake room\n\nfor the fake meeting",
					LocalDate.of(2026, 6, 30), null, "NEW", null, h(1, 0), h(1, 0), null);
			coExecutor("res-new", "co1");
			coExecutor("res-new", "co2");
			coExecutor("res-new", "exec1"); // legacy duplicate of the executor: one assignee only
			history("res-new", 0, author, null, "NEW", null, h(1, 0), null);
			reminder("res-new", "DUE_SOON", LocalDate.of(2026, 6, 30), h(100, 0));

			resolution("res-progress", "task-a", author, exec2, "Draft the fake agenda", LocalDate.of(2026, 3, 20),
					null, "IN_PROGRESS", null, h(2, 0), h(2, 10), null);
			history("res-progress", 0, author, null, "NEW", null, h(2, 0), null);
			history("res-progress", 1, exec2, "NEW", "IN_PROGRESS", null, h(2, 10), null);

			resolution("res-submitted", "task-a", author, exec1, "Write the fake report", LocalDate.of(2026, 3, 1),
					null, "SUBMITTED", "Fake report attached", h(3, 0), h(3, 40), null);
			history("res-submitted", 0, author, null, "NEW", null, h(3, 0), null);
			history("res-submitted", 1, exec1, "NEW", "IN_PROGRESS", null, h(3, 10), null);
			history("res-submitted", 2, exec1, "IN_PROGRESS", "SUBMITTED", "Fake report attached", h(3, 40), null);
			reminder("res-submitted", "DUE_SOON", LocalDate.of(2026, 3, 1), h(200, 0));
			reminder("res-submitted", "OVERDUE", LocalDate.of(2026, 3, 1), h(230, 0));

			resolution("res-rework", "task-a", author, exec2, "Prepare the fake budget", LocalDate.of(2026, 3, 15),
					null, "REWORK", "First fake draft", h(4, 0), h(4, 30), null);
			coExecutor("res-rework", "co1");
			history("res-rework", 0, author, null, "NEW", null, h(4, 0), null);
			history("res-rework", 1, exec2, "NEW", "IN_PROGRESS", null, h(4, 5), null);
			history("res-rework", 2, exec2, "IN_PROGRESS", "SUBMITTED", "First fake draft", h(4, 20), null);
			history("res-rework", 3, author, "SUBMITTED", "REWORK", "Add the fake budget lines", h(4, 30), null);

			resolution("res-accepted", "task-a", author, exec1, "Send the fake invitations",
					LocalDate.of(2026, 3, 10), null, "ACCEPTED", "Sent to all fake guests", h(5, 0), h(5, 50), null);
			history("res-accepted", 0, author, null, "NEW", null, h(5, 0), null);
			history("res-accepted", 1, exec1, "NEW", "IN_PROGRESS", null, h(5, 10), null);
			history("res-accepted", 2, exec1, "IN_PROGRESS", "SUBMITTED", "Sent to all fake guests", h(5, 40), null);
			history("res-accepted", 3, author, "SUBMITTED", "ACCEPTED", null, h(5, 50), null);

			resolution("res-canceled", "task-a", author, exec3, "Order fake banners", LocalDate.of(2026, 3, 5), null,
					"CANCELED", null, h(6, 0), h(6, 30), null);
			history("res-canceled", 0, author, null, "NEW", null, h(6, 0), null);
			history("res-canceled", 1, author, "NEW", "CANCELED", "No longer needed", h(6, 30), null);

			// redirect chain: author -> exec1 -> exec2 -> exec3
			resolution("res-chain-root", "task-a", author, exec1, "Organise the fake open day\nwith two fake talks",
					LocalDate.of(2026, 3, 25), null, "REDIRECTED", null, h(7, 0), h(7, 20), null);
			history("res-chain-root", 0, author, null, "NEW", null, h(7, 0), null);
			history("res-chain-root", 1, exec1, "NEW", "REDIRECTED", null, h(7, 20), null);
			resolution("res-chain-middle", "task-a", exec1, exec2, "Fake middle instruction " + "x".repeat(220),
					LocalDate.of(2026, 3, 24), id("res-chain-root"), "REDIRECTED", null, h(7, 20), h(7, 40), null);
			coExecutor("res-chain-middle", "co2");
			history("res-chain-middle", 0, exec1, null, "NEW", null, h(7, 20), null);
			history("res-chain-middle", 1, exec2, "NEW", "REDIRECTED", null, h(7, 40), null);
			resolution("res-chain-leaf", "task-a", exec2, exec3, "Run the fake open day", LocalDate.of(2026, 3, 23),
					id("res-chain-middle"), "IN_PROGRESS", null, h(7, 40), h(8, 0), null);
			history("res-chain-leaf", 0, exec2, null, "NEW", null, h(7, 40), null);
			history("res-chain-leaf", 1, exec3, "NEW", "IN_PROGRESS", null, h(8, 0), null);

			// cascade cancel by the system actor
			resolution("res-cascade-parent", "task-a", author, exec1, "Fake cascade parent", LocalDate.of(2026, 3, 9),
					null, "CANCELED", null, h(9, 0), h(9, 30), null);
			history("res-cascade-parent", 0, author, null, "NEW", null, h(9, 0), null);
			history("res-cascade-parent", 1, author, "NEW", "CANCELED", "Chain stopped", h(9, 30), null);
			resolution("res-cascade-child", "task-a", exec1, exec2, "Fake cascade child", LocalDate.of(2026, 3, 8),
					id("res-cascade-parent"), "CANCELED", null, h(9, 10), h(9, 30), null);
			history("res-cascade-child", 0, exec1, null, "NEW", null, h(9, 10), null);
			history("res-cascade-child", 1, new UUID(0L, 0L), "NEW", "CANCELED", "Chain stopped", h(9, 30),
					id("res-cascade-parent"));

			// Space B: fallbacks; an archived resolution with a completion note no history entry carries
			resolution("res-b-progress", "task-b", author, exec1, "Fake B in progress", LocalDate.of(2026, 4, 10),
					null, "IN_PROGRESS", null, h(10, 0), h(10, 5), null);
			history("res-b-progress", 0, author, null, "NEW", null, h(10, 0), null);
			history("res-b-progress", 1, exec1, "NEW", "IN_PROGRESS", null, h(10, 5), null);
			resolution("res-b-canceled", "task-b", author, exec2, "Fake B canceled", LocalDate.of(2026, 4, 11), null,
					"CANCELED", null, h(11, 0), h(11, 5), null);
			history("res-b-canceled", 0, author, null, "NEW", null, h(11, 0), null);
			history("res-b-canceled", 1, author, "NEW", "CANCELED", null, h(11, 5), null);
			resolution("res-b-redirected", "task-b", author, exec3, "Fake B redirected", LocalDate.of(2026, 4, 12),
					null, "REDIRECTED", null, h(12, 0), h(12, 5), null);
			history("res-b-redirected", 0, author, null, "NEW", null, h(12, 0), null);
			history("res-b-redirected", 1, exec3, "NEW", "REDIRECTED", null, h(12, 5), null);
			resolution("res-b-orphan-note", "task-b", author, exec1, "Fake B accepted", LocalDate.of(2026, 4, 13),
					null, "ACCEPTED", "Fake note without history", h(13, 0), h(13, 5), h(14, 0));
			history("res-b-orphan-note", 0, author, null, "NEW", null, h(13, 0), null);

			// an audit row about a resolution must stay untouched
			jdbc.update("insert into audit_log (id, actor_id, action, entity_type, entity_id, after_state, at) values"
					+ " (?, ?, 'CREATE', 'Resolution', ?, '{\"state\":\"NEW\"}'::jsonb, ?)", id("audit-1"), author,
					id("res-new"), Timestamp.from(h(1, 0)));
		}

		private void user(String name) {
			jdbc.update("insert into users (id, login, password_hash, status, locale, must_change_password, created_at,"
					+ " updated_at) values (?, ?, 'not-a-real-hash', 'ACTIVE', 'en', false, ?, ?)", id(name),
					"fake." + name, Timestamp.from(T), Timestamp.from(T));
		}

		private void space(String name) {
			jdbc.update("insert into task_space (id, name, is_general, created_at, updated_at) values (?, ?, true, ?,"
					+ " ?)", id(name), "Fake " + name, Timestamp.from(T), Timestamp.from(T));
		}

		private void status(String name, String space, String category, int position, boolean archived) {
			jdbc.update("insert into task_space_status (id, space_id, name, category, position, created_at,"
					+ " updated_at, archived_at) values (?, ?, ?, ?, ?, ?, ?, ?)", id(name), id(space), "Fake " + name,
					category, position, Timestamp.from(T), Timestamp.from(T), archived ? Timestamp.from(T) : null);
		}

		private void task(String name, String space, String status, LocalDate deadline) {
			jdbc.update("insert into task (id, space_id, title, status_id, priority, deadline, created_at, created_by,"
					+ " updated_at, creator_id, issued_at) values (?, ?, ?, ?, 'NORMAL', ?, ?, ?, ?, ?, ?)", id(name),
					id(space), "Fake " + name, id(status), deadline == null ? null : Date.valueOf(deadline),
					Timestamp.from(T), id("author"), Timestamp.from(T), id("author"), Timestamp.from(T));
		}

		private void resolution(String name, String task, UUID author, UUID executor, String instruction,
				LocalDate due, UUID parent, String state, String note, Instant created, Instant updated,
				Instant archived) {
			jdbc.update("insert into resolution (id, task_id, author_id, executor_id, instruction, due_date,"
					+ " parent_resolution_id, state, completion_note, created_at, created_by, updated_at, updated_by,"
					+ " archived_at, archived_by) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id(name),
					id(task), author, executor, instruction, Date.valueOf(due), parent, state, note,
					Timestamp.from(created), author, Timestamp.from(updated), executor,
					archived == null ? null : Timestamp.from(archived), archived == null ? null : id("archiver"));
		}

		private void coExecutor(String resolution, String user) {
			jdbc.update("insert into resolution_co_executor (resolution_id, user_id) values (?, ?)", id(resolution),
					id(user));
		}

		private void history(String resolution, int position, UUID actor, String from, String to, String comment,
				Instant at, UUID causedBy) {
			jdbc.update("insert into resolution_history (resolution_id, position, actor_id, from_state, to_state,"
					+ " comment, occurred_at, caused_by_resolution_id) values (?, ?, ?, ?, ?, ?, ?, ?)",
					id(resolution), position, actor, from, to, comment, Timestamp.from(at), causedBy);
		}

		private void reminder(String resolution, String threshold, LocalDate due, Instant sent) {
			jdbc.update("insert into resolution_reminder (resolution_id, threshold, due_date, sent_at) values (?, ?, ?,"
					+ " ?)", id(resolution), threshold, Date.valueOf(due), Timestamp.from(sent));
		}

	}

}
