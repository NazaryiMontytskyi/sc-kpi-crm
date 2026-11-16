package com.kpi.studentcouncil.crm.tasks.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;

/**
 * Pure rules of subtasks (spec ST-1, ST-3, TM-4; ADR-0010). No side effects; the service applies them.
 * <ul>
 * <li><b>Depth</b> ({@code [ASSUMPTION]} one level): a subtask cannot have subtasks.</li>
 * <li><b>Deadline bound</b> ({@code [ASSUMPTION]}): a subtask deadline must not be later than the parent deadline; a
 * parent without a deadline imposes no limit, and a subtask without a deadline is always within the bound. The rule
 * applies to <em>new edits</em> only: an edit that does not touch the deadline is never checked, so subtasks migrated
 * from resolutions (not clamped, INC-072) stay editable.</li>
 * <li><b>Moving the parent deadline</b>: rejected while it would leave a non-archived subtask with a later deadline,
 * naming those subtasks. A pre-existing violation (a migrated subtask that was already later than the previous
 * parent deadline) is tolerated when the parent deadline moves later, because that move does not make anything
 * worse; moving it earlier, or setting a first deadline, reports every violation.</li>
 * <li><b>Archive cascade</b>: restoring a parent restores only the subtasks archived together with it (same archive
 * instant and actor); subtasks archived on their own before stay archived.</li>
 * </ul>
 */
final class SubtaskRules {

	private SubtaskRules() {
	}

	/** A subtask as seen by the deadline rule. */
	record Subtask(UUID id, String title, LocalDate deadline) {
	}

	/** ST-1: the would-be parent must itself be a top-level task (422 {@code SUBTASK_DEPTH_EXCEEDED}). */
	static void assertCanHaveSubtasks(UUID parentOfParent) {
		if (parentOfParent != null) {
			throw new BusinessRuleException("SUBTASK_DEPTH_EXCEEDED");
		}
	}

	/** ST-3: true when the subtask deadline does not exceed the parent deadline (null on either side: no limit). */
	static boolean withinParent(LocalDate subtaskDeadline, LocalDate parentDeadline) {
		return subtaskDeadline == null || parentDeadline == null || !subtaskDeadline.isAfter(parentDeadline);
	}

	/** ST-3 for a new or changed subtask deadline (422 {@code SUBTASK_DEADLINE_AFTER_PARENT}, argument: parent deadline). */
	static void assertWithinParent(LocalDate subtaskDeadline, LocalDate parentDeadline) {
		if (!withinParent(subtaskDeadline, parentDeadline)) {
			throw new BusinessRuleException("SUBTASK_DEADLINE_AFTER_PARENT", parentDeadline.toString());
		}
	}

	/**
	 * ST-3 for a parent deadline change {@code previous -> next}: the subtasks that the new deadline would leave with a
	 * later deadline, ordered by deadline (latest first), then title and id. Empty when {@code next} is null (no limit)
	 * or nothing conflicts. A subtask that already exceeded {@code previous} is not reported when the deadline moves
	 * later ({@code next >= previous}). Callers pass non-archived subtasks only.
	 */
	static List<Subtask> conflicting(LocalDate previous, LocalDate next, Collection<Subtask> subtasks) {
		if (next == null) {
			return List.of();
		}
		boolean movedLater = previous != null && !next.isBefore(previous);
		return subtasks.stream().filter(s -> !withinParent(s.deadline(), next))
				.filter(s -> !(movedLater && !withinParent(s.deadline(), previous)))
				.sorted(Comparator.comparing(Subtask::deadline).reversed()
						.thenComparing(Subtask::title, Comparator.nullsLast(Comparator.naturalOrder()))
						.thenComparing(Subtask::id))
				.toList();
	}

	/**
	 * TM-4 restore cascade: a subtask is restored with its parent only when it was archived by the parent's archive
	 * cascade, i.e. with the same archive instant and actor as the parent.
	 */
	static boolean archivedWithParent(Instant subtaskArchivedAt, UUID subtaskArchivedBy, Instant parentArchivedAt,
			UUID parentArchivedBy) {
		return subtaskArchivedAt != null && subtaskArchivedAt.equals(parentArchivedAt)
				&& Objects.equals(subtaskArchivedBy, parentArchivedBy);
	}

}
