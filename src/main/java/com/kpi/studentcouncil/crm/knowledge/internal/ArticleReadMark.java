package com.kpi.studentcouncil.crm.knowledge.internal;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.kpi.studentcouncil.crm.shared.domain.BaseEntity;

/** A user's private "I have read this" mark on an article; only its owner can ever see it. */
@Entity
@Table(name = "kb_article_read")
class ArticleReadMark extends BaseEntity {

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "article_id", nullable = false, updatable = false)
	private UUID articleId;

	protected ArticleReadMark() {
	}

	ArticleReadMark(UUID userId, UUID articleId) {
		this.userId = userId;
		this.articleId = articleId;
	}

}
