package com.kpi.studentcouncil.crm.tasks;

import java.util.UUID;

/**
 * Public API of the {@code tasks} module: simple resolution counters of a person, for other modules (the member
 * profile). Counts cover the resolutions the person executes (as executor or co-executor); archived ones are excluded.
 */
public interface MemberResolutionCounts {

	/**
	 * @param active   NEW, IN_PROGRESS, SUBMITTED or REWORK
	 * @param overdue  due date before today (Europe/Kyiv) and state neither ACCEPTED nor CANCELED (CONTEXT.md 5.3)
	 * @param accepted ACCEPTED
	 */
	record Counts(long active, long overdue, long accepted) {
	}

	Counts countsFor(UUID userId);

}
