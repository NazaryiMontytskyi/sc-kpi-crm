package com.kpi.studentcouncil.crm.tasks.internal;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.kpi.studentcouncil.crm.shared.web.ApiV1;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.AssignTaskRequest;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.TaskAssigneeResponse;

/**
 * Assignees of a task (AS-1..AS-5). Reading needs {@code tasks.view}. Adding and removing need one of
 * {@code tasks.create}, {@code tasks.edit.any}, {@code tasks.assign}; the finer rule (self vs. another user) is
 * {@link AssigneeRules}: anybody else needs {@code tasks.assign}, oneself the creator with {@code tasks.create} or
 * {@code tasks.edit.any}.
 */
@ApiV1
@Tag(name = "Task assignees")
class TaskAssigneeController {

	private final TaskService service;

	TaskAssigneeController(TaskService service) {
		this.service = service;
	}

	@GetMapping("/tasks/{id}/assignees")
	@PreAuthorize("hasAuthority('tasks.view')")
	@Operation(summary = "Assignees of a task (oldest first; a task has few, so the list is not paginated)",
			description = "Active assignees only, unless includeRemoved=true (then removed assignments are listed "
					+ "too, with removedAt). Works for archived tasks.")
	List<TaskAssigneeResponse> list(@PathVariable UUID id,
			@RequestParam(defaultValue = "false") boolean includeRemoved) {
		return service.assignees(id, includeRemoved).stream().map(TaskAssigneeController::response).toList();
	}

	@PostMapping("/tasks/{id}/assignees")
	@PreAuthorize("hasAnyAuthority('tasks.create', 'tasks.edit.any', 'tasks.assign')")
	@Operation(summary = "Assign a user to a task",
			description = "Assigning another user needs tasks.assign; assigning oneself needs tasks.assign, "
					+ "tasks.edit.any, or tasks.create as the creator of the task (403 TASK_ASSIGN_FORBIDDEN). The "
					+ "user must be an active account (422 TASK_ASSIGNEE_INACTIVE); a user is an active assignee at "
					+ "most once (409 TASK_ASSIGNEE_DUPLICATE); 409 TASK_ARCHIVED for an archived task.")
	ResponseEntity<TaskAssigneeResponse> assign(@PathVariable UUID id, @Valid @RequestBody AssignTaskRequest request) {
		TaskAssignee created = service.assign(id, request.userId());
		return ResponseEntity.status(HttpStatus.CREATED)
				.location(URI.create("/api/v1/tasks/" + id + "/assignees/" + request.userId()))
				.body(response(created));
	}

	@DeleteMapping("/tasks/{id}/assignees/{userId}")
	@PreAuthorize("hasAnyAuthority('tasks.create', 'tasks.edit.any', 'tasks.assign')")
	@Operation(summary = "Remove an assignee from a task (the assignment is kept with removedAt)",
			description = "Same rights as assigning (403 TASK_ASSIGN_FORBIDDEN). 404 when the user is not an active "
					+ "assignee; 409 TASK_ARCHIVED for an archived task.")
	ResponseEntity<Void> unassign(@PathVariable UUID id, @PathVariable UUID userId) {
		service.unassign(id, userId);
		return ResponseEntity.noContent().build();
	}

	private static TaskAssigneeResponse response(TaskAssignee a) {
		return new TaskAssigneeResponse(a.getId(), a.getTaskId(), a.getUserId(), a.getAssignedBy(), a.getAssignedAt(),
				a.isActive(), a.getRemovedAt(), a.getRemovedBy());
	}

}
