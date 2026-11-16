package com.kpi.studentcouncil.crm.tasks.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface TaskAssigneeRepository extends JpaRepository<TaskAssignee, UUID> {

	/** Active assignees of the task, oldest assignment first. */
	List<TaskAssignee> findByTaskIdAndRemovedAtIsNullOrderByAssignedAtAscIdAsc(UUID taskId);

	/** Every assignment of the task, removed ones included, oldest first. */
	List<TaskAssignee> findByTaskIdOrderByAssignedAtAscIdAsc(UUID taskId);

	Optional<TaskAssignee> findByTaskIdAndUserIdAndRemovedAtIsNull(UUID taskId, UUID userId);

	boolean existsByTaskIdAndUserIdAndRemovedAtIsNull(UUID taskId, UUID userId);

}
