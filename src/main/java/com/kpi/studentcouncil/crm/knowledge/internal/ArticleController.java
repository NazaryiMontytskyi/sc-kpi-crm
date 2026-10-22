package com.kpi.studentcouncil.crm.knowledge.internal;

import java.net.URI;
import java.util.Comparator;
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

import com.kpi.studentcouncil.crm.knowledge.internal.ArticleService.ArticleView;
import com.kpi.studentcouncil.crm.knowledge.internal.ArticleService.TreeNode;
import com.kpi.studentcouncil.crm.knowledge.internal.KnowledgeDtos.AncestorResponse;
import com.kpi.studentcouncil.crm.knowledge.internal.KnowledgeDtos.ArticleResponse;
import com.kpi.studentcouncil.crm.knowledge.internal.KnowledgeDtos.ArticleSummaryResponse;
import com.kpi.studentcouncil.crm.knowledge.internal.KnowledgeDtos.ArticleVersionResponse;
import com.kpi.studentcouncil.crm.knowledge.internal.KnowledgeDtos.ArticleVersionSummaryResponse;
import com.kpi.studentcouncil.crm.knowledge.internal.KnowledgeDtos.AttachmentResponse;
import com.kpi.studentcouncil.crm.knowledge.internal.KnowledgeDtos.CreateArticleRequest;
import com.kpi.studentcouncil.crm.knowledge.internal.KnowledgeDtos.MoveArticleRequest;
import com.kpi.studentcouncil.crm.knowledge.internal.KnowledgeDtos.ReferenceResponse;
import com.kpi.studentcouncil.crm.knowledge.internal.KnowledgeDtos.RestoreVersionRequest;
import com.kpi.studentcouncil.crm.knowledge.internal.KnowledgeDtos.TagResponse;
import com.kpi.studentcouncil.crm.knowledge.internal.KnowledgeDtos.TreeNodeResponse;
import com.kpi.studentcouncil.crm.knowledge.internal.KnowledgeDtos.UpdateArticleRequest;
import com.kpi.studentcouncil.crm.shared.pagination.PageParams;
import com.kpi.studentcouncil.crm.shared.pagination.PageResponse;
import com.kpi.studentcouncil.crm.shared.web.ApiV1;

/**
 * Knowledge base articles. Reads need {@code kb.view}, create/edit/move {@code kb.edit}, archive/restore
 * {@code kb.archive}. Restricted articles are invisible (404, absent from lists) without {@code kb.restricted.view}.
 */
@ApiV1
@Tag(name = "Knowledge base")
class ArticleController {

	private static final Set<String> SORTABLE = Set.of("title", "createdAt", "updatedAt");
	private static final Set<String> VERSION_SORTABLE = Set.of("versionNumber");
	private static final Sort VERSION_SORT = Sort.by(Sort.Direction.DESC, "versionNumber");
	private static final Sort DEFAULT_SORT = Sort.by("title").and(Sort.by("id"));

	private final ArticleService service;

	ArticleController(ArticleService service) {
		this.service = service;
	}

	@GetMapping("/articles")
	@PreAuthorize("hasAuthority('kb.view')")
	@Operation(summary = "Articles (paginated, sortable by title, createdAt, updatedAt; default title)",
			description = "Optional filters: tag (case-insensitive), parentArticleId. Archived articles only with "
					+ "includeArchived=true. Restricted articles are omitted without kb.restricted.view.")
	PageResponse<ArticleSummaryResponse> list(@ParameterObject @Valid PageParams page, @RequestParam(required = false) String tag,
			@RequestParam(required = false) UUID parentArticleId,
			@RequestParam(defaultValue = "false") boolean includeArchived) {
		return PageResponse.of(
				service.list(tag, parentArticleId, includeArchived, page.toPageable(SORTABLE, DEFAULT_SORT)),
				ArticleController::summary);
	}

	@GetMapping("/articles/tree")
	@PreAuthorize("hasAuthority('kb.view')")
	@Operation(summary = "Page tree of all active articles",
			description = "Nested, siblings ordered by title. Restricted articles (and the branches below them) are "
					+ "omitted without kb.restricted.view.")
	List<TreeNodeResponse> tree() {
		return service.tree().stream().map(ArticleController::node).toList();
	}

	@GetMapping("/articles/tags")
	@PreAuthorize("hasAuthority('kb.view')")
	@Operation(summary = "Tags in use with the number of active articles carrying them",
			description = "Only articles the caller may see are counted. Filter articles by tag with "
					+ "GET /articles?tag=.")
	List<TagResponse> tags() {
		return service.tags().stream().map(t -> new TagResponse(t.tag(), t.count())).toList();
	}

	@GetMapping("/articles/{id}")
	@PreAuthorize("hasAuthority('kb.view')")
	@Operation(summary = "Get an article with content, tags, attachments, references and ancestors",
			description = "404 for a restricted article without kb.restricted.view.")
	ArticleResponse get(@PathVariable UUID id) {
		return detail(service.get(id));
	}

	@GetMapping("/articles/{id}/children")
	@PreAuthorize("hasAuthority('kb.view')")
	@Operation(summary = "Active direct children of an article, ordered by title")
	List<ArticleSummaryResponse> children(@PathVariable UUID id) {
		return service.children(id).stream().map(ArticleController::summary)
				.sorted(Comparator.comparing(a -> a.title().toLowerCase())).toList();
	}

	@PostMapping("/articles")
	@PreAuthorize("hasAuthority('kb.edit')")
	@Operation(summary = "Create an article",
			description = "The parent (optional) must exist, be active and visible (422). Marking an article "
					+ "restricted needs kb.restricted.view (403). References are extracted from the content.")
	ResponseEntity<ArticleResponse> create(@Valid @RequestBody CreateArticleRequest request) {
		ArticleView created = service.create(request.title(), request.content(), request.parentArticleId(),
				Boolean.TRUE.equals(request.restricted()), request.tags(), request.attachmentIds());
		return ResponseEntity.status(HttpStatus.CREATED)
				.location(URI.create("/api/v1/articles/" + created.article().getId())).body(detail(created));
	}

	@PutMapping("/articles/{id}")
	@PreAuthorize("hasAuthority('kb.edit')")
	@Operation(summary = "Edit an article (replaces title, content, tags, attachments, restricted flag)",
			description = "expectedVersion must equal the current version, otherwise 409 ARTICLE_VERSION_CONFLICT. "
					+ "Every edit raises the version counter by one and stores exactly one version snapshot. "
					+ "409 ARTICLE_ARCHIVED when archived.")
	ArticleResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateArticleRequest request) {
		return detail(service.update(id, request.expectedVersion(), request.title(), request.content(), Boolean.TRUE.equals(request.restricted()),
				request.tags(), request.attachmentIds()));
	}

	@GetMapping("/articles/{id}/versions")
	@PreAuthorize("hasAuthority('kb.view')")
	@Operation(summary = "Version history of an article (paginated, newest first; sortable by versionNumber)",
			description = "404 for a restricted article without kb.restricted.view.")
	PageResponse<ArticleVersionSummaryResponse> versions(@PathVariable UUID id, @ParameterObject @Valid PageParams page) {
		return PageResponse.of(service.listVersions(id, page.toPageable(VERSION_SORTABLE, VERSION_SORT)),
				v -> new ArticleVersionSummaryResponse(v.getVersionNumber(), v.getTitle(), v.getCreatedBy(),
						v.getCreatedAt()));
	}

	@GetMapping("/articles/{id}/versions/{versionNumber}")
	@PreAuthorize("hasAuthority('kb.view')")
	@Operation(summary = "Read one version (title and content snapshot)")
	ArticleVersionResponse version(@PathVariable UUID id, @PathVariable int versionNumber) {
		ArticleVersion v = service.getVersion(id, versionNumber);
		return new ArticleVersionResponse(v.getVersionNumber(), v.getTitle(), v.getContent(), v.getCreatedBy(),
				v.getCreatedAt());
	}

	@PostMapping("/articles/{id}/versions/{versionNumber}/restore")
	@PreAuthorize("hasAuthority('kb.edit')")
	@Operation(summary = "Restore an old version as a NEW latest version",
			description = "The article takes the title and content of that version; tags, attachments and the "
					+ "restricted flag stay. Old versions are kept. Optional expectedVersion: 409 "
					+ "ARTICLE_VERSION_CONFLICT when the article changed meanwhile; 409 ARTICLE_ARCHIVED when archived.")
	ArticleResponse restoreVersion(@PathVariable UUID id, @PathVariable int versionNumber,
			@Valid @RequestBody(required = false) RestoreVersionRequest request) {
		return detail(service.restoreVersion(id, versionNumber, request == null ? null : request.expectedVersion()));
	}

	@PostMapping("/articles/{id}/move")
	@PreAuthorize("hasAuthority('kb.edit')")
	@Operation(summary = "Move an article under another parent (null = top level)",
			description = "422 ARTICLE_CYCLE when the new parent is the article itself or one of its descendants; "
					+ "422 ARTICLE_PARENT_INVALID when the parent is missing, archived or not visible.")
	ArticleResponse move(@PathVariable UUID id, @RequestBody MoveArticleRequest request) {
		return detail(service.move(id, request.parentArticleId()));
	}

	@PostMapping("/articles/{id}/archive")
	@PreAuthorize("hasAuthority('kb.archive')")
	@Operation(summary = "Archive an article (soft)",
			description = "409 ARTICLE_HAS_ACTIVE_CHILDREN while it still has active children: archive or move them "
					+ "first.")
	ArticleResponse archive(@PathVariable UUID id) {
		return detail(service.archive(id));
	}

	@PostMapping("/articles/{id}/restore")
	@PreAuthorize("hasAuthority('kb.archive')")
	@Operation(summary = "Restore an archived article", description = "409 ARTICLE_PARENT_ARCHIVED when its parent is archived.")
	ArticleResponse restore(@PathVariable UUID id) {
		return detail(service.restore(id));
	}

	private static ArticleSummaryResponse summary(Article a) {
		return new ArticleSummaryResponse(a.getId(), a.getTitle(), a.getParentArticleId(), a.isRestricted(),
				a.getVersion(), a.getTags().stream().sorted().toList(), a.isArchived(), a.getArchivedAt(),
				a.getCreatedBy(), a.getCreatedAt(), a.getUpdatedBy(), a.getUpdatedAt());
	}

	private static ArticleResponse detail(ArticleView v) {
		Article a = v.article();
		return new ArticleResponse(a.getId(), a.getTitle(), a.getContent(), a.getParentArticleId(), a.isRestricted(),
				a.getVersion(), a.getTags().stream().sorted().toList(),
				v.attachments().stream()
						.map(f -> new AttachmentResponse(f.id(), f.fileName(), f.mimeType(), f.size())).toList(),
				a.getReferences().stream()
						.sorted(Comparator.comparing((ArticleReference r) -> r.type()).thenComparing(ArticleReference::id))
						.map(r -> new ReferenceResponse(r.type(), r.id())).toList(),
				v.ancestors().stream().map(x -> new AncestorResponse(x.id(), x.title())).toList(), a.isArchived(),
				a.getArchivedAt(), a.getCreatedBy(), a.getCreatedAt(), a.getUpdatedBy(), a.getUpdatedAt());
	}

	private static TreeNodeResponse node(TreeNode n) {
		return new TreeNodeResponse(n.id(), n.title(), n.restricted(),
				n.children().stream().map(ArticleController::node).toList());
	}

}
