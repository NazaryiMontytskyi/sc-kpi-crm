package com.kpi.studentcouncil.crm.tasks.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.audit.AuditAction;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.files.FileInfo;
import com.kpi.studentcouncil.crm.files.FileService;
import com.kpi.studentcouncil.crm.identity.UserDirectory;
import com.kpi.studentcouncil.crm.shared.domain.ArchivableSpecs;
import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;
import com.kpi.studentcouncil.crm.shared.error.ConflictException;
import com.kpi.studentcouncil.crm.shared.error.NotFoundException;
import com.kpi.studentcouncil.crm.tasks.StatusCategory;
import com.kpi.studentcouncil.crm.tasks.TaskEvent;

/**
 * Commands and queries of tasks. Lock order is always space first, then task, so that status changes cannot deadlock
 * with archiving a status (which holds the space lock and moves tasks).
 */
@Service
class TaskService {

	static final String TASK = "Task";

	/**
	 * A task together with the data that lives outside its row: related ids, the legacy resolution hint, its active
	 * assignees and the progress of its subtasks ({@link SuggestCloseRule}). {@code attachments}, {@code subtasks} and
	 * {@code artifacts} are null in list results; in a detail they hold the attachments, the subtasks as list views and
	 * the active artifacts (spec §7: one request for task, assignees, subtasks and artifacts).
	 */
	record TaskView(Task task, Set<UUID> relatedTaskIds, List<FileInfo> attachments, boolean suggestDone,
			List<TaskAssignee> assignees, SuggestCloseRule.Progress subtaskProgress, List<TaskView> subtasks,
			List<TaskArtifactService.ArtifactView> artifacts) {
	}

	/**
	 * Filters of the task list. {@code assignee} matches an active assignee, {@code creator} the creator,
	 * {@code overdue} (when true) open, non-archived tasks past their deadline, {@code view} a quick view evaluated for
	 * the caller ({@link TaskSpecs}), {@code parentId} the subtasks of that task. The legacy resolution filters ({@code executorId} incl. co-executors,
	 * {@code authorId}, {@code resolutionState}) are combined on one and the same non-archived resolution of the task.
	 */
	record TaskFilter(UUID spaceId, UUID statusId, Collection<String> labels, LocalDate deadlineFrom,
			LocalDate deadlineTo, boolean includeArchived, UUID assignee, UUID creator, boolean overdue,
			TaskQuickView view, UUID executorId, UUID authorId, ResolutionState resolutionState, UUID parentId) {

		TaskFilter(UUID spaceId, UUID statusId, Collection<String> labels, LocalDate deadlineFrom,
				LocalDate deadlineTo, boolean includeArchived) {
			this(spaceId, statusId, labels, deadlineFrom, deadlineTo, includeArchived, null, null, false, null, null,
					null, null, null);
		}
	}

	static final String TASK_ASSIGNEE = "TaskAssignee";

	private final TaskRepository tasks;
	private final TaskAssigneeRepository assignees;
	private final TaskLinkRepository links;
	private final SpaceRepository spaces;
	private final SpaceStatusRepository statuses;
	private final ResolutionRepository resolutions;
	private final CustomFieldDefinitionRepository fields;
	private final FileService files;
	private final UserDirectory users;
	private final TaskEditPolicy editPolicy;
	private final TaskArtifactService artifacts;
	private final AuditPublisher audit;
	private final ActorProvider actors;
	private final ApplicationEventPublisher events;
	private final Clock clock;

	TaskService(TaskRepository tasks, TaskAssigneeRepository assignees, TaskLinkRepository links,
			SpaceRepository spaces, SpaceStatusRepository statuses, ResolutionRepository resolutions,
			CustomFieldDefinitionRepository fields, FileService files, UserDirectory users, TaskEditPolicy editPolicy,
			TaskArtifactService artifacts, AuditPublisher audit, ActorProvider actors, ApplicationEventPublisher events,
			Clock clock) {
		this.tasks = tasks;
		this.assignees = assignees;
		this.links = links;
		this.spaces = spaces;
		this.statuses = statuses;
		this.resolutions = resolutions;
		this.fields = fields;
		this.files = files;
		this.users = users;
		this.editPolicy = editPolicy;
		this.artifacts = artifacts;
		this.audit = audit;
		this.actors = actors;
		this.events = events;
		this.clock = clock;
	}

	// ---- commands ----

	@Transactional
	TaskView create(UUID spaceId, String title, Map<String, Object> description, UUID statusId, TaskPriority priority,
			LocalDate deadline, Collection<String> labels, Map<String, Object> customFieldValues,
			Collection<UUID> attachmentIds, Collection<UUID> relatedTaskIds) {
		lockActiveSpace(spaceId);
		Task saved = insert(spaceId, null, title, description, statusId, priority, deadline, labels,
				customFieldValues, attachmentIds, relatedTaskIds);
		return view(saved);
	}

	/**
	 * Creates a subtask of {@code parentId} (ST-1, ST-2, ST-3) in the parent's space. The parent must be a non-archived
	 * top-level task (409 {@code TASK_ARCHIVED}, 422 {@code SUBTASK_DEPTH_EXCEEDED}); the deadline must not be later
	 * than the parent's (422 {@code SUBTASK_DEADLINE_AFTER_PARENT}). {@code assigneeIds} null means "for myself"; an
	 * empty list creates it unassigned. Assigning another user needs {@code tasks.assign} ({@link AssigneeRules}, 403
	 * {@code TASK_ASSIGN_FORBIDDEN}); assignees must be active (422 {@code TASK_ASSIGNEE_INACTIVE}). Audits the
	 * creation and each assignment and publishes {@code CREATED}, {@code ASSIGNED} (per assignee) and, on the parent,
	 * {@code SUBTASK_ADDED}.
	 */
	@Transactional
	TaskView createSubtask(UUID parentId, String title, Map<String, Object> description, UUID statusId,
			TaskPriority priority, LocalDate deadline, Collection<String> labels, Map<String, Object> customFieldValues,
			Collection<UUID> assigneeIds) {
		UUID spaceId = require(parentId).getSpaceId();
		lockActiveSpace(spaceId);
		Task parent = lockActive(parentId);
		SubtaskRules.assertCanHaveSubtasks(parent.getParentTaskId());
		UUID actor = actors.currentActorId();
		List<UUID> targets = assigneeIds == null ? List.of(actor)
				: List.copyOf(new LinkedHashSet<>(assigneeIds.stream().filter(Objects::nonNull).toList()));
		// the caller becomes the creator of the subtask: AssigneeRules decide on that basis (ST-2)
		Task draft = new Task(spaceId, parentId, actor, clock.instant(), title.strip(), null, parent.getStatusId(),
				TaskPriority.NORMAL, null, Set.of(), List.of(), Map.of());
		targets.forEach(target -> editPolicy.assertCanManageAssignee(draft, target));
		if (targets.stream().anyMatch(target -> !users.isActive(target))) {
			throw new BusinessRuleException("TASK_ASSIGNEE_INACTIVE");
		}
		SubtaskRules.assertWithinParent(deadline, parent.getDeadline());

		Task saved = insert(spaceId, parentId, title, description, statusId, priority, deadline, labels,
				customFieldValues, null, null);
		for (UUID target : targets) {
			List<String> before = activeAssigneeIds(saved.getId());
			TaskAssignee row = assignees.saveAndFlush(new TaskAssignee(saved.getId(), target, actor, clock.instant()));
			audit.publish(AuditAction.UPDATE, TASK, saved.getId(), assigneeSnapshot(before, null),
					assigneeSnapshot(activeAssigneeIds(saved.getId()), row.auditSnapshot()));
			events.publishEvent(new TaskEvent(TaskEvent.Kind.ASSIGNED, saved.getId(), spaceId, saved.getStatusId(),
					saved.getCreatorId(), actor, target, null, null, saved.getDeadline()));
		}
		events.publishEvent(new TaskEvent(TaskEvent.Kind.SUBTASK_ADDED, parentId, spaceId, parent.getStatusId(),
				parent.getCreatorId(), actor, null, null, null, parent.getDeadline(), saved.getId()));
		return view(saved);
	}

	/** Validates and saves a new task (the space is already locked), audits it and publishes {@code CREATED}. */
	private Task insert(UUID spaceId, UUID parentId, String title, Map<String, Object> description, UUID statusId,
			TaskPriority priority, LocalDate deadline, Collection<String> labels, Map<String, Object> customFieldValues,
			Collection<UUID> attachmentIds, Collection<UUID> relatedTaskIds) {
		UUID status = resolveInitialStatus(spaceId, statusId);
		Map<String, Object> values = TaskRules.validateCustomFields(fieldRules(spaceId), customFieldValues,
				users::exists);
		List<UUID> attachments = TaskRules.normalizeAttachments(attachmentIds);
		attachments.forEach(this::assertFileUsable);
		Set<UUID> related = TaskRules.normalizeRelated(relatedTaskIds, null);
		assertLinkable(related);
		Set<String> normalizedLabels = TaskRules.normalizeLabels(labels);

		Task saved = tasks.save(new Task(spaceId, parentId, actors.currentActorId(), clock.instant(), title.strip(),
				description, status, priority == null ? TaskPriority.NORMAL : priority, deadline, normalizedLabels,
				attachments, values));
		Map<UUID, Set<UUID>> counterpartsBefore = relatedOfAll(related);
		related.forEach(other -> links.save(new TaskLink(TaskLinkId.of(saved.getId(), other))));
		audit.publish(AuditAction.CREATE, TASK, saved.getId(), null, saved.auditSnapshot(related));
		auditCounterparts(counterpartsBefore);
		artifacts.syncLegacyAttachments(saved.getId(), List.of(), attachments);
		publish(TaskEvent.Kind.CREATED, saved);
		return saved;
	}

	private Space lockActiveSpace(UUID spaceId) {
		Space space = spaces.lockById(spaceId).orElseThrow(() -> new NotFoundException(SpaceService.SPACE, spaceId));
		if (space.isArchived()) {
			throw new ConflictException("SPACE_ARCHIVED");
		}
		return space;
	}

	/**
	 * Replaces the editable fields. Author or Admin only ({@link TaskEditPolicy}). {@code expectedVersion} (optional) is
	 * the version the caller edited; a different current version is a conflict (409 {@code TASK_VERSION_CONFLICT}).
	 * An edit that changes nothing is not saved, audited or published; a moved deadline additionally publishes
	 * {@code DEADLINE_CHANGED}. Only a changed deadline is checked against the subtask rules (ST-3,
	 * {@link SubtaskRules}): a subtask's against its parent (422 {@code SUBTASK_DEADLINE_AFTER_PARENT}), a parent's
	 * against its non-archived subtasks (422 {@code TASK_DEADLINE_BEFORE_SUBTASKS} naming them); a rejected edit
	 * changes nothing. Edits that keep the deadline never fail on it (subtasks migrated from resolutions).
	 */
	@Transactional
	TaskView update(UUID id, Long expectedVersion, String title, Map<String, Object> description,
			TaskPriority priority, LocalDate deadline, Collection<String> labels, Map<String, Object> customFieldValues,
			Collection<UUID> attachmentIds, Collection<UUID> relatedTaskIds) {
		// a subtask's deadline is checked against its parent: lock the parent first (parent -> subtask lock order)
		UUID parentId = require(id).getParentTaskId();
		Task parent = parentId == null ? null : lock(parentId);
		Task task = lockActive(id);
		editPolicy.assertCanEdit(task);
		task.assertVersion(expectedVersion);

		Map<String, Object> values = new LinkedHashMap<>(TaskRules.validateCustomFields(fieldRules(task.getSpaceId()),
				customFieldValues, users::exists));
		// values of fields that were archived since stay on the task
		Set<String> activeIds = new LinkedHashSet<>();
		fieldRules(task.getSpaceId()).forEach(f -> activeIds.add(f.id().toString()));
		task.getCustomFieldValues().forEach((key, value) -> {
			if (!activeIds.contains(key)) {
				values.put(key, value);
			}
		});
		List<UUID> attachments = TaskRules.normalizeAttachments(attachmentIds);
		Set<UUID> kept = new LinkedHashSet<>(task.getAttachmentIds());
		attachments.stream().filter(a -> !kept.contains(a)).forEach(this::assertFileUsable);
		Set<UUID> related = TaskRules.normalizeRelated(relatedTaskIds, id);
		Set<UUID> currentRelated = relatedOf(id);
		assertLinkable(without(related, currentRelated));
		Set<String> normalizedLabels = TaskRules.normalizeLabels(labels);
		String newTitle = title.strip();
		TaskPriority newPriority = priority == null ? TaskPriority.NORMAL : priority;
		if (unchanged(task, newTitle, description, newPriority, deadline, normalizedLabels, attachments, values)
				&& related.equals(currentRelated)) {
			return view(task); // a no-op edit: nothing saved, audited or published (spec 6.4)
		}
		if (!Objects.equals(task.getDeadline(), deadline)) {
			assertDeadlineMove(task, parent, deadline);
		}

		Map<String, Object> before = task.auditSnapshot(currentRelated);
		LocalDate previousDeadline = task.getDeadline();
		List<UUID> previousAttachments = task.getAttachmentIds();
		task.update(newTitle, description, newPriority, deadline, normalizedLabels, attachments, values);
		Task saved = tasks.save(task);
		tasks.flush(); // the new version is part of the audit snapshot and of the response

		Set<UUID> touched = new LinkedHashSet<>(related);
		touched.addAll(currentRelated);
		Map<UUID, Set<UUID>> counterpartsBefore = relatedOfAll(touched);
		without(related, currentRelated).forEach(o -> links.save(new TaskLink(TaskLinkId.of(id, o))));
		without(currentRelated, related).forEach(o -> links.delete(new TaskLink(TaskLinkId.of(id, o))));
		audit.publish(AuditAction.UPDATE, TASK, id, before, saved.auditSnapshot(related));
		auditCounterparts(counterpartsBefore);
		artifacts.syncLegacyAttachments(id, previousAttachments, attachments);
		publish(TaskEvent.Kind.UPDATED, saved);
		if (!Objects.equals(previousDeadline, saved.getDeadline())) {
			events.publishEvent(new TaskEvent(TaskEvent.Kind.DEADLINE_CHANGED, id, saved.getSpaceId(),
					saved.getStatusId(), saved.getCreatorId(), actors.currentActorId(), null, null, previousDeadline,
					saved.getDeadline()));
		}
		return view(saved);
	}

	/** ST-3 for a changed deadline; throws before anything is modified. */
	private void assertDeadlineMove(Task task, Task parent, LocalDate deadline) {
		if (parent != null) {
			SubtaskRules.assertWithinParent(deadline, parent.getDeadline());
			return;
		}
		List<SubtaskRules.Subtask> subtasks = tasks.findByParentTaskIdOrderByIssuedAtAscIdAsc(task.getId()).stream()
				.filter(t -> !t.isArchived())
				.map(t -> new SubtaskRules.Subtask(t.getId(), t.getTitle(), t.getDeadline())).toList();
		List<SubtaskRules.Subtask> conflicts = SubtaskRules.conflicting(task.getDeadline(), deadline, subtasks);
		if (!conflicts.isEmpty()) {
			throw new SubtaskDeadlineConflictException(conflicts);
		}
	}

	/**
	 * Moves the task to another status of its space: the creator, an active assignee, {@code tasks.edit.any} or an
	 * Admin (AS-5, {@link TaskEditPolicy}); same optional version check as editing. Moving to the current status is a
	 * no-op (nothing audited or published).
	 */
	@Transactional
	TaskView changeStatus(UUID id, Long expectedVersion, UUID statusId) {
		UUID spaceId = require(id).getSpaceId();
		spaces.lockById(spaceId).orElseThrow(() -> new NotFoundException(SpaceService.SPACE, spaceId));
		Task task = lockActive(id);
		editPolicy.assertCanChangeStatus(task);
		task.assertVersion(expectedVersion);
		SpaceStatus target = statuses.findBySpaceIdAndArchivedAtIsNullOrderByPositionAscIdAsc(task.getSpaceId())
				.stream().filter(s -> s.getId().equals(statusId)).findFirst()
				.orElseThrow(() -> new BusinessRuleException("TASK_STATUS_NOT_IN_SPACE"));
		Set<UUID> related = relatedOf(id);
		if (!target.getId().equals(task.getStatusId())) {
			Map<String, Object> before = task.auditSnapshot(related);
			UUID previousStatus = task.getStatusId();
			task.changeStatus(target.getId());
			tasks.save(task);
			tasks.flush();
			audit.publish(AuditAction.UPDATE, TASK, id, before, task.auditSnapshot(related));
			events.publishEvent(new TaskEvent(TaskEvent.Kind.STATUS_CHANGED, id, task.getSpaceId(), task.getStatusId(),
					task.getCreatorId(), actors.currentActorId(), null, previousStatus, null, task.getDeadline()));
		}
		return view(task);
	}

	/**
	 * Archives the task and, in the same transaction, every non-archived subtask (TM-4), all with the same archive
	 * instant and actor; each archived task is audited and publishes {@code ARCHIVED}. Nothing is deleted.
	 */
	@Transactional
	TaskView archive(UUID id) {
		Task task = lock(id);
		// one instant (database precision) for the whole cascade: restore recognises the cascaded subtasks by it
		Instant at = clock.instant().truncatedTo(ChronoUnit.MICROS);
		UUID actor = actors.currentActorId();
		archiveOne(task, actor, at);
		for (Task subtask : tasks.lockByParentTaskId(id)) {
			if (!subtask.isArchived()) {
				archiveOne(subtask, actor, at);
			}
		}
		return view(task);
	}

	/**
	 * Restores the task and only the subtasks archived together with it by the cascade
	 * ({@link SubtaskRules#archivedWithParent}); subtasks archived on their own before stay archived. A subtask cannot
	 * be restored while its parent is archived (409 {@code TASK_PARENT_ARCHIVED}). Each restored task is audited and
	 * publishes {@code RESTORED}.
	 */
	@Transactional
	TaskView restore(UUID id) {
		Task current = require(id);
		UUID spaceId = current.getSpaceId();
		Space space = spaces.lockById(spaceId).orElseThrow(() -> new NotFoundException(SpaceService.SPACE, spaceId));
		Task parent = current.getParentTaskId() == null ? null : lock(current.getParentTaskId());
		Task task = lock(id);
		if (!task.isArchived()) {
			throw new ConflictException("NOT_ARCHIVED");
		}
		if (space.isArchived()) {
			throw new ConflictException("SPACE_ARCHIVED");
		}
		if (parent != null && parent.isArchived()) {
			throw new ConflictException("TASK_PARENT_ARCHIVED");
		}
		Instant archivedAt = task.getArchivedAt();
		UUID archivedBy = task.getArchivedBy();
		restoreOne(task);
		for (Task subtask : tasks.lockByParentTaskId(id)) {
			if (subtask.isArchived() && SubtaskRules.archivedWithParent(subtask.getArchivedAt(),
					subtask.getArchivedBy(), archivedAt, archivedBy)) {
				restoreOne(subtask);
			}
		}
		return view(task);
	}

	private void archiveOne(Task task, UUID actor, Instant at) {
		Set<UUID> related = relatedOf(task.getId());
		Map<String, Object> before = task.auditSnapshot(related);
		task.archive(actor, at);
		Task saved = tasks.save(task);
		tasks.flush();
		audit.publish(AuditAction.ARCHIVE, TASK, task.getId(), before, saved.auditSnapshot(related));
		publish(TaskEvent.Kind.ARCHIVED, saved);
	}

	private void restoreOne(Task task) {
		Set<UUID> related = relatedOf(task.getId());
		Map<String, Object> before = task.auditSnapshot(related);
		task.restore();
		Task saved = tasks.save(task);
		tasks.flush();
		audit.publish(AuditAction.RESTORE, TASK, task.getId(), before, saved.auditSnapshot(related));
		publish(TaskEvent.Kind.RESTORED, saved);
	}

	// ---- assignees (AS-1..AS-5) ----

	/**
	 * Adds an active assignee. Rights: {@link AssigneeRules} (403 {@code TASK_ASSIGN_FORBIDDEN}); the user must be an
	 * active account (422 {@code TASK_ASSIGNEE_INACTIVE}); a user is an active assignee at most once (409
	 * {@code TASK_ASSIGNEE_DUPLICATE}); archived tasks are not changed (409 {@code TASK_ARCHIVED}).
	 */
	@Transactional
	TaskAssignee assign(UUID taskId, UUID userId) {
		Task task = lockActive(taskId);
		editPolicy.assertCanManageAssignee(task, userId);
		if (!users.isActive(userId)) {
			throw new BusinessRuleException("TASK_ASSIGNEE_INACTIVE");
		}
		if (assignees.existsByTaskIdAndUserIdAndRemovedAtIsNull(taskId, userId)) {
			throw new ConflictException("TASK_ASSIGNEE_DUPLICATE");
		}
		List<String> before = activeAssigneeIds(taskId);
		UUID actor = actors.currentActorId();
		TaskAssignee saved = assignees.saveAndFlush(new TaskAssignee(taskId, userId, actor, clock.instant()));
		audit.publish(AuditAction.UPDATE, TASK, taskId, assigneeSnapshot(before, null),
				assigneeSnapshot(activeAssigneeIds(taskId), saved.auditSnapshot()));
		events.publishEvent(new TaskEvent(TaskEvent.Kind.ASSIGNED, taskId, task.getSpaceId(), task.getStatusId(),
				task.getCreatorId(), actor, userId, null, null, task.getDeadline()));
		return saved;
	}

	/**
	 * Ends an active assignment: the row stays with {@code removedAt} (AS-4). Same rights as adding; 404 when the user
	 * is not an active assignee of the task.
	 */
	@Transactional
	TaskAssignee unassign(UUID taskId, UUID userId) {
		Task task = lockActive(taskId);
		editPolicy.assertCanManageAssignee(task, userId);
		TaskAssignee row = assignees.findByTaskIdAndUserIdAndRemovedAtIsNull(taskId, userId)
				.orElseThrow(() -> new NotFoundException(TASK_ASSIGNEE, userId));
		List<String> before = activeAssigneeIds(taskId);
		Map<String, Object> rowBefore = row.auditSnapshot();
		UUID actor = actors.currentActorId();
		row.remove(actor, clock.instant());
		TaskAssignee saved = assignees.saveAndFlush(row);
		audit.publish(AuditAction.UPDATE, TASK, taskId, assigneeSnapshot(before, rowBefore),
				assigneeSnapshot(activeAssigneeIds(taskId), saved.auditSnapshot()));
		events.publishEvent(new TaskEvent(TaskEvent.Kind.UNASSIGNED, taskId, task.getSpaceId(), task.getStatusId(),
				task.getCreatorId(), actor, userId, null, null, task.getDeadline()));
		return saved;
	}

	/** Assignees of a task (archived tasks too), oldest first; removed ones only with {@code includeRemoved}. */
	@Transactional(readOnly = true)
	List<TaskAssignee> assignees(UUID taskId, boolean includeRemoved) {
		require(taskId);
		return includeRemoved ? assignees.findByTaskIdOrderByAssignedAtAscIdAsc(taskId)
				: assignees.findByTaskIdAndRemovedAtIsNullOrderByAssignedAtAscIdAsc(taskId);
	}

	// ---- queries ----

	/** A task with its attachments, active assignees and subtasks (spec §7: one request opens the panel). */
	@Transactional(readOnly = true)
	TaskView get(UUID id) {
		return view(require(id));
	}

	/**
	 * Subtasks of a task (of an archived one too) as list views, oldest issue first; archived subtasks only with
	 * {@code includeArchived}.
	 */
	@Transactional(readOnly = true)
	List<TaskView> subtasks(UUID parentId, boolean includeArchived) {
		require(parentId);
		return views(tasks.findByParentTaskIdOrderByIssuedAtAscIdAsc(parentId).stream()
				.filter(t -> includeArchived || !t.isArchived()).toList(), false);
	}

	@Transactional(readOnly = true)
	Page<TaskView> list(TaskFilter filter, Pageable pageable) {
		Specification<Task> spec = (root, query, cb) -> cb.conjunction();
		if (!filter.includeArchived()) {
			spec = spec.and(ArchivableSpecs.notArchived());
		}
		if (filter.spaceId() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("spaceId"), filter.spaceId()));
		}
		if (filter.statusId() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("statusId"), filter.statusId()));
		}
		if (filter.deadlineFrom() != null) {
			spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.<LocalDate>get("deadline"),
					filter.deadlineFrom()));
		}
		if (filter.deadlineTo() != null) {
			spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.<LocalDate>get("deadline"),
					filter.deadlineTo()));
		}
		if (filter.assignee() != null) {
			spec = spec.and(TaskSpecs.assignee(filter.assignee()));
		}
		if (filter.creator() != null) {
			spec = spec.and(TaskSpecs.creator(filter.creator()));
		}
		if (filter.parentId() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("parentTaskId"), filter.parentId()));
		}
		if (filter.overdue()) {
			spec = spec.and(TaskSpecs.overdue(clock));
		}
		if (filter.view() != null) {
			spec = spec.and(TaskSpecs.view(filter.view(), actors.currentActorId(), clock));
		}
		Set<String> labels = filter.labels() == null || filter.labels().isEmpty() ? Set.of()
				: TaskRules.normalizeLabels(filter.labels());
		if (!labels.isEmpty()) {
			// any of the labels
			spec = spec.and((root, query, cb) -> cb.or(labels.stream()
					.map(label -> cb.isMember(label, root.<Set<String>>get("labels"))).toArray(jakarta.persistence.criteria.Predicate[]::new)));
		}
		spec = spec.and(TaskResolutionSpecs.of(filter));
		Page<Task> page = tasks.findAll(spec, pageable);
		Map<UUID, TaskView> built = new HashMap<>();
		views(page.getContent(), false).forEach(v -> built.put(v.task().getId(), v));
		return page.map(t -> built.get(t.getId()));
	}

	/** Whether the "all resolutions accepted" suggestion applies to the task ({@link SuggestDoneRule}); never changes it. */
	@Transactional(readOnly = true)
	boolean suggestDone(UUID taskId) {
		return suggestDoneOfAll(List.of(require(taskId))).getOrDefault(taskId, false);
	}

	// ---- status usage (SpaceStatusUsageGuard) ----

	@Transactional(readOnly = true)
	long countUsages(UUID spaceId, UUID statusId) {
		return tasks.countBySpaceIdAndStatusIdAndArchivedAtIsNull(spaceId, statusId);
	}

	/** Moves every task (archived ones too) of the space from one status to another, auditing each. */
	@Transactional
	void reassignStatus(UUID spaceId, UUID fromStatusId, UUID toStatusId) {
		for (Task task : tasks.findBySpaceIdAndStatusId(spaceId, fromStatusId)) {
			Set<UUID> related = relatedOf(task.getId());
			Map<String, Object> before = task.auditSnapshot(related);
			task.changeStatus(toStatusId);
			Task saved = tasks.save(task);
			tasks.flush();
			audit.publish(AuditAction.UPDATE, TASK, task.getId(), before, saved.auditSnapshot(related));
			publish(TaskEvent.Kind.UPDATED, saved);
		}
	}

	// ---- helpers ----

	/** The detail view of one task. */
	private TaskView view(Task task) {
		return views(List.of(task), true).get(0);
	}

	/**
	 * Views of the given tasks, in order, with a fixed number of batched queries (links, resolution states, active
	 * assignees, subtasks, status categories). A detail ({@code detail}) adds attachments, the active artifacts (one
	 * more query) and the subtasks: the non-archived ones, or every subtask when the task itself is archived (so an
	 * archived task still shows what was archived with it).
	 */
	private List<TaskView> views(List<Task> list, boolean detail) {
		if (list.isEmpty()) {
			return List.of();
		}
		List<UUID> ids = list.stream().map(Task::getId).toList();
		Map<UUID, Set<UUID>> related = relatedOfAll(ids);
		Map<UUID, Boolean> suggestions = suggestDoneOfAll(list);
		Map<UUID, List<TaskAssignee>> active = new HashMap<>();
		assignees.findByTaskIdInAndRemovedAtIsNullOrderByAssignedAtAscIdAsc(ids)
				.forEach(a -> active.computeIfAbsent(a.getTaskId(), k -> new ArrayList<>()).add(a));

		// only top-level tasks have subtasks (one level, ST-1)
		List<UUID> parents = list.stream().filter(t -> t.getParentTaskId() == null).map(Task::getId).toList();
		Map<UUID, List<Task>> childrenOf = new HashMap<>();
		if (!parents.isEmpty()) {
			tasks.findByParentTaskIdIn(parents).stream()
					.sorted(Comparator.comparing(Task::getIssuedAt, Comparator.nullsLast(Comparator.naturalOrder()))
							.thenComparing(Task::getId))
					.forEach(c -> childrenOf.computeIfAbsent(c.getParentTaskId(), k -> new ArrayList<>()).add(c));
		}
		Set<UUID> statusIds = new HashSet<>();
		list.forEach(t -> statusIds.add(t.getStatusId()));
		childrenOf.values().forEach(children -> children.forEach(c -> statusIds.add(c.getStatusId())));
		Map<UUID, StatusCategory> categories = new HashMap<>();
		statuses.findByIdIn(statusIds).forEach(s -> categories.put(s.getId(), s.getCategory()));

		Map<UUID, TaskView> subtaskViews = new HashMap<>();
		Map<UUID, List<TaskArtifactService.ArtifactView>> artifactsOf = detail ? artifacts.activeOfAll(ids) : Map.of();
		if (detail) {
			List<Task> shown = new ArrayList<>();
			for (Task t : list) {
				childrenOf.getOrDefault(t.getId(), List.of()).stream().filter(c -> t.isArchived() || !c.isArchived())
						.forEach(shown::add);
			}
			views(shown, false).forEach(v -> subtaskViews.put(v.task().getId(), v));
		}

		List<TaskView> result = new ArrayList<>();
		for (Task t : list) {
			List<Task> children = childrenOf.getOrDefault(t.getId(), List.of());
			SuggestCloseRule.Progress progress = children.isEmpty() ? SuggestCloseRule.Progress.NONE
					: SuggestCloseRule.progress(categories.get(t.getStatusId()), children.stream()
							.map(c -> new SuggestCloseRule.Subtask(categories.get(c.getStatusId()), c.isArchived()))
							.toList());
			List<TaskView> subtasks = !detail ? null
					: children.stream().map(c -> subtaskViews.get(c.getId())).filter(Objects::nonNull).toList();
			result.add(new TaskView(t, related.getOrDefault(t.getId(), Set.of()), detail ? attachmentInfos(t) : null,
					suggestions.getOrDefault(t.getId(), false), List.copyOf(active.getOrDefault(t.getId(), List.of())),
					progress, subtasks, detail ? List.copyOf(artifactsOf.getOrDefault(t.getId(), List.of())) : null));
		}
		return result;
	}

	/** Two batched queries (resolution states, status categories) for any number of tasks. */
	private Map<UUID, Boolean> suggestDoneOfAll(Collection<Task> list) {
		Map<UUID, Boolean> result = new HashMap<>();
		if (list.isEmpty()) {
			return result;
		}
		Map<UUID, List<SuggestDoneRule.Node>> states = new HashMap<>();
		for (ResolutionRepository.TaskResolutionState row : resolutions
				.findActiveStatesByTaskIdIn(list.stream().map(Task::getId).toList())) {
			states.computeIfAbsent(row.taskId(), k -> new ArrayList<>()).add(new SuggestDoneRule.Node(row.id(), row.parentResolutionId(), row.state()));
		}
		Set<UUID> statusIds = new HashSet<>();
		list.forEach(t -> statusIds.add(t.getStatusId()));
		Set<UUID> doneStatuses = new HashSet<>();
		statuses.findByIdIn(statusIds).forEach(s -> {
			if (s.getCategory() == StatusCategory.DONE) {
				doneStatuses.add(s.getId());
			}
		});
		for (Task t : list) {
			result.put(t.getId(), SuggestDoneRule.suggest(states.getOrDefault(t.getId(), List.of()),
					doneStatuses.contains(t.getStatusId())));
		}
		return result;
	}

	private Task require(UUID id) {
		return tasks.findById(id).orElseThrow(() -> new NotFoundException(TASK, id));
	}

	private Task lock(UUID id) {
		return tasks.lockById(id).orElseThrow(() -> new NotFoundException(TASK, id));
	}

	private Task lockActive(UUID id) {
		Task task = lock(id);
		if (task.isArchived()) {
			throw new ConflictException("TASK_ARCHIVED");
		}
		return task;
	}

	private UUID resolveInitialStatus(UUID spaceId, UUID statusId) {
		List<SpaceStatus> active = statuses.findBySpaceIdAndArchivedAtIsNullOrderByPositionAscIdAsc(spaceId);
		if (statusId == null) {
			return active.stream().filter(s -> s.getCategory() == StatusCategory.TODO).findFirst()
					.orElseThrow(() -> new BusinessRuleException("SPACE_STATUS_CATEGORY_REQUIRED", "TODO")).getId();
		}
		if (active.stream().noneMatch(s -> s.getId().equals(statusId))) {
			throw new BusinessRuleException("TASK_STATUS_NOT_IN_SPACE");
		}
		return statusId;
	}

	private List<TaskRules.FieldRule> fieldRules(UUID spaceId) {
		return fields.findBySpaceIdAndArchivedAtIsNullOrderByCreatedAtAscIdAsc(spaceId).stream()
				.map(f -> new TaskRules.FieldRule(f.getId(), f.getName(), f.getType(), f.isRequired(), f.getOptions()))
				.toList();
	}

	private void assertFileUsable(UUID fileId) {
		try {
			files.require(fileId);
		}
		catch (NotFoundException e) {
			throw new BusinessRuleException("TASK_ATTACHMENT_INVALID");
		}
	}

	/** Newly linked tasks must exist and not be archived. */
	private void assertLinkable(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return;
		}
		long active = tasks.findByIdIn(ids).stream().filter(t -> !t.isArchived()).count();
		if (active != ids.size()) {
			throw new BusinessRuleException("TASK_RELATED_INVALID");
		}
	}

	/** True when the new values equal the task's current ones (a no-op edit). */
	private static boolean unchanged(Task task, String title, Map<String, Object> description, TaskPriority priority,
			LocalDate deadline, Set<String> labels, List<UUID> attachments, Map<String, Object> values) {
		return task.getTitle().equals(title) && Objects.equals(task.getDescription(), description)
				&& task.getPriority() == priority && Objects.equals(task.getDeadline(), deadline)
				&& task.getLabels().equals(labels) && task.getAttachmentIds().equals(attachments)
				&& task.getCustomFieldValues().equals(values);
	}

	private List<String> activeAssigneeIds(UUID taskId) {
		return ids(assignees.findByTaskIdAndRemovedAtIsNullOrderByAssignedAtAscIdAsc(taskId).stream()
				.map(TaskAssignee::getUserId).toList());
	}

	/** Audit state of an assignee change: the active assignee ids and the changed row (null before an addition). */
	private static Map<String, Object> assigneeSnapshot(List<String> activeIds, Map<String, Object> row) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("assigneeIds", activeIds);
		map.put("assignee", row);
		return map;
	}

	private static Set<UUID> without(Set<UUID> source, Set<UUID> removed) {
		Set<UUID> result = new LinkedHashSet<>(source);
		result.removeAll(removed);
		return result;
	}

	private List<FileInfo> attachmentInfos(Task task) {
		List<FileInfo> infos = new ArrayList<>();
		for (UUID fileId : task.getAttachmentIds()) {
			Optional<FileInfo> info = files.find(fileId);
			info.ifPresent(infos::add);
		}
		return infos;
	}

	private Set<UUID> relatedOf(UUID id) {
		return relatedOfAll(List.of(id)).getOrDefault(id, Set.of());
	}

	/** Related ids of each given task (symmetric: a link counts for both ends); every given id is a key. */
	private Map<UUID, Set<UUID>> relatedOfAll(Collection<UUID> ids) {
		Map<UUID, Set<UUID>> result = new HashMap<>();
		if (ids.isEmpty()) {
			return result;
		}
		ids.forEach(id -> result.put(id, new TreeSet<>()));
		for (TaskLink link : links.findAllTouching(ids)) {
			UUID low = link.getId().low();
			UUID high = link.getId().high();
			if (result.containsKey(low)) {
				result.get(low).add(high);
			}
			if (result.containsKey(high)) {
				result.get(high).add(low);
			}
		}
		return result;
	}

	/**
	 * A link change also changes the related list of the other task; record it in that task's history. The map holds
	 * the related ids of each such task before the change.
	 */
	private void auditCounterparts(Map<UUID, Set<UUID>> before) {
		Map<UUID, Set<UUID>> after = relatedOfAll(before.keySet());
		for (Map.Entry<UUID, Set<UUID>> e : before.entrySet()) {
			Set<UUID> now = after.getOrDefault(e.getKey(), Set.of());
			if (!now.equals(e.getValue())) {
				audit.publish(AuditAction.UPDATE, TASK, e.getKey(), Map.of("relatedTaskIds", ids(e.getValue())),
						Map.of("relatedTaskIds", ids(now)));
			}
		}
	}

	private static List<String> ids(Collection<UUID> ids) {
		return ids.stream().map(UUID::toString).sorted().toList();
	}

	private void publish(TaskEvent.Kind kind, Task task) {
		events.publishEvent(new TaskEvent(kind, task.getId(), task.getSpaceId(), task.getStatusId(),
				task.getAuthorId(), actors.currentActorId(), null, null, null, task.getDeadline()));
	}

}
