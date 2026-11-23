package com.kpi.studentcouncil.crm.tasks;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Public API of the {@code tasks} module: the tasks one person works on, through active assignments (RR-5), for the
 * personal dashboard ("My metrics", "My tasks") and the member profile. "Active" uses the same rule as the "My tasks" quick view, so counts match the
 * list: the person is an active (not removed) assignee, the task is not archived and its status is neither in the
 * DONE nor in the CANCELED category. Subtasks are tasks too and are included. Exposes no entities and checks no
 * permission (the caller does).
 */
public interface PersonalTaskSummary {

	/**
	 * One active task of the person.
	 *
	 * @param taskId         the task
	 * @param parentId       the parent task when this is a subtask, else null
	 * @param spaceId        the space the task lives in
	 * @param title          the title
	 * @param statusId       the current status
	 * @param statusName     the name of the current status
	 * @param statusCategory the category of the current status (TODO or IN_PROGRESS for an active task)
	 * @param deadline       a calendar day in Europe/Kyiv, or null
	 * @param overdue        the deadline is before today in Europe/Kyiv
	 */
	record TaskItem(UUID taskId, UUID parentId, UUID spaceId, String title, UUID statusId, String statusName,
			StatusCategory statusCategory, LocalDate deadline, boolean overdue) {
	}

	/**
	 * @param active   active tasks the person is assigned to
	 * @param overdue  of those: deadline before today (Europe/Kyiv)
	 * @param upcoming active tasks with a deadline, earliest deadline first (overdue ones therefore come first),
	 *                 ties by id; tasks without a deadline are not "upcoming"
	 */
	record Summary(long active, long overdue, List<TaskItem> upcoming) {

		public Summary {
			upcoming = upcoming == null ? List.of() : List.copyOf(upcoming);
		}

	}

	/** Counters and the first {@code upcomingLimit} upcoming tasks of the user (none for a limit of 0 or less). */
	Summary summaryFor(UUID userId, int upcomingLimit);

	/**
	 * A page of the user's active tasks, earliest deadline first, tasks without a deadline last, ties by id. The sort
	 * of {@code pageable} is ignored; only its page number and size are used.
	 */
	Page<TaskItem> activeTasksOf(UUID userId, Pageable pageable);

}
