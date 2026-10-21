package com.kpi.studentcouncil.crm.knowledge.internal;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request and response shapes of the articles API. */
final class KnowledgeDtos {

	private KnowledgeDtos() {
	}

	/**
	 * {@code content} is a TipTap document ({@code {"type":"doc",...}}); missing means an empty page. Links and
	 * mentions are {@code mention} nodes with {@code attrs.id} and optional {@code attrs.entityType}
	 * ({@code person} by default, {@code article}, {@code task}); the server extracts them as references.
	 */
	record CreateArticleRequest(@NotBlank @Size(max = 300) String title, Map<String, Object> content,
			UUID parentArticleId, Boolean restricted, List<String> tags, List<UUID> attachmentIds) {
	}

	/** Replaces title, content, tags, attachments and the restricted flag (defaults to false). */
	record UpdateArticleRequest(@NotBlank @Size(max = 300) String title, Map<String, Object> content,
			Boolean restricted, List<String> tags, List<UUID> attachmentIds) {
	}

	/** {@code parentArticleId} null moves the article to the top level. */
	record MoveArticleRequest(UUID parentArticleId) {
	}

	record AttachmentResponse(UUID id, String fileName, String mimeType, long size) {
	}

	record ReferenceResponse(ReferenceType type, UUID id) {
	}

	record AncestorResponse(UUID id, String title) {
	}

	/** List item: no content. */
	record ArticleSummaryResponse(UUID id, String title, UUID parentArticleId, boolean restricted, int version,
			List<String> tags, boolean archived, Instant archivedAt, UUID createdBy, Instant createdAt,
			UUID updatedBy, Instant updatedAt) {
	}

	/** {@code ancestors}: visible ancestors, root first. */
	record ArticleResponse(UUID id, String title, Map<String, Object> content, UUID parentArticleId,
			boolean restricted, int version, List<String> tags, List<AttachmentResponse> attachments,
			List<ReferenceResponse> references, List<AncestorResponse> ancestors, boolean archived,
			Instant archivedAt, UUID createdBy, Instant createdAt, UUID updatedBy, Instant updatedAt) {
	}

	record TreeNodeResponse(UUID id, String title, boolean restricted, List<TreeNodeResponse> children) {
	}

	record TagResponse(String tag, long count) {
	}

}
