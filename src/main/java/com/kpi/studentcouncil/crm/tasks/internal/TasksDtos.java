package com.kpi.studentcouncil.crm.tasks.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
	 * is a rich-text (TipTap) JSON document. {@code customFieldValues} is keyed by custom field id.
	 */
	record CreateTaskRequest(@NotNull UUID spaceId, @NotBlank @Size(max = 300) String title,
			Map<String, Object> description, UUID statusId, TaskPriority priority, LocalDate dueDate,
			List<String> labels, Map<String, Object> customFieldValues, List<UUID> attachmentIds,
			List<UUID> relatedTaskIds) {
	}

	/** Replaces every editable field (the space and the status are not changed here). */
	record UpdateTaskRequest(@NotBlank @Size(max = 300) String title, Map<String, Object> description,
			TaskPriority priority, LocalDate dueDate, List<String> labels, Map<String, Object> customFieldValues,
			List<UUID> attachmentIds, List<UUID> relatedTaskIds) {
	}

	record ChangeTaskStatusRequest(@NotNull UUID statusId) {
	}

	record AttachmentResponse(UUID id, String fileName, String mimeType, long size) {
	}

	/** List item: no description, custom fields or attachment details. */
	/**
	 * {@code suggestDone}: computed hint that every resolution of the task is accepted (see SuggestDoneRule); the task
	 * is never moved automatically.
	 */
	record TaskSummaryResponse(UUID id, UUID spaceId, String title, UUID statusId, TaskPriority priority,
			LocalDate dueDate, List<String> labels, int attachmentCount, int relatedCount, UUID authorId,
			boolean archived, Instant archivedAt, boolean suggestDone, Instant createdAt, Instant updatedAt) {
	}

	record TaskResponse(UUID id, UUID spaceId, String title, Map<String, Object> description, UUID statusId,
			TaskPriority priority, LocalDate dueDate, List<String> labels, Map<String, Object> customFieldValues,
			List<AttachmentResponse> attachments, List<UUID> relatedTaskIds, UUID authorId, boolean archived,
			Instant archivedAt, boolean suggestDone, Instant createdAt, Instant updatedAt) {
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

	// ---- resolutions ----

	/** The caller becomes the author. Executor and co-executors must be ACTIVE users; the due date must not be in the past. */
	record IssueResolutionRequest(@NotNull UUID executorId, List<@NotNull UUID> coExecutorIds,
			@NotBlank @Size(max = 5000) String instruction, @NotNull LocalDate dueDate) {
	}

	/** Completion note, mandatory when submitting. */
	record SubmitResolutionRequest(@NotBlank @Size(max = 5000) String completionNote) {
	}

	/** Comment, mandatory when returning a result for rework. */
	record ReworkResolutionRequest(@NotBlank @Size(max = 5000) String comment) {
	}

	/** Optional reason of a cancel; recorded in the history of the resolution and of every canceled descendant. */
	record CancelResolutionRequest(@Size(max = 5000) String comment) {
	}

	record ResolutionHistoryResponse(UUID actorId, ResolutionState from, ResolutionState to, String comment,
			Instant at, UUID causedByResolutionId) {
	}

	/** List item: no completion note and no history. */
	record ResolutionSummaryResponse(UUID id, UUID taskId, UUID authorId, UUID executorId, List<UUID> coExecutorIds,
			String instruction, LocalDate dueDate, UUID parentResolutionId, ResolutionState state, boolean overdue,
			Instant createdAt, Instant updatedAt) {
	}

	/** Redirect: the caller (executor of the resolution) hands it over to a new executor with an own instruction and due date. */
	record RedirectResolutionRequest(@NotNull UUID executorId, @NotBlank @Size(max = 5000) String instruction,
			@NotNull LocalDate dueDate) {
	}

	/**
	 * Result of a redirect: the child and the parent (now REDIRECTED). {@code warnings} may contain
	 * {@code RESOLUTION_CHILD_DUE_AFTER_PARENT} when the child's due date is later than the parent's (not enforced).
	 */
	record RedirectResolutionResponse(ResolutionResponse child, ResolutionResponse parent, List<String> warnings) {
	}

	/** A node of the redirect chain tree; {@code children} are the resolutions redirected from it. */
	record ResolutionChainNode(UUID id, UUID taskId, UUID authorId, UUID executorId, List<UUID> coExecutorIds,
			String instruction, LocalDate dueDate, UUID parentResolutionId, ResolutionState state, boolean overdue,
			Instant createdAt, Instant updatedAt, List<ResolutionChainNode> children) {
	}

	record ResolutionResponse(UUID id, UUID taskId, UUID authorId, UUID executorId, List<UUID> coExecutorIds,
			String instruction, LocalDate dueDate, UUID parentResolutionId, ResolutionState state, boolean overdue,
			String completionNote, List<ResolutionHistoryResponse> history, Instant createdAt, Instant updatedAt) {
	}

}
