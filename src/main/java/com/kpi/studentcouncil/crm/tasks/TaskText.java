package com.kpi.studentcouncil.crm.tasks;

import java.util.Set;
import java.util.UUID;

/**
 * Searchable text of a task: the title and the plain text extracted from the rich-text description.
 *
 * @param id       the task
 * @param spaceId  the space the task lives in
 * @param title    the title
 * @param text     plain text of the description (blank when there is none)
 * @param labels   labels of the task
 * @param archived whether the task is archived
 */
public record TaskText(UUID id, UUID spaceId, String title, String text, Set<String> labels, boolean archived) {
}
