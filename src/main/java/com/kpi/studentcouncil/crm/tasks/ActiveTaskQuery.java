package com.kpi.studentcouncil.crm.tasks;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Public API of the {@code tasks} module: read-only access to open tasks and their active assignees for other
 * modules (the deadline reminders and task notifications of {@code notifications}). Successor of the
 * resolution query ({@code ActiveResolutionQuery}). Exposes no entities.
 */
public interface ActiveTaskQuery {

	/**
	 * An open task: not archived and its status is neither in the DONE nor in the CANCELED category.
	 *
	 * @param id          task id
	 * @param parentId    the parent task when this is a subtask, else null
	 * @param creatorId   the creator (may be the nil system actor for legacy rows)
	 * @param deadline    a calendar day in Europe/Kyiv; the task is due by the end of that day
	 * @param assigneeIds active (not removed) assignees, oldest assignment first; may be empty
	 */
	record ActiveTask(UUID id, UUID parentId, UUID creatorId, LocalDate deadline, Set<UUID> assigneeIds) {

		public ActiveTask {
			assigneeIds = assigneeIds == null ? Set.of() : Set.copyOf(assigneeIds);
		}

	}

	/** Open tasks with a deadline on or before {@code latestDeadline}, ordered by deadline then id. */
	List<ActiveTask> findOpenDueOnOrBefore(LocalDate latestDeadline);

	/** Active assignees of the task (archived tasks too); empty for an unknown task. */
	Set<UUID> activeAssigneeIds(UUID taskId);

}
