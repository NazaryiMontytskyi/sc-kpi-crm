package com.kpi.studentcouncil.crm.tasks.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface TaskArtifactRepository extends JpaRepository<TaskArtifact, UUID> {

	/** Active artifacts of the task, oldest first. */
	List<TaskArtifact> findByTaskIdAndArchivedAtIsNullOrderByAddedAtAscIdAsc(UUID taskId);

	/** Active artifacts of the given tasks, oldest first. */
	List<TaskArtifact> findByTaskIdInAndArchivedAtIsNullOrderByAddedAtAscIdAsc(Collection<UUID> taskIds);

	/** Every artifact of the task, removed ones included, oldest first. */
	List<TaskArtifact> findByTaskIdOrderByAddedAtAscIdAsc(UUID taskId);

	Optional<TaskArtifact> findByIdAndTaskId(UUID id, UUID taskId);

	/** Active FILE artifacts of the task pointing at the file. */
	List<TaskArtifact> findByTaskIdAndTypeAndFileIdAndArchivedAtIsNull(UUID taskId, TaskArtifactType type,
			UUID fileId);

}
