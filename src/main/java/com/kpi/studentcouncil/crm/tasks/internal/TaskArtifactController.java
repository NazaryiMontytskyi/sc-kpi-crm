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

import com.kpi.studentcouncil.crm.files.FileInfo;
import com.kpi.studentcouncil.crm.shared.web.ApiV1;
import com.kpi.studentcouncil.crm.tasks.internal.TaskArtifactService.ArtifactView;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.AddTaskArtifactRequest;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.TaskArtifactResponse;

/**
 * Artifacts of a task (AR-1..AR-5): files and links. Reading needs {@code tasks.view}. Adding and removing need
 * {@code tasks.view} plus the contribute rule of {@link TaskEditPolicy}: the creator, an active assignee, a holder of
 * {@code tasks.edit.any} or an Admin.
 */
@ApiV1
@Tag(name = "Task artifacts")
class TaskArtifactController {

	private final TaskArtifactService service;

	TaskArtifactController(TaskArtifactService service) {
		this.service = service;
	}

	@GetMapping("/tasks/{id}/artifacts")
	@PreAuthorize("hasAuthority('tasks.view')")
	@Operation(summary = "Artifacts of a task (oldest first; a task has few, so the list is not paginated)",
			description = "Active artifacts only, unless includeArchived=true (then removed ones are listed too, with "
					+ "archivedAt). Works for archived tasks.")
	List<TaskArtifactResponse> list(@PathVariable UUID id,
			@RequestParam(defaultValue = "false") boolean includeArchived) {
		return service.list(id, includeArchived).stream().map(TaskArtifactController::response).toList();
	}

	@PostMapping("/tasks/{id}/artifacts")
	@PreAuthorize("hasAuthority('tasks.view')")
	@Operation(summary = "Attach a file or a link to a task",
			description = "FILE: fileId of an uploaded file (POST /files), no url or title. LINK: url (http or https "
					+ "with a host, at most 2048 characters, surrounding whitespace is stripped) and an optional "
					+ "title. Only the creator, an active assignee, a holder of tasks.edit.any or an Admin (403 "
					+ "TASK_CONTRIBUTE_FORBIDDEN). 422 TASK_ARTIFACT_INVALID (fields do not match the type), "
					+ "TASK_ARTIFACT_FILE_INVALID (file missing or archived), TASK_ARTIFACT_URL_INVALID, "
					+ "TASK_ARTIFACT_URL_TOO_LONG; 409 TASK_ARTIFACT_DUPLICATE (file already attached), 409 "
					+ "TASK_ARCHIVED.")
	ResponseEntity<TaskArtifactResponse> add(@PathVariable UUID id,
			@Valid @RequestBody AddTaskArtifactRequest request) {
		ArtifactView created = service.add(id, request.type(), request.fileId(), request.url(), request.title());
		return ResponseEntity.status(HttpStatus.CREATED)
				.location(URI.create("/api/v1/tasks/" + id + "/artifacts/" + created.artifact().getId()))
				.body(response(created));
	}

	@DeleteMapping("/tasks/{id}/artifacts/{artifactId}")
	@PreAuthorize("hasAuthority('tasks.view')")
	@Operation(summary = "Remove an artifact from a task (archived; an uploaded file stays in storage)",
			description = "Same rights as attaching (403 TASK_CONTRIBUTE_FORBIDDEN). 404 when the artifact does not "
					+ "belong to the task or is already removed; 409 TASK_ARCHIVED for an archived task.")
	ResponseEntity<Void> remove(@PathVariable UUID id, @PathVariable UUID artifactId) {
		service.remove(id, artifactId);
		return ResponseEntity.noContent().build();
	}

	static TaskArtifactResponse response(ArtifactView view) {
		TaskArtifact a = view.artifact();
		FileInfo file = view.file();
		return new TaskArtifactResponse(a.getId(), a.getTaskId(), a.getType(), view.name(), a.getFileId(),
				file != null, file == null ? null : file.mimeType(), file == null ? null : file.size(), a.getUrl(),
				a.getTitle(), a.getAddedBy(), a.getAddedAt(), a.isArchived(), a.getArchivedAt(), a.getArchivedBy());
	}

}
