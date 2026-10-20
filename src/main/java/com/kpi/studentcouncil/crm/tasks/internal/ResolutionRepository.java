package com.kpi.studentcouncil.crm.tasks.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableRepository;

interface ResolutionRepository extends ArchivableRepository<Resolution, UUID> {

	/** Row lock: serialises changes of one resolution. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Resolution r where r.id = :id")
	Optional<Resolution> lockById(@Param("id") UUID id);

	/** Direct children (redirect targets), archived ones included, locked for update. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Resolution r where r.parentResolutionId = :parentId order by r.createdAt, r.id")
	List<Resolution> lockChildren(@Param("parentId") UUID parentId);

	List<Resolution> findByTaskId(UUID taskId);

}
