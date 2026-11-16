package com.kpi.studentcouncil.crm.tasks.internal;

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
 * Task specification for the legacy resolution-based filters ({@code executorId}, {@code authorId},
 * {@code resolutionState}): a task matches when it has a non-archived resolution that satisfies all the given
 * conditions at once. The quick views moved to the task itself ({@link TaskSpecs}).
 */
final class TaskResolutionSpecs {

	/** States in which a resolution still needs work from its executor. */
	static final Set<ResolutionState> LIVE = Set.of(ResolutionState.NEW, ResolutionState.IN_PROGRESS,
			ResolutionState.SUBMITTED, ResolutionState.REWORK);

	private TaskResolutionSpecs() {
	}

	/** Pass-through when the filter has no resolution condition. */
	static Specification<Task> of(TaskFilter filter) {
		if (filter.executorId() == null && filter.authorId() == null && filter.resolutionState() == null) {
			return (root, query, cb) -> cb.conjunction();
		}
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
			sub.select(cb.literal(1)).where(where.toArray(Predicate[]::new));
			return cb.exists(sub);
		};
	}

}
