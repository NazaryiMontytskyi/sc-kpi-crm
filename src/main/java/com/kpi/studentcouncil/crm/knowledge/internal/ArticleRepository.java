package com.kpi.studentcouncil.crm.knowledge.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableRepository;

interface ArticleRepository extends ArchivableRepository<Article, UUID> {

	/** Row lock: serialises changes of one article. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from Article a where a.id = :id")
	Optional<Article> lockById(@Param("id") UUID id);

	/** Transaction-scoped lock that serialises every change of the hierarchy, so concurrent moves cannot form a cycle. */
	@Query(value = "select cast(pg_advisory_xact_lock(7381201) as text)", nativeQuery = true)
	String lockHierarchy();

	long countByParentArticleIdAndArchivedAtIsNull(UUID parentArticleId);

	List<Article> findByIdIn(java.util.Collection<UUID> ids);

	/** Light projection of the active articles for the page tree. */
	@Query("select a.id as id, a.title as title, a.parentArticleId as parentArticleId, a.restricted as restricted "
			+ "from Article a where a.archivedAt is null and (:includeRestricted = true or a.restricted = false) "
			+ "order by lower(a.title), a.id")
	List<TreeRow> treeRows(@Param("includeRestricted") boolean includeRestricted);

	/** Tags of active, visible articles with the number of articles that carry them. */
	@Query("select t as tag, count(a) as count from Article a join a.tags t where a.archivedAt is null "
			+ "and (:includeRestricted = true or a.restricted = false) group by t order by t")
	List<TagCount> tagCounts(@Param("includeRestricted") boolean includeRestricted);

	interface TreeRow {

		UUID getId();

		String getTitle();

		UUID getParentArticleId();

		boolean getRestricted();

	}

	interface TagCount {

		String getTag();

		long getCount();

	}

}
