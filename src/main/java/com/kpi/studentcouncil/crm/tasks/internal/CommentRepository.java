package com.kpi.studentcouncil.crm.tasks.internal;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableRepository;
import com.kpi.studentcouncil.crm.tasks.CommentTargetType;

interface CommentRepository extends ArchivableRepository<Comment, UUID> {

	/** Row lock: serialises changes of one comment. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Comment c where c.id = :id")
	Optional<Comment> lockById(@Param("id") UUID id);

	Page<Comment> findByTargetTypeAndTargetIdAndArchivedAtIsNull(CommentTargetType targetType, UUID targetId,
			Pageable pageable);

}
