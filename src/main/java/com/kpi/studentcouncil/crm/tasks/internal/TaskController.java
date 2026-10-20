package com.kpi.studentcouncil.crm.tasks.internal;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.kpi.studentcouncil.crm.shared.pagination.PageParams;
import com.kpi.studentcouncil.crm.shared.pagination.PageResponse;
import com.kpi.studentcouncil.crm.shared.web.ApiV1;
import com.kpi.studentcouncil.crm.tasks.internal.TaskService.TaskFilter;
import com.kpi.studentcouncil.crm.tasks.internal.TaskService.TaskView;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.AttachmentResponse;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.ChangeTaskStatusRequest;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.CreateTaskRequest;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.TaskResponse;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.TaskSummaryResponse;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.UpdateTaskRequest;

/**
 * Tasks. Reads need {@code tasks.view} (openness by default), creating {@code tasks.create}, archiving and restoring
 * {@code tasks.archive}. Editing and status changes are decided by {@link TaskEditPolicy} (author or Admin).
 */
@ApiV1
@Tag(name = "Tasks")
class TaskController {

	private static final Set<String> SORTABLE = Set.of("createdAt", "updatedAt", "dueDate", "title");
	private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"));

	private final TaskService service;

	TaskController(TaskService service) {
		this.service = service;
	}

	@GetMapping("/tasks")
	@PreAuthorize("hasAuthority('tasks.view')")
	@Operation(summary = "Tasks (paginated, sortable by createdAt, updatedAt, dueDate, title; default newest first)",
			description = "Archived tasks only with includeArchived=true. Filters: spaceId, statusId, labels (repeat "
					+ "the parameter; a task matches when it has any of them), dueFrom/dueTo (inclusive dates). "
						+ "Resolution filters executorId (executor or co-executor), authorId, resolutionState and view "
						+ "(MY_RESOLUTIONS, ISSUED_BY_ME, OVERDUE, evaluated for the caller) match tasks having a "
						+ "non-archived resolution that satisfies all of them at once.")
	PageResponse<TaskSummaryResponse> list(@Valid PageParams page, @RequestParam(required = false) UUID spaceId,
			@RequestParam(required = false) UUID statusId, @RequestParam(required = false) List<String> labels,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
			@RequestParam(defaultValue = "false") boolean includeArchived,
				@RequestParam(required = false) UUID executorId, @RequestParam(required = false) UUID authorId,
				@RequestParam(required = false) ResolutionState resolutionState,
				@RequestParam(required = false) TaskQuickView view) {
		TaskFilter filter = new TaskFilter(spaceId, statusId, labels, dueFrom, dueTo, includeArchived, executorId,
				authorId, resolutionState, view);
		return PageResponse.of(service.list(filter, page.toPageable(SORTABLE, DEFAULT_SORT)),
				TaskController::summary);
	}

	@GetMapping("/tasks/{id}")
	@PreAuthorize("hasAuthority('tasks.view')")
	@Operation(summary = "Get a task (archived ones too)")
	TaskResponse get(@PathVariable UUID id) {
		return detail(service.get(id));
	}

	@PostMapping("/tasks")
	@PreAuthorize("hasAuthority('tasks.create')")
	@Operation(summary = "Create a task",
			description = "The status must belong to the space (default: its first TODO status); custom field values "
					+ "are validated by type against the space definition (422); attachments are ids of uploaded "
					+ "files; related tasks must exist and not be archived. The caller becomes the author.")
	ResponseEntity<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request) {
		TaskView created = service.create(request.spaceId(), request.title(), request.description(), request.statusId(),
				request.priority(), request.dueDate(), request.labels(), request.customFieldValues(),
				request.attachmentIds(), request.relatedTaskIds());
		return ResponseEntity.status(HttpStatus.CREATED).location(URI.create("/api/v1/tasks/" + created.task().getId()))
				.body(detail(created));
	}

	@PutMapping("/tasks/{id}")
	@PreAuthorize("hasAuthority('tasks.view')")
	@Operation(summary = "Edit a task (replaces all editable fields)",
			description = "Only the task author or an Admin (403 otherwise). 409 when the task is archived. The "
					+ "relatedTaskIds list is the complete new set of symmetric links.")
	TaskResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTaskRequest request) {
		return detail(service.update(id, request.title(), request.description(), request.priority(), request.dueDate(),
				request.labels(), request.customFieldValues(), request.attachmentIds(), request.relatedTaskIds()));
	}

	@PostMapping("/tasks/{id}/status")
	@PreAuthorize("hasAuthority('tasks.view')")
	@Operation(summary = "Change the status of a task",
			description = "Same rule as editing: the author or an Admin (403 otherwise). The status must be an active "
					+ "status of the task's space (422).")
	TaskResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeTaskStatusRequest request) {
		return detail(service.changeStatus(id, request.statusId()));
	}

	@PostMapping("/tasks/{id}/archive")
	@PreAuthorize("hasAuthority('tasks.archive')")
	@Operation(summary = "Archive a task (soft)")
	TaskResponse archive(@PathVariable UUID id) {
		return detail(service.archive(id));
	}

	@PostMapping("/tasks/{id}/restore")
	@PreAuthorize("hasAuthority('tasks.archive')")
	@Operation(summary = "Restore an archived task", description = "409 while its space is archived.")
	TaskResponse restore(@PathVariable UUID id) {
		return detail(service.restore(id));
	}

	private static TaskResponse detail(TaskView view) {
		Task t = view.task();
		List<AttachmentResponse> attachments = view.attachments() == null ? List.of()
				: view.attachments().stream()
						.map(f -> new AttachmentResponse(f.id(), f.fileName(), f.mimeType(), f.size())).toList();
		return new TaskResponse(t.getId(), t.getSpaceId(), t.getTitle(), t.getDescription(), t.getStatusId(),
				t.getPriority(), t.getDueDate(), t.getLabels().stream().sorted().toList(), t.getCustomFieldValues(),
				attachments, view.relatedTaskIds().stream().sorted().toList(), t.getAuthorId(), t.isArchived(),
				t.getArchivedAt(), view.suggestDone(), t.getCreatedAt(), t.getUpdatedAt());
	}

	private static TaskSummaryResponse summary(TaskView view) {
		Task t = view.task();
		return new TaskSummaryResponse(t.getId(), t.getSpaceId(), t.getTitle(), t.getStatusId(), t.getPriority(),
				t.getDueDate(), t.getLabels().stream().sorted().toList(), t.getAttachmentIds().size(),
				view.relatedTaskIds().size(), t.getAuthorId(), t.isArchived(), t.getArchivedAt(), view.suggestDone(),
				t.getCreatedAt(), t.getUpdatedAt());
	}

}
