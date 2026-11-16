package com.kpi.studentcouncil.crm.tasks.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.jpa.domain.Specification;

import com.kpi.studentcouncil.crm.tasks.StatusCategory;

/**
 * Task list specifications built on the task and its assignees: filters {@code assignee}, {@code creator},
 * {@code overdue} and the quick views (spec §3, §6.3). Deadlines are dates due at the end of that day in
 * {@code Europe/Kyiv}, so a task is overdue from the next Kyiv day on.
 */
final class TaskSpecs {

	static final ZoneId DEADLINE_ZONE = ZoneId.of("Europe/Kyiv");

	/** Status categories of finished work: such tasks are never overdue. */
	static final Set<StatusCategory> CLOSED = Set.of(StatusCategory.DONE, StatusCategory.CANCELED);

	private TaskSpecs() {
	}

	/** The user is an active (not removed) assignee of the task. */
	static Specification<Task> assignee(UUID userId) {
		return (root, query, cb) -> activeAssignee(root, query, cb, userId, false);
	}

	static Specification<Task> creator(UUID userId) {
		return (root, query, cb) -> cb.equal(root.get("creatorId"), userId);
	}

	/** Deadline before today (Kyiv), status not in a DONE/CANCELED category, task not archived. */
	static Specification<Task> overdue(Clock clock) {
		LocalDate today = LocalDate.now(clock.withZone(DEADLINE_ZONE));
		return (root, query, cb) -> cb.and(cb.isNull(root.get("archivedAt")),
				cb.lessThan(root.<LocalDate>get("deadline"), today), statusNotIn(root, query, cb, CLOSED));
	}

	/** The quick view evaluated for {@code me}; a user-bound view without a user matches nothing. */
	static Specification<Task> view(TaskQuickView view, UUID me, Clock clock) {
		return switch (view) {
			case MY_TASKS -> me == null ? none()
					: (root, query, cb) -> cb.and(activeAssignee(root, query, cb, me, false),
							statusNotIn(root, query, cb, CLOSED));
			case ISSUED_BY_ME -> me == null ? none()
					: (root, query, cb) -> cb.and(cb.equal(root.get("creatorId"), me),
							activeAssignee(root, query, cb, me, true));
			case OVERDUE -> overdue(clock);
		};
	}

	private static Specification<Task> none() {
		return (root, query, cb) -> cb.disjunction();
	}

	/**
	 * Exists an active assignee row of the task: equal to {@code userId}, or (with {@code other}) different from it.
	 */
	private static Predicate activeAssignee(Root<Task> root, CriteriaQuery<?> query, CriteriaBuilder cb, UUID userId,
			boolean other) {
		Subquery<Integer> sub = query.subquery(Integer.class);
		Root<TaskAssignee> a = sub.from(TaskAssignee.class);
		Predicate user = other ? cb.notEqual(a.get("userId"), userId) : cb.equal(a.get("userId"), userId);
		sub.select(cb.literal(1)).where(cb.equal(a.get("taskId"), root.get("id")), cb.isNull(a.get("removedAt")), user);
		return cb.exists(sub);
	}

	/** The task's status has none of the categories. */
	private static Predicate statusNotIn(Root<Task> root, CriteriaQuery<?> query, CriteriaBuilder cb,
			Set<StatusCategory> categories) {
		Subquery<Integer> sub = query.subquery(Integer.class);
		Root<SpaceStatus> s = sub.from(SpaceStatus.class);
		sub.select(cb.literal(1)).where(cb.equal(s.get("id"), root.get("statusId")), s.get("category").in(categories));
		return cb.not(cb.exists(sub));
	}

}
