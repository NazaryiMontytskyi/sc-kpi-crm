package com.kpi.studentcouncil.crm.analytics.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Response bodies of the dashboard endpoints. */
final class DashboardDtos {

	private DashboardDtos() {
	}

	/** The caller's personal dashboard. */
	record MyDashboardResponse(MyResolutions resolutions) {
	}

	/** Counters equal the "My resolutions" and "Issued by me" lists; upcoming is the head of "My resolutions". */
	record MyResolutions(long active, long overdue, long awaitingAcceptance, List<UpcomingResolution> upcoming) {
	}

	record UpcomingResolution(UUID resolutionId, UUID taskId, String taskTitle, LocalDate dueDate, String state,
			boolean overdue) {
	}

}
