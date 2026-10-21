package com.kpi.studentcouncil.crm.tasks.internal;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.kpi.studentcouncil.crm.tasks.ResolutionText;
import com.kpi.studentcouncil.crm.tasks.TaskText;
import com.kpi.studentcouncil.crm.tasks.TaskTextProvider;

/** Plain-text view of tasks and resolutions for the search index. */
@Component
class TaskTextProviderImpl implements TaskTextProvider {

	static final int MAX_TITLE = 120;
	private static final int MAX_DEPTH = 64;

	private final TaskRepository tasks;
	private final ResolutionRepository resolutions;

	TaskTextProviderImpl(TaskRepository tasks, ResolutionRepository resolutions) {
		this.tasks = tasks;
		this.resolutions = resolutions;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<TaskText> findTask(UUID taskId) {
		return tasks.findById(taskId).map(t -> new TaskText(t.getId(), t.getSpaceId(), t.getTitle(),
				plainText(t.getDescription()), Set.copyOf(t.getLabels()), t.isArchived()));
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<ResolutionText> findResolution(UUID resolutionId) {
		return resolutions.findById(resolutionId).map(r -> {
			boolean taskArchived = tasks.findById(r.getTaskId()).map(Task::isArchived).orElse(true);
			String note = r.getCompletionNote() == null ? "" : "\n" + r.getCompletionNote();
			return new ResolutionText(r.getId(), r.getTaskId(), titleOf(r.getInstruction()),
					r.getInstruction() + note, r.isArchived() || taskArchived);
		});
	}

	@Override
	@Transactional(readOnly = true)
	public List<UUID> findResolutionIdsOfTask(UUID taskId) {
		return resolutions.findByTaskId(taskId).stream().map(Resolution::getId).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<UUID> findActiveTaskIds() {
		return tasks.findByArchivedAtIsNull().stream().map(Task::getId).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<UUID> findActiveResolutionIds() {
		return resolutions.findByArchivedAtIsNull().stream().map(Resolution::getId).toList();
	}

	/** First non-blank line of the instruction, shortened to {@link #MAX_TITLE} characters. */
	static String titleOf(String instruction) {
		String first = instruction == null ? "" : instruction.strip().lines().findFirst().orElse("").strip();
		return first.length() <= MAX_TITLE ? first : first.substring(0, MAX_TITLE - 1).stripTrailing() + "\u2026";
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
