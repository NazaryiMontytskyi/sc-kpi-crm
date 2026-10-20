package com.kpi.studentcouncil.crm.tasks.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableRepository;

interface TaskRepository extends ArchivableRepository<Task, UUID> {

	/** Row lock: serialises changes of one task. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from Task t where t.id = :id")
	Optional<Task> lockById(@Param("id") UUID id);

	long countBySpaceIdAndStatusIdAndArchivedAtIsNull(UUID spaceId, UUID statusId);

	/** Every task of the space with the status, archived ones included. */
	List<Task> findBySpaceIdAndStatusId(UUID spaceId, UUID statusId);

	List<Task> findByIdIn(Collection<UUID> ids);

}
