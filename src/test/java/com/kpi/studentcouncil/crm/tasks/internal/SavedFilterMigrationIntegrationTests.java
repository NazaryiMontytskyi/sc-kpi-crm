package com.kpi.studentcouncil.crm.tasks.internal;

import static org.assertj.core.api.Assertions.assertThat;

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
 * V27 (saved filters follow the assignment model) on a database stopped at V26 holding fake saved filters, in a
 * dedicated database of the shared container.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SavedFilterMigrationIntegrationTests {

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

	@Test
	void renamesTheMyResolutionsViewAndTheDueRangeAndKeepsEveryFilter() {
		DataSource ds = freshDatabase();
		Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("26").load().migrate();
		JdbcTemplate jdbc = new JdbcTemplate(ds);
		UUID owner = UUID.randomUUID();
		jdbc.update("insert into users (id, login, password_hash, status, locale, must_change_password, created_at,"
				+ " updated_at) values (?, 'fake.filter.owner', 'not-a-real-hash', 'ACTIVE', 'en', false, now(), now())",
				owner);
		UUID mine = filter(jdbc, owner, "Mine", "MY_RESOLUTIONS");
		UUID issued = filter(jdbc, owner, "Issued", "ISSUED_BY_ME");
		UUID plain = filter(jdbc, owner, "Plain", null);

		Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();

		Map<String, Object> row = jdbc.queryForMap("select quick_view, deadline_from::text as deadline_from,"
				+ " deadline_to::text as deadline_to, overdue, assignee_id, creator_id from task_saved_filter where id = ?",
				mine);
		assertThat(row).containsEntry("quick_view", "MY_TASKS").containsEntry("deadline_from", "2031-01-01")
				.containsEntry("deadline_to", "2031-02-01").containsEntry("overdue", false)
				.containsEntry("assignee_id", null).containsEntry("creator_id", null);
		assertThat(jdbc.queryForObject("select quick_view from task_saved_filter where id = ?", String.class, issued))
				.isEqualTo("ISSUED_BY_ME");
		assertThat(jdbc.queryForObject("select quick_view from task_saved_filter where id = ?", String.class, plain))
				.isNull();
		assertThat(jdbc.queryForObject("select count(*) from task_saved_filter", Long.class)).isEqualTo(3);
		assertThat(jdbc.queryForList("select column_name from information_schema.columns where table_schema = 'public'"
				+ " and table_name = 'task_saved_filter'", String.class)).doesNotContain("due_from", "due_to");
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
				"update task_saved_filter set quick_view = 'MY_RESOLUTIONS' where id = ?", plain))
				.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
	}

	private static UUID filter(JdbcTemplate jdbc, UUID owner, String name, String view) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into task_saved_filter (id, owner_id, name, quick_view, due_from, due_to, created_at,"
				+ " updated_at) values (?, ?, ?, ?, date '2031-01-01', date '2031-02-01', now(), now())", id, owner,
				name, view);
		return id;
	}

}
