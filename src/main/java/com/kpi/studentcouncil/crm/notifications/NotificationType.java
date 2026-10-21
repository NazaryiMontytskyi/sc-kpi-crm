package com.kpi.studentcouncil.crm.notifications;

/**
 * Kinds of notification. Stored by name; never rename existing values. The i18n key of a type is
 * {@code notification.<NAME>}. Poll and handover types are reserved for later stages and have no producer yet.
 */
public enum NotificationType {
	/** A resolution was issued to me (as executor or co-executor), also by redirect. */
	RESOLUTION_ISSUED,
	/** The executor redirected a resolution I authored. */
	RESOLUTION_REDIRECTED,
	/** A resolution I authored was submitted for acceptance. */
	RESOLUTION_SUBMITTED,
	/** The author accepted the result of a resolution I execute. */
	RESOLUTION_ACCEPTED,
	/** The author returned a resolution I execute for rework. */
	RESOLUTION_REWORK,
	/** A resolution redirected from mine was accepted by its author. */
	RESOLUTION_CHILD_ACCEPTED,
	/** I was mentioned in a comment. */
	MENTION,
	/** Reserved [v2]: a poll was opened for me. */
	POLL_OPENED,
	/** Reserved [v2]: a poll I take part in was closed. */
	POLL_CLOSED,
	/** Reserved [v2]: a handover started. */
	HANDOVER_STARTED
}
