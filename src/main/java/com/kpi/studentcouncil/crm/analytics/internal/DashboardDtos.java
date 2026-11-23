package com.kpi.studentcouncil.crm.analytics.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** Response bodies of the dashboard endpoints. */
final class DashboardDtos {

	private DashboardDtos() {
	}

	/**
	 * The caller's personal dashboard. {@code tasks} is the "My metrics" / "My tasks" section built on tasks via
	 * assignees (RR-5).
	 */
	record MyDashboardResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) MyTasks tasks) {
	}

	/**
	 * Tasks I am an active assignee of (subtasks included), excluding archived ones and those in a DONE or CANCELED
	 * status category. {@code upcoming}: up to 5 of them with a deadline, earliest first (overdue first).
	 */
	record MyTasks(long active, long overdue,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UpcomingTask> upcoming) {
	}

	/** One active task with a deadline. {@code statusCategory} is TODO or IN_PROGRESS. */
	record UpcomingTask(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID taskId, UUID parentId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID spaceId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID statusId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String statusName, String statusCategory,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate deadline, boolean overdue) {
	}

}
