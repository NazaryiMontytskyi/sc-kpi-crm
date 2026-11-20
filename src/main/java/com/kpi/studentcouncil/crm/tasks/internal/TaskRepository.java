package com.kpi.studentcouncil.crm.tasks.internal;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableRepository;
import com.kpi.studentcouncil.crm.tasks.StatusCategory;

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

	/**
	 * Non-archived tasks with a deadline on or before {@code latest} whose status has none of the {@code closed}
	 * categories, ordered by deadline then id (deadline reminders, {@code ActiveTaskQuery}).
	 */
	@Query("select t from Task t where t.archivedAt is null and t.deadline is not null and t.deadline <= :latest"
			+ " and not exists (select s.id from SpaceStatus s where s.id = t.statusId and s.category in :closed)"
			+ " order by t.deadline, t.id")
	List<Task> findOpenWithDeadlineOnOrBefore(@Param("latest") LocalDate latest,
			@Param("closed") Collection<StatusCategory> closed);

	/**
	 * Non-archived tasks the user is an active assignee of whose status has none of the {@code excluded} categories
	 * ({@code PersonalTaskSummary}, {@code MemberTaskCounts}).
	 */
	@Query("select count(t) from Task t where t.archivedAt is null"
			+ " and exists (select a.id from TaskAssignee a where a.taskId = t.id and a.userId = :userId"
			+ " and a.removedAt is null)"
			+ " and not exists (select s.id from SpaceStatus s where s.id = t.statusId and s.category in :excluded)")
	long countAssignedNotInCategories(@Param("userId") UUID userId,
			@Param("excluded") Collection<StatusCategory> excluded);

	/** As {@link #countAssignedNotInCategories}, limited to deadlines before {@code before} (overdue). */
	@Query("select count(t) from Task t where t.archivedAt is null and t.deadline is not null and t.deadline < :before"
			+ " and exists (select a.id from TaskAssignee a where a.taskId = t.id and a.userId = :userId"
			+ " and a.removedAt is null)"
			+ " and not exists (select s.id from SpaceStatus s where s.id = t.statusId and s.category in :excluded)")
	long countAssignedNotInCategoriesDueBefore(@Param("userId") UUID userId,
			@Param("excluded") Collection<StatusCategory> excluded, @Param("before") LocalDate before);

	/** Non-archived tasks the user is an active assignee of whose status has one of the {@code categories}. */
	@Query("select count(t) from Task t where t.archivedAt is null"
			+ " and exists (select a.id from TaskAssignee a where a.taskId = t.id and a.userId = :userId"
			+ " and a.removedAt is null)"
			+ " and exists (select s.id from SpaceStatus s where s.id = t.statusId and s.category in :categories)")
	long countAssignedInCategories(@Param("userId") UUID userId,
			@Param("categories") Collection<StatusCategory> categories);

	/** Flushes pending changes so the incremented {@code version} is visible before the response is built. */
	void flush();

}
