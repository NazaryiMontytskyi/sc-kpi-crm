package com.kpi.studentcouncil.crm.tasks;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API of the {@code tasks} module for the {@code search} module: plain-text extraction of tasks and
 * resolutions for indexing. Read-only; it applies no permission check, the index filters by permission when it
 * answers a query.
 */
public interface TaskTextProvider {

	/** The task text, or empty when it does not exist. Archived tasks are returned (flagged). */
	Optional<TaskText> findTask(UUID taskId);

	/**
	 * The resolution text, or empty when it does not exist. A resolution of an archived task is flagged as archived,
	 * so it leaves the index together with its task.
	 */
	Optional<ResolutionText> findResolution(UUID resolutionId);

	/** Ids of all resolutions of a task (archived ones included). */
	List<UUID> findResolutionIdsOfTask(UUID taskId);

	/** Ids of all non-archived tasks, for a full reindex. */
	List<UUID> findActiveTaskIds();

	/** Ids of all non-archived resolutions, for a full reindex. */
	List<UUID> findActiveResolutionIds();

}
