package com.kpi.studentcouncil.crm.tasks.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.audit.AuditAction;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.identity.UserDirectory;
import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;
import com.kpi.studentcouncil.crm.shared.error.ConflictException;
import com.kpi.studentcouncil.crm.shared.error.NotFoundException;

/**
 * Commands and queries of resolutions (internal service; REST comes with INC-033, redirect with INC-034). The acting
 * user is always the current actor. Every change is audited inside the transaction.
 */
@Service
class ResolutionService {

	static final String RESOLUTION = "Resolution";

	private final ResolutionRepository resolutions;
	private final TaskRepository tasks;
	private final UserDirectory users;
	private final AuditPublisher audit;
	private final ActorProvider actors;
	private final Clock clock;

	ResolutionService(ResolutionRepository resolutions, TaskRepository tasks, UserDirectory users, AuditPublisher audit,
			ActorProvider actors, Clock clock) {
		this.resolutions = resolutions;
		this.tasks = tasks;
		this.users = users;
		this.audit = audit;
		this.actors = actors;
		this.clock = clock;
	}

	/** Issues a resolution on an active task; the current actor is the author. Starts in NEW. */
	@Transactional
	Resolution create(UUID taskId, UUID executorId, Collection<UUID> coExecutorIds, String instruction,
			LocalDate dueDate) {
		var task = tasks.findById(taskId).orElseThrow(() -> new NotFoundException(TaskService.TASK, taskId));
		if (task.isArchived()) {
			throw new ConflictException("TASK_ARCHIVED");
		}
		if (executorId == null || !users.exists(executorId)) {
			throw new BusinessRuleException("RESOLUTION_EXECUTOR_INVALID");
		}
		Set<UUID> coExecutors = new LinkedHashSet<>(coExecutorIds == null ? List.of() : coExecutorIds);
		coExecutors.remove(executorId);
		if (coExecutors.stream().anyMatch(id -> id == null || !users.exists(id))) {
			throw new BusinessRuleException("RESOLUTION_CO_EXECUTOR_INVALID");
		}
		Resolution saved = resolutions.save(new Resolution(taskId, actors.currentActorId(), executorId, coExecutors,
				instruction, dueDate, null, clock.instant()));
		audit.publish(AuditAction.CREATE, RESOLUTION, saved.getId(), null, saved.auditSnapshot());
		return saved;
	}

	/** Executor takes the resolution into work (NEW to IN_PROGRESS). */
	@Transactional
	Resolution start(UUID id) {
		return act(id, ResolutionAction.START, null);
	}

	/** Executor submits with a completion note (IN_PROGRESS to SUBMITTED). */
	@Transactional
	Resolution submit(UUID id, String completionNote) {
		return act(id, ResolutionAction.SUBMIT, completionNote);
	}

	/** Author accepts the result (SUBMITTED to ACCEPTED). */
	@Transactional
	Resolution accept(UUID id) {
		return act(id, ResolutionAction.ACCEPT, null);
	}

	/** Author returns it for rework with a mandatory comment (SUBMITTED to REWORK). */
	@Transactional
	Resolution requestRework(UUID id, String comment) {
		return act(id, ResolutionAction.REQUEST_REWORK, comment);
	}

	/** Executor resumes after rework (REWORK to IN_PROGRESS). */
	@Transactional
	Resolution resume(UUID id) {
		return act(id, ResolutionAction.RESUME, null);
	}

	/**
	 * Author cancels; all non-final descendants are canceled with it in the same transaction (decision Q6). Returns every
	 * resolution that changed, the root first.
	 */
	@Transactional
	List<Resolution> cancel(UUID id, String comment) {
		Resolution root = lockActive(id);
		UUID actor = actors.currentActorId();
		Map<UUID, Map<String, Object>> before = new java.util.LinkedHashMap<>();
		before.put(root.getId(), root.auditSnapshot());
		List<Resolution> changed = ResolutionCascade.cancel(root, actor, comment, clock.instant(), parentId -> {
			List<Resolution> children = resolutions.lockChildren(parentId);
			children.forEach(c -> before.put(c.getId(), c.auditSnapshot()));
			return children;
		});
		for (Resolution resolution : changed) {
			Resolution saved = resolutions.save(resolution);
			audit.publish(AuditAction.UPDATE, RESOLUTION, saved.getId(), before.get(saved.getId()),
					saved.auditSnapshot());
		}
		return changed;
	}

	@Transactional(readOnly = true)
	Resolution get(UUID id) {
		return resolutions.findById(id).orElseThrow(() -> new NotFoundException(RESOLUTION, id));
	}

	@Transactional(readOnly = true)
	List<Resolution> listByTask(UUID taskId) {
		return resolutions.findByTaskId(taskId);
	}

	private Resolution act(UUID id, ResolutionAction action, String comment) {
		Resolution resolution = lockActive(id);
		Map<String, Object> before = resolution.auditSnapshot();
		resolution.apply(action, actors.currentActorId(), comment, clock.instant());
		Resolution saved = resolutions.save(resolution);
		audit.publish(AuditAction.UPDATE, RESOLUTION, id, before, saved.auditSnapshot());
		return saved;
	}

	private Resolution lockActive(UUID id) {
		Resolution resolution = resolutions.lockById(id).orElseThrow(() -> new NotFoundException(RESOLUTION, id));
		if (resolution.isArchived()) {
			throw new ConflictException("RESOLUTION_ARCHIVED");
		}
		return resolution;
	}

}
