package com.kpi.studentcouncil.crm.tasks;

import java.util.UUID;

/**
 * Public API of the {@code tasks} module: simple task counters of a person for the member profile (RR-5). Successor
 * of {@code MemberResolutionCounts}. Counts cover non-archived tasks (subtasks included) the person is an active
 * assignee of.
 */
public interface MemberTaskCounts {

	/**
	 * @param active  status neither in the DONE nor in the CANCELED category
	 * @param overdue active and the deadline is before today (Europe/Kyiv)
	 * @param done    status in the DONE category
	 */
	record Counts(long active, long overdue, long done) {
	}

	Counts countsFor(UUID userId);

}
