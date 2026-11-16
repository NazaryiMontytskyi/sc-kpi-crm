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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.kpi.studentcouncil.crm.shared.web.ApiV1;
import com.kpi.studentcouncil.crm.tasks.internal.TaskService.TaskView;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.CreateSubtaskRequest;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.TaskResponse;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.TaskSummaryResponse;

/**
 * Subtasks of a task (ST-1..ST-3). Reading needs {@code tasks.view}. Adding needs {@code tasks.view} (the caller can
 * view the parent) and {@code tasks.create}; adding it for anybody other than oneself additionally needs
 * {@code tasks.assign} ({@link AssigneeRules}).
 */
@ApiV1
@Tag(name = "Task subtasks")
class TaskSubtaskController {

	private final TaskService service;

	TaskSubtaskController(TaskService service) {
		this.service = service;
	}

	@GetMapping("/tasks/{id}/subtasks")
	@PreAuthorize("hasAuthority('tasks.view')")
	@Operation(summary = "Subtasks of a task (oldest issue first; one level, few per task, so not paginated)",
			description = "Non-archived subtasks only, unless includeArchived=true. Works for archived tasks. The "
					+ "paginated task list offers the same set with the parentId filter.")
	List<TaskSummaryResponse> listSubtasks(@PathVariable UUID id,
			@RequestParam(defaultValue = "false") boolean includeArchived) {
		return service.subtasks(id, includeArchived).stream().map(TaskController::summary).toList();
	}

	@PostMapping("/tasks/{id}/subtasks")
	@PreAuthorize("hasAuthority('tasks.view') and hasAuthority('tasks.create')")
	@Operation(summary = "Add a subtask to a task",
			description = "The subtask lives in the parent's space; the caller becomes its creator. assigneeIds "
					+ "omitted: the subtask is for the caller; an empty list: unassigned; anybody else needs "
					+ "tasks.assign (403 TASK_ASSIGN_FORBIDDEN). Assignees must be active (422 TASK_ASSIGNEE_INACTIVE). "
					+ "A subtask cannot have subtasks (422 SUBTASK_DEPTH_EXCEEDED); its deadline must not be later than "
					+ "the parent's (422 SUBTASK_DEADLINE_AFTER_PARENT; no limit when the parent has none). 409 "
					+ "TASK_ARCHIVED / SPACE_ARCHIVED for an archived parent or space.")
	ResponseEntity<TaskResponse> createSubtask(@PathVariable UUID id,
			@Valid @RequestBody CreateSubtaskRequest request) {
		TaskView created = service.createSubtask(id, request.title(), request.description(), request.statusId(),
				request.priority(), request.deadline(), request.labels(), request.customFieldValues(),
				request.assigneeIds());
		return ResponseEntity.status(HttpStatus.CREATED)
				.location(URI.create("/api/v1/tasks/" + created.task().getId())).body(TaskController.detail(created));
	}

}
