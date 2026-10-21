package com.kpi.studentcouncil.crm.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import com.kpi.studentcouncil.crm.TestcontainersConfiguration;
import com.kpi.studentcouncil.crm.tasks.ActiveResolutionQuery;

/** The due-date reminder job against a real database with a controllable clock. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DueDateReminderIntegrationTests {

	private static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");
	private static final LocalDate DUE = LocalDate.of(2030, 6, 10);

	/** Mutable clock. */
	private static final class TestClock extends Clock {
		Instant now;

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}

	@Autowired
	JdbcTemplate jdbc;
	@Autowired
	ActiveResolutionQuery query;
	@Autowired
	NotificationDispatcher dispatcher;
	@Autowired
	PlatformTransactionManager txManager;

	private final TestClock clock = new TestClock();
	private final List<UUID> users = new ArrayList<>();
	private final List<UUID> resolutions = new ArrayList<>();
	private UUID space;
	private UUID statusId;
	private UUID task;
	private UUID author;
	private UUID executor;
	private UUID coExecutor;
	private DueDateReminderService service;

	@BeforeEach
	void setUp() {
		service = new DueDateReminderService(query, dispatcher, jdbc, txManager,
				new DueDateReminderProperties(false, Duration.ofHours(24), "0 0 * * * *"), clock);
		space = UUID.randomUUID();
		jdbc.update("insert into task_space (id, name, is_general, created_at, updated_at) values (?, ?, true, now(),"
				+ " now())", space, "Reminder " + space);
		statusId = UUID.randomUUID();
		jdbc.update("insert into task_space_status (id, space_id, name, category, position, created_at, updated_at)"
				+ " values (?, ?, 'Todo', 'TODO', 0, now(), now())", statusId, space);
		task = UUID.randomUUID();
		jdbc.update("insert into task (id, space_id, title, status_id, priority, created_at, updated_at) values (?, ?,"
				+ " 'Fixture', ?, 'NORMAL', now(), now())", task, space, statusId);
		author = user();
		executor = user();
		coExecutor = user();
	}

	@AfterEach
	void cleanUp() {
		for (UUID r : resolutions) {
			jdbc.update("delete from resolution_reminder where resolution_id = ?", r);
			jdbc.update("delete from resolution_co_executor where resolution_id = ?", r);
			jdbc.update("delete from resolution where id = ?", r);
		}
		jdbc.update("delete from task where id = ?", task);
		jdbc.update("delete from task_space_status where space_id = ?", space);
		jdbc.update("delete from task_space where id = ?", space);
		for (UUID u : users) {
			jdbc.update("delete from notification where recipient_id = ?", u);
			jdbc.update("delete from users where id = ?", u);
		}
	}

	private UUID user() {
		UUID id = UUID.randomUUID();
		users.add(id);
		jdbc.update("insert into users (id, login, password_hash, status, locale, must_change_password, created_at,"
				+ " updated_at) values (?, ?, 'x', 'ACTIVE', 'en', false, now(), now())", id,
				"rem-" + id.toString().substring(0, 8));
		return id;
	}

	private UUID resolution(String state, LocalDate due, boolean archived) {
		UUID id = UUID.randomUUID();
		resolutions.add(id);
		jdbc.update("insert into resolution (id, task_id, author_id, executor_id, instruction, due_date, state,"
				+ " created_at, updated_at, archived_at) values (?, ?, ?, ?, 'Do it', ?, ?, now(), now(), "
				+ (archived ? "now()" : "null") + ")", id, task, author, executor, due, state);
		jdbc.update("insert into resolution_co_executor (resolution_id, user_id) values (?, ?)", id, coExecutor);
		return id;
	}

	private void at(int day, int hour) {
		clock.now = LocalDate.of(2030, 6, day).atTime(hour, 0).atZone(KYIV).toInstant();
	}

	private List<String> types(UUID user) {
		return jdbc.queryForList("select type from notification where recipient_id = ? order by created_at, id",
				String.class, user);
	}

	@Test
	void dueSoonGoesToExecutorsOnlyAndIsSentOnce() {
		resolution("IN_PROGRESS", DUE, false);
		at(9, 12); // 36 h before the deadline: too early
		assertThat(service.run()).isZero();

		at(10, 8);
		assertThat(service.run()).isEqualTo(1);
		assertThat(service.run()).isZero(); // idempotent

		assertThat(types(executor)).containsExactly("RESOLUTION_DUE_SOON");
		assertThat(types(coExecutor)).containsExactly("RESOLUTION_DUE_SOON");
		assertThat(types(author)).isEmpty();
	}

	@Test
	void overdueGoesToExecutorsAndAuthorOnceAfterDueSoon() {
		resolution("REWORK", DUE, false);
		at(10, 8);
		service.run();
		at(11, 1);
		assertThat(service.run()).isEqualTo(1);
		assertThat(service.run()).isZero();
		at(15, 1);
		assertThat(service.run()).isZero();

		assertThat(types(executor)).containsExactly("RESOLUTION_DUE_SOON", "RESOLUTION_OVERDUE");
		assertThat(types(coExecutor)).containsExactly("RESOLUTION_DUE_SOON", "RESOLUTION_OVERDUE");
		assertThat(types(author)).containsExactly("RESOLUTION_OVERDUE");
	}

	@Test
	void firstSeenAlreadyOverdueSendsOnlyOverdue() {
		resolution("NEW", DUE, false);
		at(12, 10);
		service.run();
		assertThat(types(executor)).containsExactly("RESOLUTION_OVERDUE");
	}

	@Test
	void finalAndArchivedResolutionsAreIgnored() {
		resolution("ACCEPTED", DUE, false);
		resolution("CANCELED", DUE, false);
		resolution("NEW", DUE, true);
		at(12, 10);
		assertThat(service.run()).isZero();
		assertThat(types(executor)).isEmpty();
		assertThat(types(author)).isEmpty();
	}

	@Test
	void movedDueDateGetsRemindersAgain() {
		UUID id = resolution("IN_PROGRESS", DUE, false);
		at(10, 8);
		service.run();
		jdbc.update("update resolution set due_date = ? where id = ?", DUE.plusDays(5), id);
		at(15, 8);
		assertThat(service.run()).isEqualTo(1);
		assertThat(types(executor)).containsExactly("RESOLUTION_DUE_SOON", "RESOLUTION_DUE_SOON");
	}

}
