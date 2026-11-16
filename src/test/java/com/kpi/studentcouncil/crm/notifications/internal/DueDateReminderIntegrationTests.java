package com.kpi.studentcouncil.crm.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
import com.kpi.studentcouncil.crm.tasks.ActiveTaskQuery;

/**
 * The deadline reminder job against a real database with a controllable clock and offsets 3d + 1d (NT-1..NT-5, spec
 * §9.8). The real {@link ActiveTaskQuery} is used, restricted to the tasks of this test so that open tasks left by
 * other test classes in the shared database are not reminded.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DueDateReminderIntegrationTests {

	private static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");
	private static final LocalDate DUE = LocalDate.of(2030, 6, 10); // due at 11 June 00:00 Kyiv

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
	ActiveTaskQuery query;
	@Autowired
	NotificationDispatcher dispatcher;
	@Autowired
	PlatformTransactionManager txManager;

	private final TestClock clock = new TestClock();
	private final List<UUID> users = new ArrayList<>();
	private final Set<UUID> tasks = new LinkedHashSet<>();
	private UUID space;
	private UUID todo;
	private UUID done;
	private UUID canceled;
	private UUID creator;
	private UUID assignee;
	private UUID coAssignee;
	private DueDateReminderService service;

	@BeforeEach
	void setUp() {
		ActiveTaskQuery scoped = new ActiveTaskQuery() {
			@Override
			public List<ActiveTask> findOpenDueOnOrBefore(LocalDate latestDeadline) {
				return query.findOpenDueOnOrBefore(latestDeadline).stream().filter(t -> tasks.contains(t.id()))
						.toList();
			}

			@Override
			public Set<UUID> activeAssigneeIds(UUID taskId) {
				return query.activeAssigneeIds(taskId);
			}
		};
		ReminderOffsetsProvider offsets = () -> List.of(Duration.ofDays(3), Duration.ofDays(1));
		service = new DueDateReminderService(scoped, dispatcher, jdbc, txManager, offsets, clock);
		space = UUID.randomUUID();
		jdbc.update("insert into task_space (id, name, is_general, created_at, updated_at) values (?, ?, true, now(),"
				+ " now())", space, "Reminder " + space);
		todo = status("Todo", "TODO", 0);
		done = status("Done", "DONE", 1);
		canceled = status("Canceled", "CANCELED", 2);
		creator = user();
		assignee = user();
		coAssignee = user();
	}

	@AfterEach
	void cleanUp() {
		for (UUID t : tasks) {
			jdbc.update("delete from task_reminder where task_id = ?", t);
			jdbc.update("delete from task_assignee where task_id = ?", t);
		}
		jdbc.update("delete from task where space_id = ?", space);
		jdbc.update("delete from task_space_status where space_id = ?", space);
		jdbc.update("delete from task_space where id = ?", space);
		for (UUID u : users) {
			jdbc.update("delete from notification where recipient_id = ?", u);
			jdbc.update("delete from users where id = ?", u);
		}
	}

	private UUID status(String name, String category, int position) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into task_space_status (id, space_id, name, category, position, created_at, updated_at)"
				+ " values (?, ?, ?, ?, ?, now(), now())", id, space, name, category, position);
		return id;
	}

	private UUID user() {
		UUID id = UUID.randomUUID();
		users.add(id);
		jdbc.update("insert into users (id, login, password_hash, status, locale, must_change_password, created_at,"
				+ " updated_at) values (?, ?, 'x', 'ACTIVE', 'en', false, now(), now())", id,
				"rem-" + id.toString().substring(0, 8));
		return id;
	}

	private UUID task(UUID status, LocalDate deadline, UUID creatorId, boolean archived, UUID... assignees) {
		UUID id = UUID.randomUUID();
		tasks.add(id);
		jdbc.update("insert into task (id, space_id, title, status_id, priority, deadline, created_at, updated_at,"
				+ " creator_id, issued_at, archived_at) values (?, ?, 'Fixture', ?, 'NORMAL', ?, now(), now(), ?, now(), "
				+ (archived ? "now()" : "null") + ")", id, space, status, deadline, creatorId);
		for (UUID a : assignees) {
			assign(id, a);
		}
		return id;
	}

	private UUID task(UUID... assignees) {
		return task(todo, DUE, creator, false, assignees);
	}

	private void assign(UUID task, UUID user) {
		jdbc.update("insert into task_assignee (id, task_id, user_id, assigned_by, assigned_at, created_at, updated_at)"
				+ " values (?, ?, ?, ?, now(), now(), now())", UUID.randomUUID(), task, user, creator);
	}

	private void at(int day, int hour) {
		clock.now = LocalDate.of(2030, 6, day).atTime(hour, 0).atZone(KYIV).toInstant();
	}

	private List<String> types(UUID user) {
		return jdbc.queryForList("select type from notification where recipient_id = ? order by created_at, id",
				String.class, user);
	}

	/** Offsets of the user's TASK_DUE_SOON notifications, oldest first. */
	private List<String> offsets(UUID user) {
		return jdbc.queryForList("select payload->>'offset' from notification where recipient_id = ? and type ="
				+ " 'TASK_DUE_SOON' order by created_at, id", String.class, user);
	}

	private List<String> reminderKeys(UUID task, UUID user) {
		return jdbc.queryForList("select threshold || '@' || deadline from task_reminder where task_id = ? and"
				+ " user_id = ? order by sent_at, threshold", String.class, task, user);
	}

	@Test
	void oneNotificationPerAssigneePerOffsetAndASecondRunSendsNothing() {
		UUID t = task(assignee, coAssignee);
		at(7, 12); // before the 3-day window (opens 8 June 00:00)
		assertThat(service.run()).isZero();

		at(8, 8);
		assertThat(service.run()).isEqualTo(1);
		assertThat(service.run()).isZero();
		at(10, 8);
		assertThat(service.run()).isEqualTo(1);
		assertThat(service.run()).isZero();

		for (UUID user : List.of(assignee, coAssignee)) {
			assertThat(types(user)).containsExactly("TASK_DUE_SOON", "TASK_DUE_SOON");
			assertThat(offsets(user)).containsExactly("PT72H", "PT24H");
			assertThat(reminderKeys(t, user)).containsExactly("DUE_SOON_PT72H@2030-06-10", "DUE_SOON_PT24H@2030-06-10");
		}
		assertThat(types(creator)).isEmpty(); // creator only for overdue when there are assignees
		assertThat(jdbc.queryForList("select distinct target_type || ':' || target_id from notification where"
				+ " recipient_id = ?", String.class, assignee)).containsExactly("TASK:" + t);
	}

	@Test
	void overdueGoesToAssigneesAndTheCreatorOnce() {
		task(assignee);
		at(11, 1);
		assertThat(service.run()).isEqualTo(1);
		assertThat(service.run()).isZero();
		at(15, 1);
		assertThat(service.run()).isZero();

		assertThat(types(assignee)).containsExactly("TASK_OVERDUE");
		assertThat(types(creator)).containsExactly("TASK_OVERDUE");
	}

	@Test
	void creatorWhoIsAlsoAssigneeGetsOverdueOnce() {
		task(todo, DUE, creator, false, creator, assignee);
		at(12, 1);
		service.run();
		assertThat(types(creator)).containsExactly("TASK_OVERDUE");
		assertThat(types(assignee)).containsExactly("TASK_OVERDUE");
	}

	@Test
	void firstSeenInsideTheOneDayWindowSendsOnlyTheOneDayReminder() {
		task(assignee);
		at(10, 12);
		assertThat(service.run()).isEqualTo(1);
		at(10, 20);
		assertThat(service.run()).isZero();
		assertThat(offsets(assignee)).containsExactly("PT24H");
	}

	@Test
	void firstSeenOverdueSendsExactlyOneOverdue() {
		task(assignee);
		at(12, 10);
		service.run();
		service.run();
		assertThat(types(assignee)).containsExactly("TASK_OVERDUE");
	}

	@Test
	void doneCanceledAndArchivedTasksAreIgnored() {
		UUID d = task(done, DUE, creator, false, assignee);
		UUID c = task(canceled, DUE, creator, false, assignee);
		UUID a = task(todo, DUE, creator, true, assignee);
		at(12, 10);
		assertThat(service.run()).isZero();
		assertThat(types(assignee)).isEmpty();
		assertThat(types(creator)).isEmpty();
		assertThat(query.findOpenDueOnOrBefore(DUE.plusDays(1))).extracting(ActiveTaskQuery.ActiveTask::id)
				.doesNotContain(d, c, a);
	}

	@Test
	void tasksWithoutDeadlineOrRemovedAssigneesAreNotReminded() {
		UUID noDeadline = task(todo, null, creator, false, assignee);
		UUID t = task(assignee, coAssignee);
		jdbc.update("update task_assignee set removed_at = now(), removed_by = ? where task_id = ? and user_id = ?",
				creator, t, coAssignee);
		at(10, 8);
		service.run();
		assertThat(types(assignee)).containsExactly("TASK_DUE_SOON");
		assertThat(types(coAssignee)).isEmpty();
		assertThat(query.findOpenDueOnOrBefore(DUE.plusYears(10))).extracting(ActiveTaskQuery.ActiveTask::id)
				.doesNotContain(noDeadline);
	}

	@Test
	void taskWithoutAssigneesRemindsItsCreator() {
		task();
		at(10, 8);
		service.run();
		at(11, 8);
		service.run();
		assertThat(types(creator)).containsExactly("TASK_DUE_SOON", "TASK_OVERDUE");
	}

	@Test
	void movedDeadlineReArmsTheReminders() {
		UUID t = task(assignee);
		at(10, 8);
		service.run();
		jdbc.update("update task set deadline = ? where id = ?", DUE.plusDays(5), t);
		at(13, 8); // 3-day window of 15 June
		assertThat(service.run()).isEqualTo(1);
		at(15, 8);
		assertThat(service.run()).isEqualTo(1);
		assertThat(service.run()).isZero();
		assertThat(offsets(assignee)).containsExactly("PT24H", "PT72H", "PT24H");
		assertThat(reminderKeys(t, assignee)).containsExactly("DUE_SOON_PT24H@2030-06-10",
				"DUE_SOON_PT72H@2030-06-15", "DUE_SOON_PT24H@2030-06-15");
	}

	@Test
	void anAssigneeAddedLaterStillGetsTheCurrentReminder() {
		UUID t = task(assignee);
		at(10, 8);
		service.run();
		assign(t, coAssignee);
		at(10, 9);
		assertThat(service.run()).isEqualTo(1);
		assertThat(types(assignee)).containsExactly("TASK_DUE_SOON");
		assertThat(types(coAssignee)).containsExactly("TASK_DUE_SOON");
	}

	@Test
	void remindersMigratedFromResolutionsPreventDuplicates() {
		// rows as written by V28: legacy keys DUE_SOON (executor, co-executor) and OVERDUE (plus the author)
		UUID late = user();
		UUID t = task(assignee, coAssignee);
		for (UUID user : List.of(assignee, coAssignee)) {
			legacy(t, user, "DUE_SOON");
			legacy(t, user, "OVERDUE");
		}
		legacy(t, creator, "OVERDUE");
		assign(t, late); // assigned after the cut-over: no legacy rows

		at(8, 8);
		service.run();
		at(10, 8);
		service.run();
		at(11, 8);
		service.run();

		assertThat(types(assignee)).isEmpty();
		assertThat(types(coAssignee)).isEmpty();
		assertThat(types(creator)).isEmpty();
		assertThat(types(late)).containsExactly("TASK_DUE_SOON", "TASK_DUE_SOON", "TASK_OVERDUE");
	}

	@Test
	void legacyReminderForAnOldDeadlineDoesNotSuppressTheNewOne() {
		UUID t = task(assignee);
		legacy(t, assignee, "DUE_SOON");
		jdbc.update("update task set deadline = ? where id = ?", DUE.plusDays(5), t);
		at(15, 8);
		service.run();
		assertThat(offsets(assignee)).containsExactly("PT24H");
	}

	@Test
	void oneFailingTaskDoesNotStopTheRun() {
		// an unknown creator without assignees: writing its reminder violates the users foreign key
		UUID broken = task(todo, DUE.minusDays(1), UUID.randomUUID(), false);
		task(assignee);
		at(12, 10);
		assertThat(service.run()).isEqualTo(1);
		assertThat(types(assignee)).containsExactly("TASK_OVERDUE");
		assertThat(jdbc.queryForObject("select count(*) from task_reminder where task_id = ?", Long.class, broken))
				.isZero();
	}

	private void legacy(UUID task, UUID user, String threshold) {
		jdbc.update("insert into task_reminder (task_id, user_id, threshold, deadline, sent_at) values (?, ?, ?, ?,"
				+ " now())", task, user, threshold, DUE);
	}

}
