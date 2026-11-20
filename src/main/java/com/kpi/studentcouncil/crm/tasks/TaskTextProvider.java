package com.kpi.studentcouncil.crm.tasks;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API of the {@code tasks} module for the {@code search} module: plain-text extraction of tasks for indexing.
 * Read-only; it applies no permission check, the index filters by permission when it answers a query.
 *
 * <p>The resolution methods are legacy: since INC-080 the index no longer holds resolutions (RR-5); they stay until
 * resolutions are removed (INC-081) and are not called by the index.
 */
public interface TaskTextProvider {

	/** The task text, or empty when it does not exist. Archived tasks are returned (flagged). */
	Optional<TaskText> findTask(UUID taskId);

	/** Ids of the non-archived tasks the user is an active assignee of (to refresh them when the name changes). */
	List<UUID> findActiveTaskIdsAssignedTo(UUID userId);

	/** Ids of all non-archived tasks, for a full reindex. */
	List<UUID> findActiveTaskIds();

	/**
	 * The resolution text, or empty when it does not exist.
	 *
	 * @deprecated resolutions are no longer indexed (INC-080); removed in INC-081
	 */
	@Deprecated(forRemoval = true)
	Optional<ResolutionText> findResolution(UUID resolutionId);

	/**
	 * Ids of all resolutions of a task (archived ones included).
	 *
	 * @deprecated resolutions are no longer indexed (INC-080); removed in INC-081
	 */
	@Deprecated(forRemoval = true)
	List<UUID> findResolutionIdsOfTask(UUID taskId);

	/**
	 * Ids of all non-archived resolutions.
	 *
	 * @deprecated resolutions are no longer indexed (INC-080); removed in INC-081
	 */
	@Deprecated(forRemoval = true)
	List<UUID> findActiveResolutionIds();

}
