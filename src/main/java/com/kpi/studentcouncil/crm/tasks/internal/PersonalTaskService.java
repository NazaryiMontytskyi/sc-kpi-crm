package com.kpi.studentcouncil.crm.tasks.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kpi.studentcouncil.crm.tasks.MemberTaskCounts;
import com.kpi.studentcouncil.crm.tasks.PersonalTaskSummary;
import com.kpi.studentcouncil.crm.tasks.StatusCategory;

/**
 * Implements {@link PersonalTaskSummary} and {@link MemberTaskCounts}: one person's tasks through active assignments
 * (RR-5). "Active" is the "My tasks" quick view ({@link TaskSpecs#view}), so counters and lists agree; "overdue" is
 * the deadline before today in {@code Europe/Kyiv} (the same rule as {@link TaskSpecs#overdue}).
 */
@Service
class PersonalTaskService implements PersonalTaskSummary, MemberTaskCounts {

	/** Earliest deadline first; PostgreSQL sorts nulls last in ascending order. Ties by id for stable pages. */
	private static final Sort BY_DEADLINE = Sort.by("deadline").and(Sort.by("id"));

	private static final Set<StatusCategory> DONE = Set.of(StatusCategory.DONE);

	private final TaskRepository tasks;
	private final SpaceStatusRepository statuses;
	private final Clock clock;

	PersonalTaskService(TaskRepository tasks, SpaceStatusRepository statuses, Clock clock) {
		this.tasks = tasks;
		this.statuses = statuses;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public Counts countsFor(UUID userId) {
		return new Counts(tasks.countAssignedNotInCategories(userId, TaskSpecs.CLOSED),
				tasks.countAssignedNotInCategoriesDueBefore(userId, TaskSpecs.CLOSED, today()),
				tasks.countAssignedInCategories(userId, DONE));
	}

	@Override
	@Transactional(readOnly = true)
	public Summary summaryFor(UUID userId, int upcomingLimit) {
		long active = tasks.countAssignedNotInCategories(userId, TaskSpecs.CLOSED);
		long overdue = tasks.countAssignedNotInCategoriesDueBefore(userId, TaskSpecs.CLOSED, today());
		List<TaskItem> upcoming = List.of();
		if (upcomingLimit > 0) {
			Specification<Task> withDeadline = (root, query, cb) -> cb.isNotNull(root.get("deadline"));
			upcoming = items(tasks.findActive(mine(userId).and(withDeadline),
					PageRequest.of(0, upcomingLimit, BY_DEADLINE))).getContent();
		}
		return new Summary(active, overdue, upcoming);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<TaskItem> activeTasksOf(UUID userId, Pageable pageable) {
		Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), BY_DEADLINE);
		return items(tasks.findActive(mine(userId), page));
	}

	private Specification<Task> mine(UUID userId) {
		return TaskSpecs.view(TaskQuickView.MY_TASKS, userId, clock);
	}

	private Page<TaskItem> items(Page<Task> page) {
		Set<UUID> statusIds = page.getContent().stream().map(Task::getStatusId).collect(Collectors.toSet());
		Map<UUID, SpaceStatus> byId = statusIds.isEmpty() ? Map.of()
				: statuses.findByIdIn(statusIds).stream()
						.collect(Collectors.toMap(SpaceStatus::getId, Function.identity()));
		LocalDate today = today();
		return page.map(t -> {
			SpaceStatus status = byId.get(t.getStatusId());
			return new TaskItem(t.getId(), t.getParentTaskId(), t.getSpaceId(), t.getTitle(), t.getStatusId(),
					status == null ? "" : status.getName(), status == null ? null : status.getCategory(),
					t.getDeadline(), isOverdue(t.getDeadline(), today));
		});
	}

	/** An active task is overdue from the Kyiv day after its deadline on. */
	static boolean isOverdue(LocalDate deadline, LocalDate today) {
		return deadline != null && deadline.isBefore(today);
	}

	private LocalDate today() {
		return LocalDate.now(clock.withZone(TaskSpecs.DEADLINE_ZONE));
	}

}
