package com.kpi.studentcouncil.crm.tasks.internal;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.kpi.studentcouncil.crm.shared.pagination.PageParams;
import com.kpi.studentcouncil.crm.shared.pagination.PageResponse;
import com.kpi.studentcouncil.crm.shared.web.ApiV1;
import com.kpi.studentcouncil.crm.tasks.MemberResolutionCounts;
import com.kpi.studentcouncil.crm.tasks.internal.MemberResolutionService.Item;
import com.kpi.studentcouncil.crm.tasks.internal.MemberResolutionService.Role;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.MemberResolutionCountsResponse;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.MemberResolutionResponse;

/**
 * Resolutions of a member for the profile and the personal lists. Member id equals user id. Reads need
 * {@code tasks.view} (openness by default): any such user sees any member's resolutions.
 */
@ApiV1
@Tag(name = "Resolutions")
class MemberResolutionController {

	private static final Set<String> SORTABLE = Set.of("createdAt", "updatedAt", "dueDate", "state");
	private static final Sort DEFAULT_SORT = Sort.by("dueDate").and(Sort.by("id"));

	private final MemberResolutionService service;
	private final Clock clock;

	MemberResolutionController(MemberResolutionService service, Clock clock) {
		this.service = service;
		this.clock = clock;
	}

	@GetMapping("/members/{id}/resolutions")
	@PreAuthorize("hasAuthority('tasks.view')")
	@Operation(summary = "Resolutions of a member (paginated, sortable by createdAt, updatedAt, dueDate, state; default "
			+ "earliest due date first)",
			description = "role=EXECUTOR (default) returns those the member executes as executor or co-executor, "
					+ "role=AUTHOR those the member issued. active=true keeps NEW, IN_PROGRESS, SUBMITTED and REWORK; "
					+ "state filters one state. Archived resolutions are excluded. 404 for an unknown member.")
	PageResponse<MemberResolutionResponse> list(@PathVariable UUID id, @Valid PageParams page,
			@RequestParam(defaultValue = "EXECUTOR") Role role, @RequestParam(defaultValue = "false") boolean active,
			@RequestParam(required = false) ResolutionState state) {
		return PageResponse.of(service.list(id, role, active, state, page.toPageable(SORTABLE, DEFAULT_SORT)),
				this::response);
	}

	@GetMapping("/members/{id}/resolutions/counts")
	@PreAuthorize("hasAuthority('tasks.view')")
	@Operation(summary = "Counters of the resolutions a member executes",
			description = "active (NEW, IN_PROGRESS, SUBMITTED, REWORK), overdue (due date passed in Europe/Kyiv, state not "
					+ "ACCEPTED/CANCELED) and accepted; archived resolutions are not counted.")
	MemberResolutionCountsResponse counts(@PathVariable UUID id) {
		MemberResolutionCounts.Counts counts = service.countsFor(id);
		return new MemberResolutionCountsResponse(counts.active(), counts.overdue(), counts.accepted());
	}

	private MemberResolutionResponse response(Item item) {
		Resolution r = item.resolution();
		return new MemberResolutionResponse(r.getId(), r.getTaskId(), item.taskTitle(), r.getAuthorId(),
				r.getExecutorId(), r.getCoExecutorIds().stream().sorted().toList(), r.getInstruction(), r.getDueDate(),
				r.getState(), r.isOverdue(clock), r.getCreatedAt(), r.getUpdatedAt());
	}

}
