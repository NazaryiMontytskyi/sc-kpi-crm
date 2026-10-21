package com.kpi.studentcouncil.crm.tasks.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kpi.studentcouncil.crm.identity.UserDirectory;
import com.kpi.studentcouncil.crm.shared.error.NotFoundException;
import com.kpi.studentcouncil.crm.tasks.MemberResolutionCounts;
import com.kpi.studentcouncil.crm.tasks.PersonalResolutionSummary;

/** Resolutions of one member (executor view or author view) and their counters. Member id equals user id. */
@Service
class MemberResolutionService implements MemberResolutionCounts, PersonalResolutionSummary {

	/** Which side of a resolution the member is on. */
	enum Role {
		/** Executor or co-executor ("My resolutions"). */
		EXECUTOR,
		/** Author ("Issued by me"). */
		AUTHOR
	}

	private static final Set<ResolutionState> ACCEPTED = Set.of(ResolutionState.ACCEPTED);
	private static final Set<ResolutionState> NOT_OVERDUE_STATES = Set.of(ResolutionState.ACCEPTED,
			ResolutionState.CANCELED);

	private final ResolutionRepository resolutions;
	private final TaskRepository tasks;
	private final UserDirectory users;
	private final Clock clock;

	MemberResolutionService(ResolutionRepository resolutions, TaskRepository tasks, UserDirectory users, Clock clock) {
		this.resolutions = resolutions;
		this.tasks = tasks;
		this.users = users;
		this.clock = clock;
	}

	/** A resolution with the title of its task. */
	record Item(Resolution resolution, String taskTitle) {
	}

	@Transactional(readOnly = true)
	Page<Item> list(UUID memberId, Role role, boolean active, ResolutionState state, Pageable pageable) {
		requireMember(memberId);
		Specification<Resolution> spec = role == Role.AUTHOR
				? (root, query, cb) -> cb.equal(root.get("authorId"), memberId)
				: (root, query, cb) -> cb.or(cb.equal(root.get("executorId"), memberId),
						cb.isMember(memberId, root.<Set<UUID>>get("coExecutorIds")));
		if (active) {
			spec = spec.and((root, query, cb) -> root.get("state").in(TaskResolutionSpecs.LIVE));
		}
		if (state != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("state"), state));
		}
		Page<Resolution> page = resolutions.findActive(spec, pageable);
		Collection<UUID> taskIds = page.getContent().stream().map(Resolution::getTaskId).collect(Collectors.toSet());
		Map<UUID, String> titles = tasks.findByIdIn(taskIds).stream()
				.collect(Collectors.toMap(Task::getId, Task::getTitle));
		return page.map(r -> new Item(r, titles.getOrDefault(r.getTaskId(), "")));
	}

	@Override
	@Transactional(readOnly = true)
	public Counts countsFor(UUID userId) {
		requireMember(userId);
		LocalDate today = LocalDate.now(clock.withZone(ResolutionStateMachine.DISPLAY_ZONE));
		return new Counts(resolutions.countExecuting(userId, TaskResolutionSpecs.LIVE),
				resolutions.countExecutingOverdue(userId, NOT_OVERDUE_STATES, today),
				resolutions.countExecuting(userId, ACCEPTED));
	}

	@Override
	@Transactional(readOnly = true)
	public Summary summaryFor(UUID userId, int upcomingLimit) {
		requireMember(userId);
		LocalDate today = LocalDate.now(clock.withZone(ResolutionStateMachine.DISPLAY_ZONE));
		long active = resolutions.countExecuting(userId, TaskResolutionSpecs.LIVE);
		long overdue = resolutions.countExecutingOverdue(userId, NOT_OVERDUE_STATES, today);
		long awaiting = resolutions.countAuthored(userId, Set.of(ResolutionState.SUBMITTED));
		java.util.List<UpcomingItem> upcoming = upcomingLimit <= 0 ? java.util.List.of()
				: list(userId, Role.EXECUTOR, true, null,
						PageRequest.of(0, upcomingLimit, Sort.by("dueDate").and(Sort.by("id")))).getContent().stream()
						.map(i -> new UpcomingItem(i.resolution().getId(), i.resolution().getTaskId(), i.taskTitle(),
								i.resolution().getDueDate(), i.resolution().getState().name(),
								i.resolution().isOverdue(clock)))
						.toList();
		return new Summary(active, overdue, awaiting, upcoming);
	}

	private void requireMember(UUID id) {
		if (!users.exists(id)) {
			throw new NotFoundException("Member", id);
		}
	}

}
