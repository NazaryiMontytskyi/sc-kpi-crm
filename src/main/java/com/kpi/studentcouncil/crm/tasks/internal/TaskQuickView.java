package com.kpi.studentcouncil.crm.tasks.internal;

/**
 * Built-in quick views of the task list ("My tasks", "Issued by me", "Overdue"; spec §3). Each one is evaluated for
 * the current user on the task itself and its assignees ({@link TaskSpecs}).
 */
enum TaskQuickView {

	/** The current user is an active assignee of an open task (status category TODO or IN_PROGRESS). */
	MY_TASKS,

	/** The current user created the task and it has at least one active assignee other than them. */
	ISSUED_BY_ME,

	/**
	 * The deadline has passed (end of day, Europe/Kyiv) and the status category is neither DONE nor CANCELED; never
	 * archived tasks.
	 */
	OVERDUE

}
