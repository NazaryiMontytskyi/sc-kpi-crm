package com.kpi.studentcouncil.crm.org.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableRepository;

interface PositionRepository extends ArchivableRepository<Position, UUID> {

	/** Row lock: serialises appointments to the same position so the capacity check cannot be raced. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Position p where p.id = :id")
	Optional<Position> lockById(@Param("id") UUID id);

	Page<Position> findByOrgUnitId(UUID orgUnitId, Pageable pageable);

	Page<Position> findByOrgUnitIdAndArchivedAtIsNull(UUID orgUnitId, Pageable pageable);

	boolean existsByOrgUnitIdAndHeadTrueAndArchivedAtIsNull(UUID orgUnitId);

	List<Position> findByIdIn(Collection<UUID> ids);

}
