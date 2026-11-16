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

	/** Every subtask (archived ones too) of the given parents. */
	List<Task> findByParentTaskIdIn(Collection<UUID> parentIds);

	/** Every subtask (archived ones too) of the parent, oldest issue first. */
	List<Task> findByParentTaskIdOrderByIssuedAtAscIdAsc(UUID parentId);

	/** Row locks on every subtask (archived ones too) of the parent; taken after the parent's lock. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from Task t where t.parentTaskId = :parentId order by t.issuedAt, t.id")
	List<Task> lockByParentTaskId(@Param("parentId") UUID parentId);

	/** Flushes pending changes so the incremented {@code version} is visible before the response is built. */
	void flush();

}
