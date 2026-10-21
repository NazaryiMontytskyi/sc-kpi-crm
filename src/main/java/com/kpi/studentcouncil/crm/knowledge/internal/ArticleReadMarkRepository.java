package com.kpi.studentcouncil.crm.knowledge.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Every query is scoped by the user id: a mark is only ever read or changed by its owner. */
interface ArticleReadMarkRepository extends Repository<ArticleReadMark, UUID> {

	ArticleReadMark save(ArticleReadMark mark);

	@Query("select count(m) > 0 from ArticleReadMark m where m.userId = :userId and m.articleId = :articleId")
	boolean isRead(@Param("userId") UUID userId, @Param("articleId") UUID articleId);

	@Query("select m.articleId from ArticleReadMark m where m.userId = :userId and m.articleId in :articleIds")
	List<UUID> readAmong(@Param("userId") UUID userId, @Param("articleIds") Collection<UUID> articleIds);

	/** Withdraws the user's own mark; a personal mark is not a business entity. */
	@Modifying
	@Query("delete from ArticleReadMark m where m.userId = :userId and m.articleId = :articleId")
	int withdraw(@Param("userId") UUID userId, @Param("articleId") UUID articleId);

}
