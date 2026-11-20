package com.kpi.studentcouncil.crm.tasks;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Searchable text of a task: the title, the plain text extracted from the rich-text description, the active
 * assignees and the titles of the non-archived subtasks (RR-5). Assignee names are not resolved here: the
 * {@code tasks} module knows only user ids; the index resolves names through the {@code members} public API.
 *
 * @param id            the task
 * @param spaceId       the space the task lives in
 * @param parentId      the parent task when this is a subtask, else null
 * @param title         the title
 * @param text          plain text of the description (blank when there is none)
 * @param labels        labels of the task
 * @param assigneeIds   active (not removed) assignees, oldest assignment first
 * @param subtaskTitles titles of the non-archived subtasks, oldest issue first (empty for a subtask)
 * @param archived      whether the task is archived
 */
public record TaskText(UUID id, UUID spaceId, UUID parentId, String title, String text, Set<String> labels,
		List<UUID> assigneeIds, List<String> subtaskTitles, boolean archived) {

	public TaskText {
		labels = labels == null ? Set.of() : Set.copyOf(labels);
		assigneeIds = assigneeIds == null ? List.of() : List.copyOf(assigneeIds);
		subtaskTitles = subtaskTitles == null ? List.of() : List.copyOf(subtaskTitles);
	}

}
