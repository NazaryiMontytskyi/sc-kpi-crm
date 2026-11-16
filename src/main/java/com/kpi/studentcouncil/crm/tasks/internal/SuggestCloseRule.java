package com.kpi.studentcouncil.crm.tasks.internal;

import java.util.Collection;

import com.kpi.studentcouncil.crm.tasks.StatusCategory;

/**
 * The "all subtasks are done" hint (spec ST-4, ST-5; successor of {@link SuggestDoneRule}). Pure functions, no side
 * effects: the parent status is never changed automatically, and a parent may be closed with open subtasks (the
 * client warns using {@link #openCount}).
 */
final class SuggestCloseRule {

	private SuggestCloseRule() {
	}

	/** A subtask as seen by the rule: the category of its status and whether it is archived. */
	record Subtask(StatusCategory category, boolean archived) {
	}

	/** Progress of the subtasks of one task. */
	record Progress(int total, int open, boolean suggestClose) {

		static final Progress NONE = new Progress(0, 0, false);

	}

	/**
	 * True iff the parent is not already closed (DONE or CANCELED category), at least one non-archived, non-canceled
	 * subtask exists and every such subtask is in the DONE category. Archived and CANCELED subtasks are ignored.
	 */
	static boolean suggest(StatusCategory parentCategory, Collection<Subtask> subtasks) {
		if (parentCategory == StatusCategory.DONE || parentCategory == StatusCategory.CANCELED) {
			return false;
		}
		boolean anyCounted = false;
		for (Subtask s : subtasks) {
			if (s.archived() || s.category() == StatusCategory.CANCELED) {
				continue;
			}
			if (s.category() != StatusCategory.DONE) {
				return false;
			}
			anyCounted = true;
		}
		return anyCounted;
	}

	/** Open subtasks: non-archived, status category neither DONE nor CANCELED (an unknown category counts as open). */
	static int openCount(Collection<Subtask> subtasks) {
		return (int) subtasks.stream().filter(s -> !s.archived())
				.filter(s -> s.category() != StatusCategory.DONE && s.category() != StatusCategory.CANCELED).count();
	}

	/** Non-archived subtasks. */
	static int activeCount(Collection<Subtask> subtasks) {
		return (int) subtasks.stream().filter(s -> !s.archived()).count();
	}

	static Progress progress(StatusCategory parentCategory, Collection<Subtask> subtasks) {
		return new Progress(activeCount(subtasks), openCount(subtasks), suggest(parentCategory, subtasks));
	}

}
