package com.kpi.studentcouncil.crm.tasks.internal;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.kpi.studentcouncil.crm.tasks.TaskText;
import com.kpi.studentcouncil.crm.tasks.TaskTextProvider;

/**
 * Plain-text view of tasks for the search index: title, description, labels, active assignees and subtask titles.
 */
@Component
class TaskTextProviderImpl implements TaskTextProvider {

	private static final int MAX_DEPTH = 64;

	private final TaskRepository tasks;
	private final TaskAssigneeRepository assignees;

	TaskTextProviderImpl(TaskRepository tasks, TaskAssigneeRepository assignees) {
		this.tasks = tasks;
		this.assignees = assignees;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<TaskText> findTask(UUID taskId) {
		return tasks.findById(taskId).map(t -> {
			List<UUID> assigneeIds = assignees.findByTaskIdAndRemovedAtIsNullOrderByAssignedAtAscIdAsc(t.getId())
					.stream().map(TaskAssignee::getUserId).distinct().toList();
			List<String> subtaskTitles = t.getParentTaskId() != null ? List.of()
					: tasks.findByParentTaskIdOrderByIssuedAtAscIdAsc(t.getId()).stream()
							.filter(s -> !s.isArchived()).map(Task::getTitle).toList();
			return new TaskText(t.getId(), t.getSpaceId(), t.getParentTaskId(), t.getTitle(),
					plainText(t.getDescription()), Set.copyOf(t.getLabels()), assigneeIds, subtaskTitles,
					t.isArchived());
		});
	}

	@Override
	@Transactional(readOnly = true)
	public List<UUID> findActiveTaskIdsAssignedTo(UUID userId) {
		List<UUID> taskIds = assignees.findByUserIdAndRemovedAtIsNull(userId).stream().map(TaskAssignee::getTaskId)
				.distinct().toList();
		if (taskIds.isEmpty()) {
			return List.of();
		}
		return tasks.findByIdIn(taskIds).stream().filter(t -> !t.isArchived()).map(Task::getId).sorted().toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<UUID> findActiveTaskIds() {
		return tasks.findByArchivedAtIsNull().stream().map(Task::getId).toList();
	}

	/** Text nodes of a TipTap document, one line per block; blank for null. */
	static String plainText(Map<String, Object> document) {
		StringBuilder text = new StringBuilder();
		walk(document, 0, text);
		return text.toString().strip();
	}

	private static void walk(Object node, int depth, StringBuilder text) {
		if (depth > MAX_DEPTH || !(node instanceof Map<?, ?> map)) {
			return;
		}
		if (map.get("text") instanceof String s) {
			text.append(s);
		}
		if (map.get("content") instanceof List<?> children) {
			for (Object child : children) {
				walk(child, depth + 1, text);
			}
			if (!text.isEmpty() && text.charAt(text.length() - 1) != '\n') {
				text.append('\n');
			}
		}
	}

}
