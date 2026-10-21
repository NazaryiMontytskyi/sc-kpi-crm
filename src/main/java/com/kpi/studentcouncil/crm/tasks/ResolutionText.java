package com.kpi.studentcouncil.crm.tasks;

import java.util.UUID;

/**
 * Searchable text of a resolution. A resolution has no title of its own: {@code title} is the first line of the
 * instruction (shortened) and {@code text} the full instruction plus the completion note.
 *
 * @param id       the resolution
 * @param taskId   the task it belongs to
 * @param title    shortened first line of the instruction
 * @param text     instruction and completion note
 * @param archived whether the resolution, or the task it belongs to, is archived
 */
public record ResolutionText(UUID id, UUID taskId, String title, String text, boolean archived) {
}
