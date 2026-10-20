package com.kpi.studentcouncil.crm.tasks.internal;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.kpi.studentcouncil.crm.tasks.SpaceStatusUsageGuard;

/**
 * Wires tasks into the space status rules (INC-024 extension point): a status used by active tasks cannot be archived
 * without mapping its tasks to another status of the space.
 */
@Component
class TaskStatusUsageGuard implements SpaceStatusUsageGuard {

	private final TaskService tasks;

	TaskStatusUsageGuard(TaskService tasks) {
		this.tasks = tasks;
	}

	@Override
	public long countUsages(UUID spaceId, UUID statusId) {
		return tasks.countUsages(spaceId, statusId);
	}

	@Override
	public void reassign(UUID spaceId, UUID fromStatusId, UUID toStatusId) {
		tasks.reassignStatus(spaceId, fromStatusId, toStatusId);
	}

}
