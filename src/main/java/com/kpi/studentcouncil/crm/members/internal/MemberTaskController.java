package com.kpi.studentcouncil.crm.members.internal;

import java.util.Set;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.kpi.studentcouncil.crm.identity.UserDirectory;
import com.kpi.studentcouncil.crm.members.internal.MemberTaskDtos.MemberTaskCountsResponse;
import com.kpi.studentcouncil.crm.members.internal.MemberTaskDtos.MemberTaskResponse;
import com.kpi.studentcouncil.crm.shared.error.NotFoundException;
import com.kpi.studentcouncil.crm.shared.pagination.PageParams;
import com.kpi.studentcouncil.crm.shared.pagination.PageResponse;
import com.kpi.studentcouncil.crm.shared.web.ApiV1;
import com.kpi.studentcouncil.crm.tasks.MemberTaskCounts;
import com.kpi.studentcouncil.crm.tasks.PersonalTaskSummary;

/**
 * Tasks of a member for the profile (RR-5): counters and the list of active tasks the member is an active assignee
 * of. Read through the public {@code tasks} APIs only. Member id equals user id. Reads need {@code tasks.view}
 * (openness by default): any such user sees any member's tasks.
 */
@ApiV1
@RequestMapping("/members/{id}/tasks")
@Tag(name = "Members")
class MemberTaskController {

	private final MemberTaskCounts counts;
	private final PersonalTaskSummary tasks;
	private final UserDirectory users;

	MemberTaskController(MemberTaskCounts counts, PersonalTaskSummary tasks, UserDirectory users) {
		this.counts = counts;
		this.tasks = tasks;
		this.users = users;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('tasks.view')")
	@Operation(summary = "Active tasks of a member (paginated; earliest deadline first, no deadline last)",
			description = "Tasks and subtasks the member is an active assignee of whose status is neither in the DONE "
					+ "nor in the CANCELED category; archived tasks are excluded. The order is fixed (sort is not "
					+ "accepted). overdue = deadline before today in Europe/Kyiv. 404 for an unknown member.")
	PageResponse<MemberTaskResponse> list(@PathVariable UUID id, @ParameterObject @Valid PageParams page) {
		requireMember(id);
		return PageResponse.of(tasks.activeTasksOf(id, page.toPageable(Set.of(), Sort.unsorted())),
				i -> new MemberTaskResponse(i.taskId(), i.parentId(), i.spaceId(), i.title(), i.statusId(),
						i.statusName(), i.statusCategory() == null ? null : i.statusCategory().name(), i.deadline(),
						i.overdue()));
	}

	@GetMapping("/counts")
	@PreAuthorize("hasAuthority('tasks.view')")
	@Operation(summary = "Task counters of a member",
			description = "active (status neither DONE nor CANCELED category), overdue (active, deadline before today "
					+ "in Europe/Kyiv) and done (DONE category) over the non-archived tasks and subtasks the member is "
					+ "an active assignee of. 404 for an unknown member.")
	MemberTaskCountsResponse counts(@PathVariable UUID id) {
		requireMember(id);
		MemberTaskCounts.Counts c = counts.countsFor(id);
		return new MemberTaskCountsResponse(c.active(), c.overdue(), c.done());
	}

	private void requireMember(UUID id) {
		if (!users.exists(id)) {
			throw new NotFoundException("Member", id);
		}
	}

}
