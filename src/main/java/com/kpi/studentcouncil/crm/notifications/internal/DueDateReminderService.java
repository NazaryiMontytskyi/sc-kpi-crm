package com.kpi.studentcouncil.crm.notifications.internal;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.kpi.studentcouncil.crm.notifications.NotificationTarget;
import com.kpi.studentcouncil.crm.notifications.NotificationType;
import com.kpi.studentcouncil.crm.notifications.internal.ReminderThresholds.Threshold;
import com.kpi.studentcouncil.crm.tasks.ActiveTaskQuery;
import com.kpi.studentcouncil.crm.tasks.ActiveTaskQuery.ActiveTask;

/**
 * Sends {@code TASK_DUE_SOON} (one per configured offset) and {@code TASK_OVERDUE} notifications for open tasks
 * (TASK_TRACKING_FEATURE NT-1..NT-5).
 *
 * <p>Threshold selection and recipients are the pure rules of {@link ReminderThresholds}; offsets come only from
 * {@link ReminderOffsetsProvider}. Each (task, recipient, threshold, deadline) is recorded in {@code task_reminder} in
 * the same transaction as the notification, so a run is idempotent, a recipient added later still gets the current
 * reminder, and a changed deadline re-arms every threshold. Reminders migrated from resolutions (V28, legacy keys
 * {@code DUE_SOON}/{@code OVERDUE}) count as sent. Each task runs in its own transaction: a failure is logged and the
 * run continues with the next task.
 */
@Service
class DueDateReminderService {

	static final String TARGET_TYPE = "TASK";

	private static final Logger log = LoggerFactory.getLogger(DueDateReminderService.class);

	private static final String INSERT = "insert into task_reminder (task_id, user_id, threshold, deadline, sent_at)"
			+ " values (?, ?, ?, ?, ?) on conflict do nothing";

	/** Same insert, skipped when a legacy DUE_SOON row exists for the task, recipient and deadline. */
	private static final String INSERT_UNLESS_LEGACY = "insert into task_reminder (task_id, user_id, threshold,"
			+ " deadline, sent_at) select ?, ?, ?, ?, ? where not exists (select 1 from task_reminder where task_id = ?"
			+ " and user_id = ? and deadline = ? and threshold = '" + ReminderThresholds.LEGACY_DUE_SOON + "')"
			+ " on conflict do nothing";

	private final ActiveTaskQuery tasks;
	private final NotificationDispatcher dispatcher;
	private final JdbcTemplate jdbc;
	private final TransactionTemplate tx;
	private final ReminderOffsetsProvider offsets;
	private final Clock clock;

	DueDateReminderService(ActiveTaskQuery tasks, NotificationDispatcher dispatcher, JdbcTemplate jdbc,
			PlatformTransactionManager txManager, ReminderOffsetsProvider offsets, Clock clock) {
		this.tasks = tasks;
		this.dispatcher = dispatcher;
		this.jdbc = jdbc;
		this.tx = new TransactionTemplate(txManager);
		this.offsets = offsets;
		this.clock = clock;
	}

	/** Runs one pass; returns the number of tasks for which at least one reminder was sent. */
	int run() {
		Instant now = clock.instant();
		List<Duration> configured = offsets.offsets();
		int sent = 0;
		for (ActiveTask task : tasks.findOpenDueOnOrBefore(ReminderThresholds.latestDeadlineToScan(now, configured))) {
			Optional<Threshold> threshold = ReminderThresholds.select(task.deadline(), now, configured);
			if (threshold.isEmpty()) {
				continue;
			}
			try {
				if (Boolean.TRUE.equals(tx.execute(status -> remind(task, threshold.get(), now)))) {
					sent++;
				}
			}
			catch (RuntimeException e) {
				log.warn("Deadline reminder failed for task {}", task.id(), e);
			}
		}
		return sent;
	}

	private boolean remind(ActiveTask task, Threshold threshold, Instant now) {
		Set<UUID> notify = new LinkedHashSet<>();
		Timestamp sentAt = Timestamp.from(now);
		for (UUID user : ReminderThresholds.recipients(task.creatorId(), task.assigneeIds(), threshold.isOverdue())) {
			int inserted = threshold.coveredByLegacyDueSoon()
					? jdbc.update(INSERT_UNLESS_LEGACY, task.id(), user, threshold.key(), task.deadline(), sentAt,
							task.id(), user, task.deadline())
					: jdbc.update(INSERT, task.id(), user, threshold.key(), task.deadline(), sentAt);
			if (inserted > 0) {
				notify.add(user);
			}
		}
		if (notify.isEmpty()) {
			return false;
		}
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("taskId", task.id().toString());
		if (task.parentId() != null) {
			payload.put("parentTaskId", task.parentId().toString());
		}
		payload.put("deadline", task.deadline().toString());
		if (!threshold.isOverdue()) {
			payload.put("offset", threshold.offset().toString());
		}
		// system-triggered: no actor to suppress
		dispatcher.dispatch(notify, null, threshold.isOverdue() ? NotificationType.TASK_OVERDUE
				: NotificationType.TASK_DUE_SOON, new NotificationTarget(TARGET_TYPE, task.id()), payload);
		return true;
	}

}
