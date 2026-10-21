package com.kpi.studentcouncil.crm.knowledge.internal;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.audit.AuditAction;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.files.FileInfo;
import com.kpi.studentcouncil.crm.files.FileService;
import com.kpi.studentcouncil.crm.knowledge.ArticleEvent;
import com.kpi.studentcouncil.crm.knowledge.ArticleText;
import com.kpi.studentcouncil.crm.shared.domain.ArchivableSpecs;
import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;
import com.kpi.studentcouncil.crm.shared.error.ConflictException;
import com.kpi.studentcouncil.crm.shared.error.ForbiddenException;
import com.kpi.studentcouncil.crm.shared.error.NotFoundException;

/**
 * Commands and queries of articles. Restricted articles behave as non-existent (404) for an actor without
 * {@code kb.restricted.view}, and are left out of lists, the tree, tag counts and text extraction. Every change of
 * the hierarchy takes a database-wide advisory lock, so two concurrent moves cannot form a cycle.
 */
@Service
class ArticleService {

	static final String ARTICLE = "Article";
	static final String ARTICLE_VERSION = "ArticleVersion";

	/** An article with the data that lives outside its row. */
	record ArticleView(Article article, List<FileInfo> attachments, List<Ancestor> ancestors) {
	}

	record Ancestor(UUID id, String title) {
	}

	/** A node of the page tree; only visible, active articles. */
	record TreeNode(UUID id, String title, boolean restricted, List<TreeNode> children) {
	}

	record TagUsage(String tag, long count) {
	}

	private final ArticleRepository articles;
	private final ArticleVersionRepository versions;
	private final ArticleViewer viewer;
	private final FileService files;
	private final AuditPublisher audit;
	private final ActorProvider actors;
	private final ApplicationEventPublisher events;
	private final Clock clock;

	ArticleService(ArticleRepository articles, ArticleVersionRepository versions, ArticleViewer viewer, FileService files, AuditPublisher audit,
			ActorProvider actors, ApplicationEventPublisher events, Clock clock) {
		this.articles = articles;
		this.versions = versions;
		this.viewer = viewer;
		this.files = files;
		this.audit = audit;
		this.actors = actors;
		this.events = events;
		this.clock = clock;
	}

	// ---- commands ----

	@Transactional
	ArticleView create(String title, Map<String, Object> content, UUID parentArticleId, boolean restricted,
			Collection<String> tags, Collection<UUID> attachmentIds) {
		assertMayMark(restricted);
		if (parentArticleId != null) {
			lockUsableParent(parentArticleId);
		}
		Map<String, Object> doc = ArticleRules.normalizeContent(content);
		List<UUID> attachments = ArticleRules.normalizeAttachments(attachmentIds);
		attachments.forEach(this::assertFileUsable);
		Article saved = articles.save(new Article(title.strip(), doc, parentArticleId, restricted,
				ArticleRules.normalizeTags(tags), attachments, ArticleRules.extractReferences(doc)));
		snapshot(saved);
		audit.publish(AuditAction.CREATE, ARTICLE, saved.getId(), null, saved.auditSnapshot());
		publish(ArticleEvent.Kind.CREATED, saved);
		return view(saved);
	}

	/**
	 * Replaces title, content, tags, attachments and the restricted flag; counts a new save and snapshots it.
	 * {@code expectedVersion} is the version the caller edited; a different current version is a conflict (409).
	 */
	@Transactional
	ArticleView update(UUID id, int expectedVersion, String title, Map<String, Object> content, boolean restricted,
			Collection<String> tags, Collection<UUID> attachmentIds) {
		Article article = lockActive(id);
		assertVersion(article, expectedVersion);
		if (restricted != article.isRestricted()) {
			assertMayMark(restricted);
		}
		Map<String, Object> doc = ArticleRules.normalizeContent(content);
		List<UUID> attachments = ArticleRules.normalizeAttachments(attachmentIds);
		Set<UUID> kept = new HashSet<>(article.getAttachmentIds());
		attachments.stream().filter(a -> !kept.contains(a)).forEach(this::assertFileUsable);
		Map<String, Object> before = article.auditSnapshot();
		article.edit(title.strip(), doc, restricted, ArticleRules.normalizeTags(tags), attachments,
				ArticleRules.extractReferences(doc));
		Article saved = articles.save(article);
		snapshot(saved);
		audit.publish(AuditAction.UPDATE, ARTICLE, id, before, saved.auditSnapshot());
		publish(ArticleEvent.Kind.UPDATED, saved);
		return view(saved);
	}

	/**
	 * Restores an old version: the article takes that version's title and content (tags, attachments and the
	 * restricted flag stay) and a NEW version is appended; no existing version changes.
	 */
	@Transactional
	ArticleView restoreVersion(UUID id, int versionNumber, Integer expectedVersion) {
		Article article = lockActive(id);
		if (expectedVersion != null) {
			assertVersion(article, expectedVersion);
		}
		ArticleVersion old = versions.findByArticleIdAndVersionNumber(id, versionNumber)
				.orElseThrow(() -> new NotFoundException(ARTICLE_VERSION, versionNumber));
		Map<String, Object> before = article.auditSnapshot();
		article.edit(old.getTitle(), old.getContent(), article.isRestricted(), article.getTags(),
				article.getAttachmentIds(), ArticleRules.extractReferences(old.getContent()));
		Article saved = articles.save(article);
		snapshot(saved);
		audit.publish(AuditAction.UPDATE, ARTICLE, id, before, saved.auditSnapshot());
		publish(ArticleEvent.Kind.UPDATED, saved);
		return view(saved);
	}

	/** Moves an article under another parent (null = top level). Refuses a move under itself or a descendant. */
	@Transactional
	ArticleView move(UUID id, UUID newParentId) {
		articles.lockHierarchy();
		Article article = lockActive(id);
		if (Objects.equals(article.getParentArticleId(), newParentId)) {
			return view(article);
		}
		if (newParentId != null) {
			if (ArticleRules.wouldCreateCycle(id, newParentId, this::parentOf)) {
				throw new BusinessRuleException("ARTICLE_CYCLE");
			}
			lockUsableParent(newParentId);
		}
		Map<String, Object> before = article.auditSnapshot();
		article.moveTo(newParentId);
		Article saved = articles.save(article);
		audit.publish(AuditAction.UPDATE, ARTICLE, id, before, saved.auditSnapshot());
		publish(ArticleEvent.Kind.MOVED, saved);
		return view(saved);
	}

	/** Archives an article; refused while it still has active children. */
	@Transactional
	ArticleView archive(UUID id) {
		Article article = lockVisible(id);
		if (article.isArchived()) {
			throw new ConflictException("ALREADY_ARCHIVED");
		}
		long children = articles.countByParentArticleIdAndArchivedAtIsNull(id);
		if (children > 0) {
			throw new ConflictException("ARTICLE_HAS_ACTIVE_CHILDREN", children);
		}
		Map<String, Object> before = article.auditSnapshot();
		article.archive(actors.currentActorId(), clock.instant());
		Article saved = articles.save(article);
		audit.publish(AuditAction.ARCHIVE, ARTICLE, id, before, saved.auditSnapshot());
		publish(ArticleEvent.Kind.ARCHIVED, saved);
		return view(saved);
	}

	@Transactional
	ArticleView restore(UUID id) {
		articles.lockHierarchy();
		Article article = lockVisible(id);
		if (!article.isArchived()) {
			throw new ConflictException("NOT_ARCHIVED");
		}
		UUID parentId = article.getParentArticleId();
		if (parentId != null && articles.findById(parentId).map(Article::isArchived).orElse(true)) {
			throw new ConflictException("ARTICLE_PARENT_ARCHIVED");
		}
		Map<String, Object> before = article.auditSnapshot();
		article.restore();
		Article saved = articles.save(article);
		audit.publish(AuditAction.RESTORE, ARTICLE, id, before, saved.auditSnapshot());
		publish(ArticleEvent.Kind.RESTORED, saved);
		return view(saved);
	}

	// ---- queries ----

	/** An article the actor may see (archived ones included); 404 otherwise, also for a restricted one. */
	@Transactional(readOnly = true)
	ArticleView get(UUID id) {
		return view(getVisible(id));
	}

	/** Versions of a visible article (archived ones too). */
	@Transactional(readOnly = true)
	Page<ArticleVersion> listVersions(UUID id, Pageable pageable) {
		getVisible(id);
		return versions.findByArticleId(id, pageable);
	}

	@Transactional(readOnly = true)
	ArticleVersion getVersion(UUID id, int versionNumber) {
		getVisible(id);
		return versions.findByArticleIdAndVersionNumber(id, versionNumber)
				.orElseThrow(() -> new NotFoundException(ARTICLE_VERSION, versionNumber));
	}

	/** Flat list; archived articles only with {@code includeArchived}. Optional filters: tag, parent. */
	@Transactional(readOnly = true)
	Page<Article> list(String tag, UUID parentArticleId, boolean includeArchived, Pageable pageable) {
		Specification<Article> spec = visibility();
		if (!includeArchived) {
			spec = spec.and(ArchivableSpecs.notArchived());
		}
		if (tag != null && !tag.isBlank()) {
			String normalized = tag.strip().toLowerCase(Locale.ROOT);
			spec = spec.and((root, query, cb) -> cb.isMember(normalized, root.<Set<String>>get("tags")));
		}
		if (parentArticleId != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("parentArticleId"), parentArticleId));
		}
		return articles.findAll(spec, pageable);
	}

	/** Active, visible direct children of a visible article, by title. */
	@Transactional(readOnly = true)
	List<Article> children(UUID id) {
		getVisible(id);
		Specification<Article> spec = visibility().and(ArchivableSpecs.notArchived())
				.and((root, query, cb) -> cb.equal(root.get("parentArticleId"), id));
		return articles.findAll(spec, Pageable.unpaged(Sort.by("title").and(Sort.by("id")))).getContent();
	}

	/**
	 * The whole page tree of active articles the actor may see. A visible article below a hidden one is not reachable
	 * from the tree (the hidden parent hides its branch).
	 */
	@Transactional(readOnly = true)
	List<TreeNode> tree() {
		List<ArticleRepository.TreeRow> rows = articles.treeRows(viewer.canViewRestricted());
		Map<UUID, List<ArticleRepository.TreeRow>> byParent = new LinkedHashMap<>();
		for (ArticleRepository.TreeRow row : rows) {
			byParent.computeIfAbsent(row.getParentArticleId(), k -> new ArrayList<>()).add(row);
		}
		return build(null, byParent);
	}

	@Transactional(readOnly = true)
	List<TagUsage> tags() {
		return articles.tagCounts(viewer.canViewRestricted()).stream()
				.map(t -> new TagUsage(t.getTag(), t.getCount())).toList();
	}

	/** Searchable text of an article, or empty when the actor may not see it. */
	@Transactional(readOnly = true)
	Optional<ArticleText> findText(UUID id) {
		return articles.findById(id).filter(viewer::canSee).map(a -> new ArticleText(a.getId(), a.getTitle(),
				ArticleRules.extractPlainText(a.getContent()), a.getTags(), a.isRestricted(), a.isArchived()));
	}

	/** Text of an article for the search index; no visibility check (the index filters by the restricted flag). */
	@Transactional(readOnly = true)
	Optional<ArticleText> findTextForIndexing(UUID id) {
		return articles.findById(id).map(a -> new ArticleText(a.getId(), a.getTitle(),
				ArticleRules.extractPlainText(a.getContent()), a.getTags(), a.isRestricted(), a.isArchived()));
	}

	/** Ids of all non-archived articles, restricted ones included. */
	@Transactional(readOnly = true)
	List<UUID> findActiveIds() {
		return articles.findByArchivedAtIsNull().stream().map(Article::getId).toList();
	}

	// ---- helpers ----

	private void snapshot(Article saved) {
		versions.save(new ArticleVersion(saved.getId(), saved.getVersion(), saved.getTitle(), saved.getContent()));
	}

	private static void assertVersion(Article article, int expectedVersion) {
		if (article.getVersion() != expectedVersion) {
			throw new ConflictException("ARTICLE_VERSION_CONFLICT", article.getVersion());
		}
	}

	private List<TreeNode> build(UUID parentId, Map<UUID, List<ArticleRepository.TreeRow>> byParent) {
		List<TreeNode> nodes = new ArrayList<>();
		for (ArticleRepository.TreeRow row : byParent.getOrDefault(parentId, List.of())) {
			nodes.add(new TreeNode(row.getId(), row.getTitle(), row.getRestricted(), build(row.getId(), byParent)));
		}
		return nodes;
	}

	private Specification<Article> visibility() {
		if (viewer.canViewRestricted()) {
			return (root, query, cb) -> cb.conjunction();
		}
		return (root, query, cb) -> cb.isFalse(root.get("restricted"));
	}

	private Article getVisible(UUID id) {
		return articles.findById(id).filter(viewer::canSee).orElseThrow(() -> new NotFoundException(ARTICLE, id));
	}

	private Article lockVisible(UUID id) {
		return articles.lockById(id).filter(viewer::canSee).orElseThrow(() -> new NotFoundException(ARTICLE, id));
	}

	private Article lockActive(UUID id) {
		Article article = lockVisible(id);
		if (article.isArchived()) {
			throw new ConflictException("ARTICLE_ARCHIVED");
		}
		return article;
	}

	/** The parent must be an active article the actor can see; it is locked so it cannot be archived meanwhile. */
	private void lockUsableParent(UUID parentId) {
		Article parent = articles.lockById(parentId).filter(viewer::canSee)
				.orElseThrow(() -> new BusinessRuleException("ARTICLE_PARENT_INVALID"));
		if (parent.isArchived()) {
			throw new BusinessRuleException("ARTICLE_PARENT_INVALID");
		}
	}

	private UUID parentOf(UUID id) {
		return articles.findById(id).map(Article::getParentArticleId).orElse(null);
	}

	/** Marking an article restricted needs kb.restricted.view: nobody locks themselves out of their own page. */
	private void assertMayMark(boolean restricted) {
		if (!viewer.canViewRestricted() && restricted) {
			throw new ForbiddenException("ARTICLE_RESTRICTED_FORBIDDEN");
		}
	}

	private void assertFileUsable(UUID fileId) {
		try {
			files.require(fileId);
		}
		catch (NotFoundException e) {
			throw new BusinessRuleException("ARTICLE_ATTACHMENT_INVALID");
		}
	}

	private ArticleView view(Article article) {
		List<FileInfo> attachments = article.getAttachmentIds().stream().map(files::find)
				.flatMap(Optional::stream).toList();
		return new ArticleView(article, attachments, ancestors(article));
	}

	/** Visible ancestors, root first; the chain stops at the first missing or hidden one. */
	private List<Ancestor> ancestors(Article article) {
		List<Ancestor> chain = new ArrayList<>();
		Set<UUID> seen = new HashSet<>();
		UUID current = article.getParentArticleId();
		while (current != null && seen.add(current)) {
			Optional<Article> parent = articles.findById(current).filter(viewer::canSee);
			if (parent.isEmpty()) {
				break;
			}
			chain.add(0, new Ancestor(current, parent.get().getTitle()));
			current = parent.get().getParentArticleId();
		}
		return chain;
	}

	private void publish(ArticleEvent.Kind kind, Article article) {
		events.publishEvent(new ArticleEvent(kind, article.getId(), article.getParentArticleId(),
				article.isRestricted()));
	}

}
