package com.kpi.studentcouncil.crm.knowledge.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface ArticleVersionRepository extends JpaRepository<ArticleVersion, UUID> {

	Page<ArticleVersion> findByArticleId(UUID articleId, Pageable pageable);

	Optional<ArticleVersion> findByArticleIdAndVersionNumber(UUID articleId, int versionNumber);

}
