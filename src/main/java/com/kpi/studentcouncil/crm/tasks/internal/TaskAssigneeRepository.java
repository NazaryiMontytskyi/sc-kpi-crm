package com.kpi.studentcouncil.crm.tasks.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface TaskAssigneeRepository extends JpaRepository<TaskAssignee, UUID> {

	/** Active assignees of the task, oldest assignment first. */
	List<TaskAssignee> findByTaskIdAndRemovedAtIsNullOrderByAssignedAtAscIdAsc(UUID taskId);

	/** Active assignees of the given tasks, oldest assignment first. */
	List<TaskAssignee> findByTaskIdInAndRemovedAtIsNullOrderByAssignedAtAscIdAsc(Collection<UUID> taskIds);

	/** Every assignment of the task, removed ones included, oldest first. */
	List<TaskAssignee> findByTaskIdOrderByAssignedAtAscIdAsc(UUID taskId);

	Optional<TaskAssignee> findByTaskIdAndUserIdAndRemovedAtIsNull(UUID taskId, UUID userId);

	boolean existsByTaskIdAndUserIdAndRemovedAtIsNull(UUID taskId, UUID userId);

	/** Active assignments of the user (any task, archived ones too). */
	List<TaskAssignee> findByUserIdAndRemovedAtIsNull(UUID userId);

}
