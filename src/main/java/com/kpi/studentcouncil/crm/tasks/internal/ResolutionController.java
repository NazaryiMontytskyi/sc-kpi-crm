package com.kpi.studentcouncil.crm.tasks.internal;

import java.net.URI;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.kpi.studentcouncil.crm.shared.pagination.PageParams;
import com.kpi.studentcouncil.crm.shared.pagination.PageResponse;
import com.kpi.studentcouncil.crm.shared.web.ApiV1;
import com.kpi.studentcouncil.crm.tasks.internal.ResolutionService.ResolutionFilter;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.CancelResolutionRequest;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.IssueResolutionRequest;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.ReworkResolutionRequest;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.ResolutionHistoryResponse;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.ResolutionResponse;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.ResolutionSummaryResponse;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.SubmitResolutionRequest;

/**
 * Resolutions. Issuing needs {@code resolutions.create} (any executor, no hierarchy check); reads need
 * {@code tasks.view} (openness by default). The lifecycle actions are open to every authenticated user and decided by
 * the state machine: only the executor starts, submits and resumes, only the author accepts, returns for rework and
 * cancels (403 with a stable code otherwise).
 */
@ApiV1
@Tag(name = "Resolutions")
class ResolutionController {

	private static final Set<String> SORTABLE = Set.of("createdAt", "updatedAt", "dueDate", "state");
	private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"));

	private final ResolutionService service;
	private final Clock clock;

	ResolutionController(ResolutionService service, Clock clock) {
		this.service = service;
		this.clock = clock;
	}

	@PostMapping("/tasks/{taskId}/resolutions")
	@PreAuthorize("hasAuthority('resolutions.create')")
	@Operation(summary = "Issue a resolution on a task",
			description = "The caller becomes the author; any executor is allowed (top-down, horizontal or bottom-up). "
					+ "Executor and co-executors must be ACTIVE users (422), the due date must not be in the past (422), "
					+ "409 when the task is archived. A task may have several resolutions. Starts in NEW.")
	ResponseEntity<ResolutionResponse> issue(@PathVariable UUID taskId,
			@Valid @RequestBody IssueResolutionRequest request) {
		Resolution created = service.create(taskId, request.executorId(), request.coExecutorIds(),
				request.instruction(), request.dueDate());
		return ResponseEntity.status(HttpStatus.CREATED).location(URI.create("/api/v1/resolutions/" + created.getId()))
				.body(detail(created));
	}

	@GetMapping("/tasks/{taskId}/resolutions")
	@PreAuthorize("hasAuthority('tasks.view')")
	@Operation(summary = "Resolutions of a task (paginated, sortable by createdAt, updatedAt, dueDate, state)")
	PageResponse<ResolutionSummaryResponse> listByTask(@PathVariable UUID taskId, @Valid PageParams page) {
		return PageResponse.of(service.list(new ResolutionFilter(taskId, null, null, null, false),
				page.toPageable(SORTABLE, DEFAULT_SORT)), this::summary);
	}

	@GetMapping("/resolutions")
	@PreAuthorize("hasAuthority('tasks.view')")
	@Operation(summary = "Resolutions (paginated, sortable by createdAt, updatedAt, dueDate, state; default newest first)",
			description = "Filters: taskId, executorId, authorId, state; mine=true returns those where the caller is the "
					+ "author, the executor or a co-executor.")
	PageResponse<ResolutionSummaryResponse> list(@Valid PageParams page, @RequestParam(required = false) UUID taskId,
			@RequestParam(required = false) UUID executorId, @RequestParam(required = false) UUID authorId,
			@RequestParam(required = false) ResolutionState state, @RequestParam(defaultValue = "false") boolean mine) {
		return PageResponse.of(service.list(new ResolutionFilter(taskId, executorId, authorId, state, mine),
				page.toPageable(SORTABLE, DEFAULT_SORT)), this::summary);
	}

	@GetMapping("/resolutions/{id}")
	@PreAuthorize("hasAuthority('tasks.view')")
	@Operation(summary = "Get a resolution with its full history")
	ResolutionResponse get(@PathVariable UUID id) {
		return detail(service.get(id));
	}

	@PostMapping("/resolutions/{id}/start")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "Executor accepts the resolution into work (NEW to IN_PROGRESS)",
			description = "403 RESOLUTION_NOT_EXECUTOR for anybody else, 422 RESOLUTION_TRANSITION_NOT_ALLOWED.")
	ResolutionResponse start(@PathVariable UUID id) {
		return detail(service.start(id));
	}

	@PostMapping("/resolutions/{id}/resume")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "Executor resumes work after rework (REWORK to IN_PROGRESS)")
	ResolutionResponse resume(@PathVariable UUID id) {
		return detail(service.resume(id));
	}

	@PostMapping("/resolutions/{id}/submit")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "Executor submits the result (IN_PROGRESS to SUBMITTED)",
			description = "The completion note is mandatory (422). 403 RESOLUTION_NOT_EXECUTOR for anybody else.")
	ResolutionResponse submit(@PathVariable UUID id, @Valid @RequestBody SubmitResolutionRequest request) {
		return detail(service.submit(id, request.completionNote()));
	}

	@PostMapping("/resolutions/{id}/accept")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "Author accepts the result (SUBMITTED to ACCEPTED)",
			description = "403 RESOLUTION_NOT_AUTHOR for anybody else.")
	ResolutionResponse accept(@PathVariable UUID id) {
		return detail(service.accept(id));
	}

	@PostMapping("/resolutions/{id}/rework")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "Author returns the result for rework (SUBMITTED to REWORK)",
			description = "The comment is mandatory (422). 403 RESOLUTION_NOT_AUTHOR for anybody else.")
	ResolutionResponse rework(@PathVariable UUID id, @Valid @RequestBody ReworkResolutionRequest request) {
		return detail(service.requestRework(id, request.comment()));
	}

	@PostMapping("/resolutions/{id}/cancel")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "Author cancels the resolution (any non-final state to CANCELED)",
			description = "Non-final resolutions redirected from it are canceled with it, atomically. 403 "
					+ "RESOLUTION_NOT_AUTHOR for anybody else.")
	ResolutionResponse cancel(@PathVariable UUID id,
			@RequestBody(required = false) @Valid CancelResolutionRequest request) {
		return detail(service.cancel(id, request == null ? null : request.comment()).get(0));
	}

	private ResolutionSummaryResponse summary(Resolution r) {
		return new ResolutionSummaryResponse(r.getId(), r.getTaskId(), r.getAuthorId(), r.getExecutorId(),
				r.getCoExecutorIds().stream().sorted().toList(), r.getInstruction(), r.getDueDate(),
				r.getParentResolutionId(), r.getState(), r.isOverdue(clock), r.getCreatedAt(), r.getUpdatedAt());
	}

	private ResolutionResponse detail(Resolution r) {
		return new ResolutionResponse(r.getId(), r.getTaskId(), r.getAuthorId(), r.getExecutorId(),
				r.getCoExecutorIds().stream().sorted().toList(), r.getInstruction(), r.getDueDate(),
				r.getParentResolutionId(), r.getState(), r.isOverdue(clock), r.getCompletionNote(),
				r.getHistory().stream().map(h -> new ResolutionHistoryResponse(h.actorId(), h.from(), h.to(),
						h.comment(), h.at(), h.causedByResolutionId())).toList(),
				r.getCreatedAt(), r.getUpdatedAt());
	}

}
