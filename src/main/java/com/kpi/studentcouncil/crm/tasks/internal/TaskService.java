package com.kpi.studentcouncil.crm.tasks.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

	/** A task together with the data that lives outside its row; {@code attachments} is null in list results. */
	record TaskView(Task task, Set<UUID> relatedTaskIds, List<FileInfo> attachments, boolean suggestDone) {
	}

	/** Filters of the task list. */
	record TaskFilter(UUID spaceId, UUID statusId, Collection<String> labels, LocalDate dueFrom, LocalDate dueTo,
			boolean includeArchived) {
	}

	private final TaskRepository tasks;
	private final TaskLinkRepository links;
	private final SpaceRepository spaces;
	private final SpaceStatusRepository statuses;
	private final ResolutionRepository resolutions;
	private final CustomFieldDefinitionRepository fields;
	private final FileService files;
	private final UserDirectory users;
	private final TaskEditPolicy editPolicy;
	private final AuditPublisher audit;
	private final ActorProvider actors;
	private final ApplicationEventPublisher events;
	private final Clock clock;

	TaskService(TaskRepository tasks, TaskLinkRepository links, SpaceRepository spaces, SpaceStatusRepository statuses,
			ResolutionRepository resolutions, CustomFieldDefinitionRepository fields, FileService files, UserDirectory users, TaskEditPolicy editPolicy,
			AuditPublisher audit, ActorProvider actors, ApplicationEventPublisher events, Clock clock) {
		this.tasks = tasks;
		this.links = links;
		this.spaces = spaces;
		this.statuses = statuses;
		this.resolutions = resolutions;
		this.fields = fields;
		this.files = files;
		this.users = users;
		this.editPolicy = editPolicy;
		this.audit = audit;
		this.actors = actors;
		this.events = events;
		this.clock = clock;
	}

	// ---- commands ----

	@Transactional
	TaskView create(UUID spaceId, String title, Map<String, Object> description, UUID statusId, TaskPriority priority,
			LocalDate dueDate, Collection<String> labels, Map<String, Object> customFieldValues,
			Collection<UUID> attachmentIds, Collection<UUID> relatedTaskIds) {
		Space space = spaces.lockById(spaceId).orElseThrow(() -> new NotFoundException(SpaceService.SPACE, spaceId));
		if (space.isArchived()) {
			throw new ConflictException("SPACE_ARCHIVED");
		}
		UUID status = resolveInitialStatus(spaceId, statusId);
		Map<String, Object> values = TaskRules.validateCustomFields(fieldRules(spaceId), customFieldValues,
				users::exists);
		List<UUID> attachments = TaskRules.normalizeAttachments(attachmentIds);
		attachments.forEach(this::assertFileUsable);
		Set<UUID> related = TaskRules.normalizeRelated(relatedTaskIds, null);
		assertLinkable(related);
		Set<String> normalizedLabels = TaskRules.normalizeLabels(labels);

		Task saved = tasks.save(new Task(spaceId, title.strip(), description, status,
				priority == null ? TaskPriority.NORMAL : priority, dueDate, normalizedLabels, attachments, values));
		Map<UUID, Set<UUID>> counterpartsBefore = relatedOfAll(related);
		related.forEach(other -> links.save(new TaskLink(TaskLinkId.of(saved.getId(), other))));
		audit.publish(AuditAction.CREATE, TASK, saved.getId(), null, saved.auditSnapshot(related));
		auditCounterparts(counterpartsBefore);
		publish(TaskEvent.Kind.CREATED, saved);
		return view(saved, related);
	}

	/** Replaces the editable fields. Author or Admin only ({@link TaskEditPolicy}). */
	@Transactional
	TaskView update(UUID id, String title, Map<String, Object> description, TaskPriority priority, LocalDate dueDate,
			Collection<String> labels, Map<String, Object> customFieldValues, Collection<UUID> attachmentIds,
			Collection<UUID> relatedTaskIds) {
		Task task = lockActive(id);
		editPolicy.assertCanEdit(task);

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

		Map<String, Object> before = task.auditSnapshot(currentRelated);
		task.update(title.strip(), description, priority == null ? TaskPriority.NORMAL : priority, dueDate,
				normalizedLabels, attachments, values);
		Task saved = tasks.save(task);

		Set<UUID> touched = new LinkedHashSet<>(related);
		touched.addAll(currentRelated);
		Map<UUID, Set<UUID>> counterpartsBefore = relatedOfAll(touched);
		without(related, currentRelated).forEach(o -> links.save(new TaskLink(TaskLinkId.of(id, o))));
		without(currentRelated, related).forEach(o -> links.delete(new TaskLink(TaskLinkId.of(id, o))));
		audit.publish(AuditAction.UPDATE, TASK, id, before, saved.auditSnapshot(related));
		auditCounterparts(counterpartsBefore);
		publish(TaskEvent.Kind.UPDATED, saved);
		return view(saved, related);
	}

	/** Moves the task to another status of its space. Same rule as editing. */
	@Transactional
	TaskView changeStatus(UUID id, UUID statusId) {
		UUID spaceId = require(id).getSpaceId();
		spaces.lockById(spaceId).orElseThrow(() -> new NotFoundException(SpaceService.SPACE, spaceId));
		Task task = lockActive(id);
		editPolicy.assertCanChangeStatus(task);
		SpaceStatus target = statuses.findBySpaceIdAndArchivedAtIsNullOrderByPositionAscIdAsc(task.getSpaceId())
				.stream().filter(s -> s.getId().equals(statusId)).findFirst()
				.orElseThrow(() -> new BusinessRuleException("TASK_STATUS_NOT_IN_SPACE"));
		Set<UUID> related = relatedOf(id);
		if (!target.getId().equals(task.getStatusId())) {
			Map<String, Object> before = task.auditSnapshot(related);
			task.changeStatus(target.getId());
			tasks.save(task);
			audit.publish(AuditAction.UPDATE, TASK, id, before, task.auditSnapshot(related));
			publish(TaskEvent.Kind.UPDATED, task);
		}
		return view(task, related);
	}

	@Transactional
	TaskView archive(UUID id) {
		Task task = lock(id);
		Set<UUID> related = relatedOf(id);
		Map<String, Object> before = task.auditSnapshot(related);
		task.archive(actors.currentActorId(), clock.instant());
		Task saved = tasks.save(task);
		audit.publish(AuditAction.ARCHIVE, TASK, id, before, saved.auditSnapshot(related));
		publish(TaskEvent.Kind.ARCHIVED, saved);
		return view(saved, related);
	}

	@Transactional
	TaskView restore(UUID id) {
		UUID spaceId = require(id).getSpaceId();
		Space space = spaces.lockById(spaceId).orElseThrow(() -> new NotFoundException(SpaceService.SPACE, spaceId));
		Task task = lock(id);
		if (!task.isArchived()) {
			throw new ConflictException("NOT_ARCHIVED");
		}
		if (space.isArchived()) {
			throw new ConflictException("SPACE_ARCHIVED");
		}
		Set<UUID> related = relatedOf(id);
		Map<String, Object> before = task.auditSnapshot(related);
		task.restore();
		Task saved = tasks.save(task);
		audit.publish(AuditAction.RESTORE, TASK, id, before, saved.auditSnapshot(related));
		publish(TaskEvent.Kind.RESTORED, saved);
		return view(saved, related);
	}

	// ---- queries ----

	@Transactional(readOnly = true)
	TaskView get(UUID id) {
		Task task = require(id);
		return view(task, relatedOf(id));
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
		if (filter.dueFrom() != null) {
			spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.<LocalDate>get("dueDate"), filter.dueFrom()));
		}
		if (filter.dueTo() != null) {
			spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.<LocalDate>get("dueDate"), filter.dueTo()));
		}
		Set<String> labels = filter.labels() == null || filter.labels().isEmpty() ? Set.of()
				: TaskRules.normalizeLabels(filter.labels());
		if (!labels.isEmpty()) {
			// any of the labels
			spec = spec.and((root, query, cb) -> cb.or(labels.stream()
					.map(label -> cb.isMember(label, root.<Set<String>>get("labels"))).toArray(jakarta.persistence.criteria.Predicate[]::new)));
		}
		Page<Task> page = tasks.findAll(spec, pageable);
		Map<UUID, Set<UUID>> related = relatedOfAll(page.getContent().stream().map(Task::getId).toList());
		Map<UUID, Boolean> suggestions = suggestDoneOfAll(page.getContent());
		return page.map(t -> new TaskView(t, related.getOrDefault(t.getId(), Set.of()), null,
				suggestions.getOrDefault(t.getId(), false)));
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
			audit.publish(AuditAction.UPDATE, TASK, task.getId(), before, saved.auditSnapshot(related));
			publish(TaskEvent.Kind.UPDATED, saved);
		}
	}

	// ---- helpers ----

	private TaskView view(Task task, Set<UUID> related) {
		return new TaskView(task, related, attachmentInfos(task),
				suggestDoneOfAll(List.of(task)).getOrDefault(task.getId(), false));
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
				task.getAuthorId()));
	}

}
