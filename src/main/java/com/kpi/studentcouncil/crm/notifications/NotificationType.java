package com.kpi.studentcouncil.crm.notifications;

/**
 * Kinds of notification. Stored by name; never rename or remove existing values (stored notifications must keep
 * rendering). The i18n key of a type is {@code notification.<NAME>}. Poll and handover types are reserved for later
 * stages and have no producer yet. The {@code RESOLUTION_*} values are deprecated: resolutions were replaced by tasks
 * with assignees and subtasks (TASK_TRACKING_FEATURE NT-6); nothing emits them anymore.
 */
public enum NotificationType {
	/** Deprecated, no longer emitted: a resolution was issued to me (as executor or co-executor), also by redirect. */
	@Deprecated
	RESOLUTION_ISSUED,
	/** Deprecated, no longer emitted: the executor redirected a resolution I authored. */
	@Deprecated
	RESOLUTION_REDIRECTED,
	/** Deprecated, no longer emitted: a resolution I authored was submitted for acceptance. */
	@Deprecated
	RESOLUTION_SUBMITTED,
	/** Deprecated, no longer emitted: the author accepted the result of a resolution I execute. */
	@Deprecated
	RESOLUTION_ACCEPTED,
	/** Deprecated, no longer emitted: the author returned a resolution I execute for rework. */
	@Deprecated
	RESOLUTION_REWORK,
	/** Deprecated, no longer emitted: a resolution redirected from mine was accepted by its author. */
	@Deprecated
	RESOLUTION_CHILD_ACCEPTED,
	/** Deprecated, no longer emitted: a resolution I execute is approaching its due date. */
	@Deprecated
	RESOLUTION_DUE_SOON,
	/** Deprecated, no longer emitted: a resolution I execute or authored passed its due date. */
	@Deprecated
	RESOLUTION_OVERDUE,
	/** I was mentioned in a comment. */
	MENTION,
	/** Reserved [v2]: a poll was opened for me. */
	POLL_OPENED,
	/** Reserved [v2]: a poll I take part in was closed. */
	POLL_CLOSED,
	/** Reserved [v2]: a handover started. */
	HANDOVER_STARTED,
	/** I was assigned to a task. */
	TASK_ASSIGNED,
	/** I was removed from the assignees of a task. */
	TASK_UNASSIGNED,
	/** The deadline of a task I created or am assigned to was changed. */
	TASK_DEADLINE_CHANGED,
	/** A task I am responsible for reaches one of the reminder offsets before its deadline. */
	TASK_DUE_SOON,
	/** A task I am responsible for (or created) passed its deadline. */
	TASK_OVERDUE,
	/** A subtask was added to a task I created or am assigned to. */
	SUBTASK_ADDED,
	/** The status of a task I created or am assigned to was changed. */
	TASK_STATUS_CHANGED
}
