package com.kpi.studentcouncil.crm.members.internal;

import java.time.LocalDate;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** Response bodies of the member-profile task section (tasks via assignees, RR-5). */
final class MemberTaskDtos {

	private MemberTaskDtos() {
	}

	/**
	 * Task counters of a member: {@code active} (status neither DONE nor CANCELED category), {@code overdue} (active
	 * and the deadline is before today in Europe/Kyiv) and {@code done} (DONE category). Archived tasks never count.
	 */
	record MemberTaskCountsResponse(long active, long overdue, long done) {
	}

	/** One active task of a member. {@code parentId} is set for a subtask; {@code statusCategory} TODO/IN_PROGRESS. */
	record MemberTaskResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id, UUID parentId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID spaceId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID statusId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String statusName, String statusCategory,
			LocalDate deadline, boolean overdue) {
	}

}
