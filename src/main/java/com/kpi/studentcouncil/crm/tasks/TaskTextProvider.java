package com.kpi.studentcouncil.crm.tasks;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API of the {@code tasks} module for the {@code search} module: plain-text extraction of tasks for indexing.
 * Read-only; it applies no permission check, the index filters by permission when it answers a query.
 */
public interface TaskTextProvider {

	/** The task text, or empty when it does not exist. Archived tasks are returned (flagged). */
	Optional<TaskText> findTask(UUID taskId);

	/** Ids of the non-archived tasks the user is an active assignee of (to refresh them when the name changes). */
	List<UUID> findActiveTaskIdsAssignedTo(UUID userId);

	/** Ids of all non-archived tasks, for a full reindex. */
	List<UUID> findActiveTaskIds();

}
