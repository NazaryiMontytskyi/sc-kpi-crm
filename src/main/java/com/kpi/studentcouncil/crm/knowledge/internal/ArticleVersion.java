package com.kpi.studentcouncil.crm.knowledge.internal;

import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.kpi.studentcouncil.crm.shared.domain.BaseEntity;

/**
 * Immutable snapshot of an article taken on every save. {@code createdBy}/{@code createdAt} are the editor and the
 * time of the save. Never updated or deleted.
 */
@Entity
@Table(name = "kb_article_version")
class ArticleVersion extends BaseEntity {

	@Column(name = "article_id", nullable = false, updatable = false)
	private UUID articleId;

	@Column(name = "version_number", nullable = false, updatable = false)
	private int versionNumber;

	@Column(name = "title", nullable = false, updatable = false, length = 300)
	private String title;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "content", columnDefinition = "jsonb", nullable = false, updatable = false)
	private Map<String, Object> content;

	protected ArticleVersion() {
	}

	ArticleVersion(UUID articleId, int versionNumber, String title, Map<String, Object> content) {
		this.articleId = articleId;
		this.versionNumber = versionNumber;
		this.title = title;
		this.content = content;
	}

	UUID getArticleId() {
		return articleId;
	}

	int getVersionNumber() {
		return versionNumber;
	}

	String getTitle() {
		return title;
	}

	Map<String, Object> getContent() {
		return content;
	}

}
