package com.kpi.studentcouncil.crm.tasks.internal;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kpi.studentcouncil.crm.tasks.ActiveTaskQuery;

/** Implements the public {@link ActiveTaskQuery}; exposes no entities. */
@Service
class ActiveTaskQueryService implements ActiveTaskQuery {

	/** Upper bound of task ids per assignee lookup (keeps the IN list small). */
	private static final int CHUNK = 500;

	private final TaskRepository tasks;

	private final TaskAssigneeRepository assignees;

	ActiveTaskQueryService(TaskRepository tasks, TaskAssigneeRepository assignees) {
		this.tasks = tasks;
		this.assignees = assignees;
	}

	@Override
	@Transactional(readOnly = true)
	public List<ActiveTask> findOpenDueOnOrBefore(LocalDate latestDeadline) {
		List<Task> open = tasks.findOpenWithDeadlineOnOrBefore(latestDeadline, TaskSpecs.CLOSED);
		Map<UUID, Set<UUID>> byTask = new LinkedHashMap<>();
		List<UUID> ids = open.stream().map(Task::getId).toList();
		for (int from = 0; from < ids.size(); from += CHUNK) {
			List<UUID> chunk = ids.subList(from, Math.min(ids.size(), from + CHUNK));
			for (TaskAssignee a : assignees.findByTaskIdInAndRemovedAtIsNullOrderByAssignedAtAscIdAsc(chunk)) {
				byTask.computeIfAbsent(a.getTaskId(), k -> new LinkedHashSet<>()).add(a.getUserId());
			}
		}
		List<ActiveTask> result = new ArrayList<>(open.size());
		for (Task t : open) {
			result.add(new ActiveTask(t.getId(), t.getParentTaskId(), t.getCreatorId(), t.getDeadline(),
					byTask.getOrDefault(t.getId(), Set.of())));
		}
		return result;
	}

	@Override
	@Transactional(readOnly = true)
	public Set<UUID> activeAssigneeIds(UUID taskId) {
		Set<UUID> ids = new LinkedHashSet<>();
		assignees.findByTaskIdAndRemovedAtIsNullOrderByAssignedAtAscIdAsc(taskId).forEach(a -> ids.add(a.getUserId()));
		return ids;
	}

}
