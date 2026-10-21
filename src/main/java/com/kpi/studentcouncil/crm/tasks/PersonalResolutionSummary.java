package com.kpi.studentcouncil.crm.tasks;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Public API of the {@code tasks} module: a compact summary of one person's resolutions for dashboards. The numbers
 * use the same rules as the personal lists ("My resolutions" and "Issued by me"), so counts match the lists.
 */
public interface PersonalResolutionSummary {

	/**
	 * @param active             resolutions the person executes in NEW, IN_PROGRESS, SUBMITTED or REWORK
	 * @param overdue            of those the person executes: due before today (Europe/Kyiv), not ACCEPTED/CANCELED
	 * @param awaitingAcceptance resolutions the person issued that are SUBMITTED (waiting for the author's decision)
	 * @param upcoming           the executed live resolutions with the earliest due dates (overdue ones come first)
	 */
	record Summary(long active, long overdue, long awaitingAcceptance, List<UpcomingItem> upcoming) {
	}

	/** One executed live resolution. */
	record UpcomingItem(UUID resolutionId, UUID taskId, String taskTitle, LocalDate dueDate, String state,
			boolean overdue) {
	}

	Summary summaryFor(UUID userId, int upcomingLimit);

}
