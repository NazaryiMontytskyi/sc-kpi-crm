package com.kpi.studentcouncil.crm.knowledge.internal;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** A link or mention found in an article's content. Value object, owned by the article. */
@Embeddable
record ArticleReference(@Enumerated(EnumType.STRING) @Column(name = "ref_type", nullable = false, length = 10) ReferenceType type,
		@Column(name = "ref_id", nullable = false) UUID id) {
}
