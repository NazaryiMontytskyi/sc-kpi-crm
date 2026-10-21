package com.kpi.studentcouncil.crm.knowledge.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableEntity;

/** A knowledge base page. Pages form a hierarchy through {@code parentArticleId}; the hierarchy has no cycles. */
@Entity
@Table(name = "kb_article")
class Article extends ArchivableEntity {

	@Column(name = "title", nullable = false, length = 300)
	private String title;

	/** Rich-text document (TipTap JSON), never null. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "content", columnDefinition = "jsonb", nullable = false)
	private Map<String, Object> content;

	@Column(name = "parent_article_id")
	private UUID parentArticleId;

	@Column(name = "restricted", nullable = false)
	private boolean restricted;

	/** Save counter: starts at 1, grows by one on every edit. */
	@Column(name = "version", nullable = false)
	private int version = 1;

	@ElementCollection(fetch = FetchType.EAGER)
	@Fetch(FetchMode.SELECT)
	@BatchSize(size = 50)
	@CollectionTable(name = "kb_article_tag", joinColumns = @JoinColumn(name = "article_id"))
	@Column(name = "tag", nullable = false, length = 50)
	private Set<String> tags = new LinkedHashSet<>();

	@ElementCollection(fetch = FetchType.EAGER)
	@Fetch(FetchMode.SELECT)
	@BatchSize(size = 50)
	@CollectionTable(name = "kb_article_attachment", joinColumns = @JoinColumn(name = "article_id"))
	@OrderColumn(name = "position")
	@Column(name = "file_id", nullable = false)
	private List<UUID> attachmentIds = new ArrayList<>();

	@ElementCollection(fetch = FetchType.EAGER)
	@Fetch(FetchMode.SELECT)
	@BatchSize(size = 50)
	@CollectionTable(name = "kb_article_reference", joinColumns = @JoinColumn(name = "article_id"))
	private Set<ArticleReference> references = new LinkedHashSet<>();

	protected Article() {
	}

	Article(String title, Map<String, Object> content, UUID parentArticleId, boolean restricted, Set<String> tags,
			List<UUID> attachmentIds, Set<ArticleReference> references) {
		this.parentArticleId = parentArticleId;
		apply(title, content, restricted, tags, attachmentIds, references);
	}

	String getTitle() {
		return title;
	}

	Map<String, Object> getContent() {
		return content;
	}

	UUID getParentArticleId() {
		return parentArticleId;
	}

	boolean isRestricted() {
		return restricted;
	}

	int getVersion() {
		return version;
	}

	Set<String> getTags() {
		return new LinkedHashSet<>(tags);
	}

	List<UUID> getAttachmentIds() {
		return List.copyOf(attachmentIds);
	}

	Set<ArticleReference> getReferences() {
		return new LinkedHashSet<>(references);
	}

	/** Replaces the editable state and counts a new save. */
	void edit(String title, Map<String, Object> content, boolean restricted, Set<String> tags,
			List<UUID> attachmentIds, Set<ArticleReference> references) {
		apply(title, content, restricted, tags, attachmentIds, references);
		this.version++;
	}

	void moveTo(UUID parentArticleId) {
		this.parentArticleId = parentArticleId;
	}

	private void apply(String title, Map<String, Object> content, boolean restricted, Set<String> tags,
			List<UUID> attachmentIds, Set<ArticleReference> references) {
		this.title = title;
		this.content = content;
		this.restricted = restricted;
		this.tags.clear();
		this.tags.addAll(tags);
		this.attachmentIds.clear();
		this.attachmentIds.addAll(attachmentIds);
		this.references.clear();
		this.references.addAll(references);
	}

	/**
	 * State recorded in audit events. The content is not part of it (it is not small, and the version history keeps
	 * it), and the title of a restricted article is left out so that the object history cannot reveal it.
	 */
	Map<String, Object> auditSnapshot() {
		Map<String, Object> map = new LinkedHashMap<>();
		if (!restricted) {
			map.put("title", title);
		}
		map.put("parentArticleId", parentArticleId == null ? null : parentArticleId.toString());
		map.put("restricted", restricted);
		map.put("version", version);
		map.put("tags", tags.stream().sorted().toList());
		map.put("attachmentIds", attachmentIds.stream().map(UUID::toString).toList());
		map.put("references", references.stream().map(r -> r.type() + ":" + r.id()).sorted().toList());
		map.put("archivedAt", getArchivedAt() == null ? null : getArchivedAt().toString());
		return map;
	}

}
