package com.kpi.studentcouncil.crm.tasks.internal;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableRepository;

interface SpaceRepository extends ArchivableRepository<Space, UUID> {

	/** Row lock: serialises changes of one space with its statuses and fields so their invariants cannot be raced. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from Space s where s.id = :id")
	Optional<Space> lockById(@Param("id") UUID id);

}
