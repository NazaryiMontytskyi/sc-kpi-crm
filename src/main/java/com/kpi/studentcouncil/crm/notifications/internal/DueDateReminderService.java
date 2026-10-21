package com.kpi.studentcouncil.crm.notifications.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
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
import com.kpi.studentcouncil.crm.tasks.ActiveResolutionQuery;
import com.kpi.studentcouncil.crm.tasks.ActiveResolutionQuery.ActiveResolution;

/**
 * Sends "due soon" and "overdue" notifications for open resolutions.
 *
 * <p>A resolution is due by the end of its due date in Europe/Kyiv, i.e. at the start of the next Kyiv day (DST-aware).
 * "Due soon" is sent once the remaining time is at most the lead time; "overdue" once the deadline has passed. If the
 * job first sees a resolution already overdue, only "overdue" is sent. Each (resolution, threshold, due date) is
 * recorded in {@code resolution_reminder} in the same transaction as the notifications, so a run is idempotent.
 *
 * <p>Recipients: executor and co-executors for both thresholds; the author additionally for "overdue" (the author
 * has to decide whether to wait, redirect or cancel). ACCEPTED, CANCELED and archived resolutions are never
 * considered ({@link ActiveResolutionQuery}).
 */
@Service
class DueDateReminderService {

	static final ZoneId ZONE = ZoneId.of("Europe/Kyiv");
	static final String DUE_SOON = "DUE_SOON";
	static final String OVERDUE = "OVERDUE";

	private static final Logger log = LoggerFactory.getLogger(DueDateReminderService.class);

	private final ActiveResolutionQuery resolutions;
	private final NotificationDispatcher dispatcher;
	private final JdbcTemplate jdbc;
	private final TransactionTemplate tx;
	private final DueDateReminderProperties properties;
	private final Clock clock;

	DueDateReminderService(ActiveResolutionQuery resolutions, NotificationDispatcher dispatcher, JdbcTemplate jdbc,
			PlatformTransactionManager txManager, DueDateReminderProperties properties, Clock clock) {
		this.resolutions = resolutions;
		this.dispatcher = dispatcher;
		this.jdbc = jdbc;
		this.tx = new TransactionTemplate(txManager);
		this.properties = properties;
		this.clock = clock;
	}

	/** Runs one pass; returns the number of resolutions for which a reminder was sent. */
	int run() {
		Instant now = clock.instant();
		LocalDate latestDue = now.plus(properties.leadTime()).atZone(ZONE).toLocalDate();
		int sent = 0;
		for (ActiveResolution resolution : resolutions.findActiveDueOnOrBefore(latestDue)) {
			String threshold = threshold(resolution.dueDate(), now);
			if (threshold == null) {
				continue;
			}
			try {
				if (Boolean.TRUE.equals(tx.execute(status -> remind(resolution, threshold, now)))) {
					sent++;
				}
			}
			catch (RuntimeException e) {
				log.warn("Due-date reminder failed for resolution {}", resolution.id(), e);
			}
		}
		return sent;
	}

	/** Which threshold applies at {@code now}, or null if none yet. */
	String threshold(LocalDate dueDate, Instant now) {
		Instant deadline = dueDate.plusDays(1).atStartOfDay(ZONE).toInstant();
		if (!now.isBefore(deadline)) {
			return OVERDUE;
		}
		return now.isBefore(deadline.minus(properties.leadTime())) ? null : DUE_SOON;
	}

	private boolean remind(ActiveResolution resolution, String threshold, Instant now) {
		int inserted = jdbc.update("insert into resolution_reminder (resolution_id, threshold, due_date, sent_at)"
				+ " values (?, ?, ?, ?) on conflict do nothing", resolution.id(), threshold, resolution.dueDate(),
				java.sql.Timestamp.from(now));
		if (inserted == 0) {
			return false;
		}
		Set<UUID> recipients = new LinkedHashSet<>();
		recipients.add(resolution.executorId());
		recipients.addAll(resolution.coExecutorIds());
		boolean overdue = OVERDUE.equals(threshold);
		if (overdue) {
			recipients.add(resolution.authorId());
		}
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("taskId", resolution.taskId().toString());
		payload.put("resolutionId", resolution.id().toString());
		payload.put("dueDate", resolution.dueDate().toString());
		dispatcher.dispatch(recipients, null,
				overdue ? NotificationType.RESOLUTION_OVERDUE : NotificationType.RESOLUTION_DUE_SOON,
				new NotificationTarget("RESOLUTION", resolution.id()), payload);
		return true;
	}

}
