package com.kpi.studentcouncil.crm.tasks.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.jpa.domain.Specification;

import com.kpi.studentcouncil.crm.tasks.internal.TaskService.TaskFilter;

/**
 * Task specification for the resolution-based filters and quick views: a task matches when it has a non-archived
 * resolution that satisfies all the given conditions at once.
 */
final class TaskResolutionSpecs {

	/** States in which a resolution still needs work from its executor. */
	static final Set<ResolutionState> LIVE = Set.of(ResolutionState.NEW, ResolutionState.IN_PROGRESS,
			ResolutionState.SUBMITTED, ResolutionState.REWORK);

	private TaskResolutionSpecs() {
	}

	/** Pass-through when the filter has no resolution condition. {@code me} may be null (then user views match nothing). */
	static Specification<Task> of(TaskFilter filter, UUID me, Clock clock) {
		TaskQuickView view = filter.view();
		if (filter.executorId() == null && filter.authorId() == null && filter.resolutionState() == null && view == null) {
			return (root, query, cb) -> cb.conjunction();
		}
		boolean needsUser = view == TaskQuickView.MY_RESOLUTIONS || view == TaskQuickView.ISSUED_BY_ME;
		if (needsUser && me == null) {
			return (root, query, cb) -> cb.disjunction();
		}
		LocalDate today = LocalDate.now(clock.withZone(ResolutionStateMachine.DISPLAY_ZONE));
		return (root, query, cb) -> {
			Subquery<Integer> sub = query.subquery(Integer.class);
			Root<Resolution> r = sub.from(Resolution.class);
			List<Predicate> where = new ArrayList<>();
			where.add(cb.equal(r.get("taskId"), root.get("id")));
			where.add(cb.isNull(r.get("archivedAt")));
			if (filter.executorId() != null) {
				where.add(cb.or(cb.equal(r.get("executorId"), filter.executorId()),
						cb.isMember(filter.executorId(), r.<Set<UUID>>get("coExecutorIds"))));
			}
			if (filter.authorId() != null) {
				where.add(cb.equal(r.get("authorId"), filter.authorId()));
			}
			if (filter.resolutionState() != null) {
				where.add(cb.equal(r.get("state"), filter.resolutionState()));
			}
			if (view == TaskQuickView.MY_RESOLUTIONS) {
				where.add(cb.or(cb.equal(r.get("executorId"), me), cb.isMember(me, r.<Set<UUID>>get("coExecutorIds"))));
				where.add(r.get("state").in(LIVE));
			}
			else if (view == TaskQuickView.ISSUED_BY_ME) {
				where.add(cb.equal(r.get("authorId"), me));
			}
			else if (view == TaskQuickView.OVERDUE) {
				where.add(cb.lessThan(r.<LocalDate>get("dueDate"), today));
				where.add(cb.not(r.get("state").in(ResolutionState.ACCEPTED, ResolutionState.CANCELED)));
			}
			sub.select(cb.literal(1)).where(where.toArray(Predicate[]::new));
			return cb.exists(sub);
		};
	}

}
