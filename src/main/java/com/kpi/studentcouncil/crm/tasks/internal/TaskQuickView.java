package com.kpi.studentcouncil.crm.tasks.internal;

/**
 * Built-in quick views of the task list. Each one is a filter on the non-archived resolutions of a task and is
 * evaluated for the current user ({@link TaskResolutionSpecs}).
 */
enum TaskQuickView {

	/** The current user is the executor or a co-executor of a live resolution (NEW, IN_PROGRESS, SUBMITTED, REWORK). */
	MY_RESOLUTIONS,

	/** The current user is the author of a resolution. */
	ISSUED_BY_ME,

	/** A resolution whose due date has passed (Europe/Kyiv) and that is neither ACCEPTED nor CANCELED. */
	OVERDUE

}
