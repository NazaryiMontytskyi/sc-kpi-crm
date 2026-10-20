package com.kpi.studentcouncil.crm.tasks.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.kpi.studentcouncil.crm.identity.UserDirectory;
import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;
import com.kpi.studentcouncil.crm.shared.error.ConflictException;
import com.kpi.studentcouncil.crm.shared.error.NotFoundException;
import com.kpi.studentcouncil.crm.tasks.ChildResolutionAccepted;
import com.kpi.studentcouncil.crm.tasks.ResolutionEvent;

/**
 * Commands and queries of resolutions, including redirect and the chain. The acting
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
	private final ApplicationEventPublisher events;
	private final Clock clock;

	ResolutionService(ResolutionRepository resolutions, TaskRepository tasks, UserDirectory users, AuditPublisher audit,
			ActorProvider actors, ApplicationEventPublisher events, Clock clock) {
		this.events = events;
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
		if (dueDate != null && dueDate.isBefore(LocalDate.now(clock.withZone(ResolutionStateMachine.DISPLAY_ZONE)))) {
			throw new BusinessRuleException("RESOLUTION_DUE_DATE_IN_PAST");
		}
		if (executorId == null || !users.isActive(executorId)) {
			throw new BusinessRuleException("RESOLUTION_EXECUTOR_INVALID");
		}
		Set<UUID> coExecutors = new LinkedHashSet<>(coExecutorIds == null ? List.of() : coExecutorIds);
		coExecutors.remove(executorId);
		if (coExecutors.stream().anyMatch(id -> id == null || !users.isActive(id))) {
			throw new BusinessRuleException("RESOLUTION_CO_EXECUTOR_INVALID");
		}
		Resolution saved = resolutions.save(new Resolution(taskId, actors.currentActorId(), executorId, coExecutors,
				instruction, dueDate, null, clock.instant()));
		audit.publish(AuditAction.CREATE, RESOLUTION, saved.getId(), null, saved.auditSnapshot());
		publish(ResolutionEvent.Kind.ISSUED, saved);
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
			publish(ResolutionEvent.Kind.CANCELED, saved);
		}
		return changed;
	}

	/**
	 * The executor of an IN_PROGRESS resolution hands it over: exactly one child resolution is created (authored by the
	 * redirecting executor) and the parent becomes REDIRECTED, in one transaction. A due date later than the parent's is
	 * accepted and only reported (decision: warning, not enforced). Redirecting to oneself or to anybody who already is in
	 * the chain (author or executor of an ancestor) is rejected.
	 */
	@Transactional
	RedirectResult redirect(UUID id, UUID newExecutorId, String instruction, LocalDate dueDate) {
		Resolution parent = lockActive(id);
		UUID actor = actors.currentActorId();
		// transition and actor first: 403 / 422 before any data validation
		ResolutionStateMachine.check(parent.getState(), ResolutionAction.REDIRECT, actor, parent.getAuthorId(),
				parent.getExecutorId(), null);
		var task = tasks.findById(parent.getTaskId())
				.orElseThrow(() -> new NotFoundException(TaskService.TASK, parent.getTaskId()));
		if (task.isArchived()) {
			throw new ConflictException("TASK_ARCHIVED");
		}
		if (newExecutorId == null || !users.isActive(newExecutorId)) {
			throw new BusinessRuleException("RESOLUTION_EXECUTOR_INVALID");
		}
		if (newExecutorId.equals(actor)) {
			throw new BusinessRuleException("RESOLUTION_REDIRECT_TO_SELF");
		}
		if (chainParticipants(parent).contains(newExecutorId)) {
			throw new BusinessRuleException("RESOLUTION_REDIRECT_CYCLE");
		}
		if (dueDate != null && dueDate.isBefore(LocalDate.now(clock.withZone(ResolutionStateMachine.DISPLAY_ZONE)))) {
			throw new BusinessRuleException("RESOLUTION_DUE_DATE_IN_PAST");
		}
		Map<String, Object> before = parent.auditSnapshot();
		parent.apply(ResolutionAction.REDIRECT, actor, null, clock.instant());
		Resolution savedParent = resolutions.save(parent);
		audit.publish(AuditAction.UPDATE, RESOLUTION, id, before, savedParent.auditSnapshot());
		Resolution child = resolutions.save(new Resolution(parent.getTaskId(), actor, newExecutorId, Set.of(),
				instruction, dueDate, parent.getId(), clock.instant()));
		audit.publish(AuditAction.CREATE, RESOLUTION, child.getId(), null, child.auditSnapshot());
		publish(ResolutionEvent.Kind.REDIRECTED, savedParent);
		publish(ResolutionEvent.Kind.ISSUED, child);
		return new RedirectResult(savedParent, child, dueDate != null && dueDate.isAfter(parent.getDueDate()));
	}

	/** Parent (now REDIRECTED), the new child, and whether the child's due date is later than the parent's. */
	record RedirectResult(Resolution parent, Resolution child, boolean dueAfterParent) {
	}

	/** The redirect chain as a tree. */
	record ChainNode(Resolution resolution, List<ChainNode> children) {
	}

	/** The whole chain the resolution belongs to, as a tree rooted at the first resolution. */
	@Transactional(readOnly = true)
	ChainNode chain(UUID id) {
		Resolution root = get(id);
		Set<UUID> seen = new java.util.HashSet<>();
		seen.add(root.getId());
		while (root.getParentResolutionId() != null) {
			Resolution up = resolutions.findById(root.getParentResolutionId()).orElse(null);
			if (up == null || !seen.add(up.getId())) {
				break;
			}
			root = up;
		}
		return buildNode(root, new java.util.HashSet<>());
	}

	private ChainNode buildNode(Resolution resolution, Set<UUID> seen) {
		seen.add(resolution.getId());
		List<ChainNode> children = new java.util.ArrayList<>();
		for (Resolution child : resolutions.findByParentResolutionIdOrderByCreatedAtAscIdAsc(resolution.getId())) {
			if (!seen.contains(child.getId())) {
				children.add(buildNode(child, seen));
			}
		}
		return new ChainNode(resolution, children);
	}

	/** Authors and executors of the resolution and all its ancestors. */
	private Set<UUID> chainParticipants(Resolution from) {
		Set<UUID> people = new java.util.HashSet<>();
		Set<UUID> seen = new java.util.HashSet<>();
		Resolution current = from;
		while (current != null && seen.add(current.getId())) {
			people.add(current.getAuthorId());
			people.add(current.getExecutorId());
			current = current.getParentResolutionId() == null ? null
					: resolutions.findById(current.getParentResolutionId()).orElse(null);
		}
		return people;
	}

	@Transactional(readOnly = true)
	Resolution get(UUID id) {
		return resolutions.findById(id).orElseThrow(() -> new NotFoundException(RESOLUTION, id));
	}

	@Transactional(readOnly = true)
	List<Resolution> listByTask(UUID taskId) {
		return resolutions.findByTaskId(taskId);
	}

	/**
	 * Filtered, paginated list of non-archived resolutions. With {@code mine} only those where the current user is the
	 * author, the executor or a co-executor.
	 */
	@Transactional(readOnly = true)
	Page<Resolution> list(ResolutionFilter filter, Pageable pageable) {
		Specification<Resolution> spec = (root, query, cb) -> cb.conjunction();
		if (filter.taskId() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("taskId"), filter.taskId()));
		}
		if (filter.executorId() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("executorId"), filter.executorId()));
		}
		if (filter.authorId() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("authorId"), filter.authorId()));
		}
		if (filter.state() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("state"), filter.state()));
		}
		if (filter.mine()) {
			UUID me = actors.currentActorId();
			spec = spec.and((root, query, cb) -> cb.or(cb.equal(root.get("authorId"), me),
					cb.equal(root.get("executorId"), me), cb.isMember(me, root.<Set<UUID>>get("coExecutorIds"))));
		}
		return resolutions.findActive(spec, pageable);
	}

	/** Filters of {@link #list}; every field is optional. */
	record ResolutionFilter(UUID taskId, UUID executorId, UUID authorId, ResolutionState state, boolean mine) {
	}

	private Resolution act(UUID id, ResolutionAction action, String comment) {
		Resolution resolution = lockActive(id);
		Map<String, Object> before = resolution.auditSnapshot();
		resolution.apply(action, actors.currentActorId(), comment, clock.instant());
		Resolution saved = resolutions.save(resolution);
		audit.publish(AuditAction.UPDATE, RESOLUTION, id, before, saved.auditSnapshot());
		publish(switch (action) {
			case START, RESUME -> ResolutionEvent.Kind.ACCEPTED;
			case SUBMIT -> ResolutionEvent.Kind.SUBMITTED;
			case REQUEST_REWORK -> ResolutionEvent.Kind.REWORKED;
			case ACCEPT -> ResolutionEvent.Kind.CLOSED;
			case CANCEL -> ResolutionEvent.Kind.CANCELED;
			case REDIRECT -> throw new IllegalStateException("Redirect is not handled here");
		}, saved);
		if (action == ResolutionAction.ACCEPT && saved.getParentResolutionId() != null) {
			// tell the parent executor; the parent itself is deliberately left untouched (5.3)
			resolutions.findById(saved.getParentResolutionId()).ifPresent(parent -> events.publishEvent(
					new ChildResolutionAccepted(saved.getId(), parent.getId(), saved.getTaskId(), parent.getExecutorId(),
							saved.getExecutorId(), actors.currentActorId())));
		}
		return saved;
	}

	private void publish(ResolutionEvent.Kind kind, Resolution r) {
		events.publishEvent(new ResolutionEvent(kind, r.getId(), r.getTaskId(), r.getAuthorId(), r.getExecutorId(),
				r.getCoExecutorIds(), actors.currentActorId()));
	}

	private Resolution lockActive(UUID id) {
		Resolution resolution = resolutions.lockById(id).orElseThrow(() -> new NotFoundException(RESOLUTION, id));
		if (resolution.isArchived()) {
			throw new ConflictException("RESOLUTION_ARCHIVED");
		}
		return resolution;
	}

}
