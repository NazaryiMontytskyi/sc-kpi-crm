package com.kpi.studentcouncil.crm.tasks.internal;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Sort;
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
import com.kpi.studentcouncil.crm.tasks.CommentTargetType;
import com.kpi.studentcouncil.crm.tasks.internal.CommentService.CommentView;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.AttachmentResponse;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.CommentResponse;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.CreateCommentRequest;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.EditCommentRequest;

/**
 * Comments on tasks (and later resolutions). Writing needs authentication only (no comment permission in the
 * catalog); reading needs the view permission of the target; edit and archive are decided by {@link CommentPolicy}
 * (author or Admin).
 */
@ApiV1
@Tag(name = "Comments")
class CommentController {

	private static final Set<String> SORTABLE = Set.of("createdAt");
	private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by("id"));

	private final CommentService service;

	CommentController(CommentService service) {
		this.service = service;
	}

	@GetMapping("/comments")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "Comments of a record (paginated, oldest first)",
			description = "Needs the view permission of the target (tasks.view for TASK). Archived comments are "
					+ "hidden. 422 COMMENT_TARGET_UNSUPPORTED while the target type has no implementation.")
	PageResponse<CommentResponse> list(@ParameterObject @Valid PageParams page, @RequestParam CommentTargetType targetType,
			@RequestParam UUID targetId) {
		return PageResponse.of(service.list(targetType, targetId, page.toPageable(SORTABLE, DEFAULT_SORT)),
				CommentController::response);
	}

	@PostMapping("/comments")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "Add a comment",
			description = "Any authenticated user. The target must exist and accept comments (404 / 409). "
					+ "mentionUserIds must equal the mention nodes of the body; every mentioned user must be active "
					+ "(422 COMMENT_MENTION_INVALID). Attachments are ids of uploaded files.")
	ResponseEntity<CommentResponse> create(@Valid @RequestBody CreateCommentRequest request) {
		CommentView created = service.create(request.targetType(), request.targetId(), request.body(),
				request.mentionUserIds(), request.attachmentIds());
		return ResponseEntity.status(HttpStatus.CREATED)
				.location(URI.create("/api/v1/comments/" + created.comment().getId())).body(response(created));
	}

	@PutMapping("/comments/{id}")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "Edit a comment (replaces body, mentions and attachments)",
			description = "Only the comment author or an Admin (403 otherwise). 409 when the comment is archived. "
					+ "The previous version is kept in the audit history.")
	CommentResponse edit(@PathVariable UUID id, @Valid @RequestBody EditCommentRequest request) {
		return response(service.edit(id, request.body(), request.mentionUserIds(), request.attachmentIds()));
	}

	@PostMapping("/comments/{id}/archive")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "Archive a comment (soft delete)",
			description = "Only the comment author or an Admin (403 otherwise). Comments are never deleted.")
	CommentResponse archive(@PathVariable UUID id) {
		return response(service.archive(id));
	}

	private static CommentResponse response(CommentView view) {
		Comment c = view.comment();
		List<AttachmentResponse> attachments = view.attachments().stream()
				.map(f -> new AttachmentResponse(f.id(), f.fileName(), f.mimeType(), f.size())).toList();
		return new CommentResponse(c.getId(), c.getTargetType(), c.getTargetId(), c.getAuthorId(), c.getBody(),
				c.getMentionUserIds().stream().sorted().toList(), attachments, c.getEditedAt() != null,
				c.getEditedAt(), c.isArchived(), c.getArchivedAt(), c.getCreatedAt(), c.getUpdatedAt());
	}

}
