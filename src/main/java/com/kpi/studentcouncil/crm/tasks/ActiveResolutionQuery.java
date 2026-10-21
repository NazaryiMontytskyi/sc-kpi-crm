package com.kpi.studentcouncil.crm.tasks;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Public API of the {@code tasks} module: read-only listing of resolutions that are still open, for other modules
 * (the due-date reminders of {@code notifications}).
 */
public interface ActiveResolutionQuery {

	/**
	 * @param id            resolution id
	 * @param taskId        task the resolution belongs to
	 * @param authorId      author of the resolution
	 * @param executorId    executor
	 * @param coExecutorIds co-executors (never contains the executor)
	 * @param dueDate       due date, a calendar day in Europe/Kyiv (the resolution is due by the end of that day)
	 */
	record ActiveResolution(UUID id, UUID taskId, UUID authorId, UUID executorId, Set<UUID> coExecutorIds,
			LocalDate dueDate) {
	}

	/**
	 * Non-archived resolutions that are neither ACCEPTED nor CANCELED and whose due date is on or before
	 * {@code latestDueDate}, ordered by due date then id.
	 */
	List<ActiveResolution> findActiveDueOnOrBefore(LocalDate latestDueDate);

}
