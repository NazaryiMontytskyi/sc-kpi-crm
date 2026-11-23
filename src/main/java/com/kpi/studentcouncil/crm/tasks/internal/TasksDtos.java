package com.kpi.studentcouncil.crm.tasks.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.kpi.studentcouncil.crm.tasks.CommentTargetType;
import com.kpi.studentcouncil.crm.tasks.CustomFieldType;
import com.kpi.studentcouncil.crm.tasks.StatusCategory;

/** Request and response shapes of the spaces API. */
final class TasksDtos {

	private TasksDtos() {
	}

	/** {@code general} defaults to false; a general space has no {@code orgUnitId}. */
	record CreateSpaceRequest(@NotBlank @Size(max = 200) String name, UUID orgUnitId, Boolean general) {
	}

	/** Replaces name, unit binding and general flag ({@code general} defaults to false). */
	record UpdateSpaceRequest(@NotBlank @Size(max = 200) String name, UUID orgUnitId, Boolean general) {
	}

	record SpaceSummaryResponse(UUID id, String name, UUID orgUnitId, boolean general, boolean archived,
			Instant archivedAt, Instant createdAt, Instant updatedAt) {
	}

	/** A space with its active statuses (in order) and active custom fields. */
	record SpaceResponse(UUID id, String name, UUID orgUnitId, boolean general, boolean archived, Instant archivedAt,
			Instant createdAt, Instant updatedAt, List<SpaceStatusResponse> statuses,
			List<CustomFieldResponse> customFields) {
	}

	record StatusRequest(@NotBlank @Size(max = 100) String name, @NotNull StatusCategory category) {
	}

	/** Every active status of the space, each exactly once, in the new order. */
	record ReorderStatusesRequest(@NotEmpty List<@NotNull UUID> statusIds) {
	}

	/** {@code replacementStatusId} is required while tasks still have the status. */
	record ArchiveStatusRequest(UUID replacementStatusId) {
	}

	record SpaceStatusResponse(UUID id, UUID spaceId, String name, StatusCategory category, int position,
			boolean archived, Instant createdAt, Instant updatedAt) {
	}

	/** {@code options} are required for SELECT and forbidden for the other types. */
	record CreateCustomFieldRequest(@NotBlank @Size(max = 100) String name, @NotNull CustomFieldType type,
			Boolean required, List<String> options) {
	}

	/** The type cannot change. {@code options} are required for SELECT fields. */
	record UpdateCustomFieldRequest(@NotBlank @Size(max = 100) String name, Boolean required, List<String> options) {
	}

	record CustomFieldResponse(UUID id, UUID spaceId, String name, CustomFieldType type, boolean required,
			List<String> options, boolean archived, Instant createdAt, Instant updatedAt) {
	}

	// ---- tasks ----

	/**
	 * {@code statusId} defaults to the first TODO status of the space, {@code priority} to NORMAL. {@code description}
	 * is a rich-text (TipTap) JSON document. {@code customFieldValues} is keyed by custom field id. {@code deadline} is
	 * due at the end of that day in Europe/Kyiv.
	 */
	record CreateTaskRequest(@NotNull UUID spaceId, @NotBlank @Size(max = 300) String title,
			Map<String, Object> description, UUID statusId, TaskPriority priority, LocalDate deadline,
			List<String> labels, Map<String, Object> customFieldValues, List<UUID> attachmentIds,
			List<UUID> relatedTaskIds) {
	}

	/**
	 * Replaces every editable field (the space and the status are not changed here). {@code expectedVersion} is
	 * optional: when given it must equal the current {@code version}, otherwise 409 TASK_VERSION_CONFLICT.
	 */
	record UpdateTaskRequest(@Min(0) Long expectedVersion, @NotBlank @Size(max = 300) String title,
			Map<String, Object> description, TaskPriority priority, LocalDate deadline, List<String> labels,
			Map<String, Object> customFieldValues, List<UUID> attachmentIds, List<UUID> relatedTaskIds) {
	}

	/** {@code expectedVersion}: optional optimistic-locking guard, as for {@link UpdateTaskRequest}. */
	record ChangeTaskStatusRequest(@NotNull UUID statusId, @Min(0) Long expectedVersion) {
	}

	record AttachmentResponse(UUID id, String fileName, String mimeType, long size) {
	}

	/** List item: no description, custom fields or attachment details. */
	/**
	 * {@code deadline} is due at the end of that day (Europe/Kyiv); {@code issuedAt} is the issue instant (UTC);
	 * {@code creatorId} issued the task ({@code authorId} is the same user, kept for compatibility); {@code parentId}
	 * is set for a subtask; {@code version} is the optimistic-locking version to send back as {@code expectedVersion}.
	 */
	/**
	 * Subtask progress (ST-4, ST-5): {@code subtaskCount} non-archived subtasks, {@code openSubtaskCount} of them not in
	 * a DONE/CANCELED-category status, {@code suggestClose} the hint that every non-archived, non-canceled subtask is
	 * done (at least one) while the task itself is not closed; the task is never closed automatically.
	 * {@code assigneeIds}: active assignees, oldest assignment first.
	 */
	record TaskSummaryResponse(UUID id, UUID spaceId, UUID parentId, String title, UUID statusId,
			TaskPriority priority, LocalDate deadline, Instant issuedAt, UUID creatorId, List<String> labels,
			int attachmentCount, int relatedCount, UUID authorId, boolean archived, Instant archivedAt,
			long version, Instant createdAt, Instant updatedAt, List<UUID> assigneeIds,
			int subtaskCount, int openSubtaskCount, boolean suggestClose) {
	}

	/**
	 * Field meanings as in {@link TaskSummaryResponse}. The detail also carries the active {@code assignees} and the
	 * {@code subtasks} (non-archived ones; for an archived task every subtask) and the active {@code artifacts} (oldest
	 * first), so one request opens the task (spec §7). {@code attachments} is the legacy view of the same files.
	 */
	record TaskResponse(UUID id, UUID spaceId, UUID parentId, String title, Map<String, Object> description,
			UUID statusId, TaskPriority priority, LocalDate deadline, Instant issuedAt, UUID creatorId,
			List<String> labels, Map<String, Object> customFieldValues, List<AttachmentResponse> attachments,
			List<UUID> relatedTaskIds, UUID authorId, boolean archived, Instant archivedAt,
			long version, Instant createdAt, Instant updatedAt, List<TaskAssigneeResponse> assignees,
			List<TaskSummaryResponse> subtasks, int subtaskCount, int openSubtaskCount, boolean suggestClose,
			List<TaskArtifactResponse> artifacts) {
	}

	/**
	 * A subtask of the task in the path (ST-1..ST-3); it inherits the parent's space. {@code assigneeIds}: omitted
	 * means "for myself" (the caller); an empty list creates it unassigned; anybody other than the caller needs
	 * {@code tasks.assign}. {@code deadline} must not be later than the parent's (no limit when the parent has none).
	 * Other fields as in {@link CreateTaskRequest}.
	 */
	record CreateSubtaskRequest(@NotBlank @Size(max = 300) String title, Map<String, Object> description,
			UUID statusId, TaskPriority priority, LocalDate deadline, List<String> labels,
			Map<String, Object> customFieldValues, @Size(max = 20) List<@NotNull UUID> assigneeIds) {
	}

	// ---- assignees ----

	record AssignTaskRequest(@NotNull UUID userId) {
	}

	/** An assignment; {@code active} is false once removed ({@code removedAt}/{@code removedBy} set). */
	record TaskAssigneeResponse(UUID id, UUID taskId, UUID userId, UUID assignedBy, Instant assignedAt,
			boolean active, Instant removedAt, UUID removedBy) {
	}

	// ---- artifacts ----

	/**
	 * A FILE artifact needs {@code fileId} (an uploaded file) and no {@code url}/{@code title}; a LINK artifact needs
	 * {@code url} (http or https with a host, at most 2048 characters) and may have a {@code title}.
	 */
	record AddTaskArtifactRequest(@NotNull TaskArtifactType type, UUID fileId, String url,
			@Size(max = 300) String title) {
	}

	/**
	 * An artifact of a task (AR-5). {@code name} is the file name, the link title or (untitled link) the URL; it is
	 * null only for a FILE whose file is no longer available ({@code fileAvailable} false). {@code mimeType} and
	 * {@code size} are set for available files only. {@code archived}/{@code archivedAt}/{@code archivedBy} describe a
	 * removed artifact.
	 */
	record TaskArtifactResponse(UUID id, UUID taskId, TaskArtifactType type, String name, UUID fileId,
			boolean fileAvailable, String mimeType, Long size, String url, String title, UUID addedBy,
			Instant addedAt, boolean archived, Instant archivedAt, UUID archivedBy) {
	}

	// ---- saved filters ----

	/**
	 * Task list criteria with the names of the list query parameters; every part is optional ({@code overdue}
	 * defaults to false). {@code view} is one of the built-in quick views.
	 */
	record SavedFilterCriteria(UUID spaceId, UUID statusId, List<String> labels, UUID assignee, UUID creator,
			Boolean overdue, TaskQuickView view, LocalDate deadlineFrom, LocalDate deadlineTo) {
	}

	/** Create or replace a saved filter of the caller. */
	record SaveFilterRequest(@NotBlank @Size(max = 100) String name, @NotNull @Valid SavedFilterCriteria criteria) {
	}

	record SavedFilterResponse(UUID id, String name, SavedFilterCriteria criteria, Instant createdAt,
			Instant updatedAt) {
	}

	// ---- comments ----

	/**
	 * {@code body} is a rich-text (TipTap) document; {@code mentionUserIds} must be exactly the ids of the mention nodes
	 * in the body.
	 */
	record CreateCommentRequest(@NotNull CommentTargetType targetType, @NotNull UUID targetId,
			@NotNull Map<String, Object> body, List<UUID> mentionUserIds, List<UUID> attachmentIds) {
	}

	/** Replaces body, mentions and attachments (the target never changes). */
	record EditCommentRequest(@NotNull Map<String, Object> body, List<UUID> mentionUserIds,
			List<UUID> attachmentIds) {
	}

	record CommentResponse(UUID id, CommentTargetType targetType, UUID targetId, UUID authorId,
			Map<String, Object> body, List<UUID> mentionUserIds, List<AttachmentResponse> attachments,
			boolean edited, Instant editedAt, boolean archived, Instant archivedAt, Instant createdAt,
			Instant updatedAt) {
	}

}
