package com.kpi.studentcouncil.crm.notifications.internal;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.kpi.studentcouncil.crm.audit.ActorProvider;

/**
 * Pure rules of the deadline reminders (NT-1..NT-4): which threshold applies to a deadline at a given instant, who
 * receives it, and how thresholds are named in {@code task_reminder}.
 *
 * <p>A deadline is a calendar day in Europe/Kyiv; the task is due at the start of the next Kyiv day (DST-aware).
 * At {@code now}:
 * <ul>
 * <li>deadline passed -> {@code OVERDUE} only (never a late "due soon");</li>
 * <li>otherwise, of the offsets whose window has opened ({@code now >= due - offset}) only the most urgent (the
 * smallest) applies: a task first seen inside the 1-day window yields the 1-day reminder, never also the 3-day one
 * (no burst);</li>
 * <li>no window open yet -> nothing.</li>
 * </ul>
 *
 * <p>Threshold keys: {@code OVERDUE}, and {@code DUE_SOON_<ISO-8601 offset>} (e.g. {@code DUE_SOON_PT72H},
 * {@code DUE_SOON_PT24H}) per offset. Legacy keys migrated from {@code resolution_reminder} by V28 are
 * {@code DUE_SOON} and {@code OVERDUE}: {@code OVERDUE} is the same key, and a legacy {@code DUE_SOON} counts as sent
 * for every offset of at least {@link #LEGACY_LEAD_TIME} (see {@link Threshold#coveredByLegacyDueSoon()}).
 */
final class ReminderThresholds {

	static final ZoneId ZONE = ZoneId.of("Europe/Kyiv");

	static final String OVERDUE = "OVERDUE";

	/** Threshold key written by the resolution reminder job and migrated by V28. */
	static final String LEGACY_DUE_SOON = "DUE_SOON";

	/**
	 * Lead time of the resolution reminder job (its only configured value): a legacy {@code DUE_SOON} was sent when at
	 * most this much time was left.
	 */
	static final Duration LEGACY_LEAD_TIME = Duration.ofHours(24);

	private static final String DUE_SOON_PREFIX = "DUE_SOON_";

	private ReminderThresholds() {
	}

	/**
	 * A threshold to remind about.
	 *
	 * @param key    the {@code task_reminder.threshold} value
	 * @param offset the offset of a "due soon" threshold; null for "overdue"
	 */
	record Threshold(String key, Duration offset) {

		static Threshold overdue() {
			return new Threshold(OVERDUE, null);
		}

		static Threshold dueSoon(Duration offset) {
			return new Threshold(DUE_SOON_PREFIX + offset, offset);
		}

		boolean isOverdue() {
			return offset == null;
		}

		/**
		 * Whether a legacy {@code DUE_SOON} row for the same task, recipient and deadline means this reminder was
		 * already delivered. The legacy reminder was sent at most {@link #LEGACY_LEAD_TIME} before the deadline, i.e.
		 * after the window of every offset {@code >= LEGACY_LEAD_TIME} had opened, and it carried the same message;
		 * sending such an offset again would be a duplicate. A smaller offset is a new, more urgent reminder.
		 */
		boolean coveredByLegacyDueSoon() {
			return offset != null && offset.compareTo(LEGACY_LEAD_TIME) >= 0;
		}

	}

	/** The instant a deadline day ends: the start of the next day in Europe/Kyiv. */
	static Instant dueAt(LocalDate deadline) {
		return deadline.plusDays(1).atStartOfDay(ZONE).toInstant();
	}

	/**
	 * The threshold that applies to {@code deadline} at {@code now}, or empty if none yet.
	 *
	 * @param offsets distinct positive offsets in any order
	 */
	static Optional<Threshold> select(LocalDate deadline, Instant now, List<Duration> offsets) {
		Instant due = dueAt(deadline);
		if (!now.isBefore(due)) {
			return Optional.of(Threshold.overdue());
		}
		Duration mostUrgent = null;
		for (Duration offset : offsets) {
			boolean open = !now.isBefore(due.minus(offset));
			if (open && (mostUrgent == null || offset.compareTo(mostUrgent) < 0)) {
				mostUrgent = offset;
			}
		}
		return mostUrgent == null ? Optional.empty() : Optional.of(Threshold.dueSoon(mostUrgent));
	}

	/** The latest deadline day for which some threshold may apply at {@code now} (scan bound, inclusive). */
	static LocalDate latestDeadlineToScan(Instant now, List<Duration> offsets) {
		Duration largest = offsets.stream().max(Duration::compareTo).orElse(Duration.ZERO);
		return now.plus(largest).atZone(ZONE).toLocalDate();
	}

	/**
	 * Recipients of a reminder (NT-2): every active assignee; the creator additionally for "overdue"; the creator alone
	 * when the task has no assignees. The nil system actor (creator of some legacy rows) is never a recipient.
	 */
	static Set<UUID> recipients(UUID creatorId, Set<UUID> assigneeIds, boolean overdue) {
		Set<UUID> people = new LinkedHashSet<>();
		assigneeIds.stream().filter(ReminderThresholds::isPerson).forEach(people::add);
		if ((overdue || assigneeIds.isEmpty()) && isPerson(creatorId)) {
			people.add(creatorId);
		}
		return people;
	}

	private static boolean isPerson(UUID id) {
		return id != null && !ActorProvider.SYSTEM_ACTOR.equals(id);
	}

}
