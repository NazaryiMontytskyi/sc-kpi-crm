package com.kpi.studentcouncil.crm.tasks;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Domain event published (inside the transaction) after every change of a {@code Task}. {@code search} and
 * {@code notifications} subscribe to it. Kinds:
 * <ul>
 * <li>{@code CREATED}, {@code ARCHIVED}, {@code RESTORED};</li>
 * <li>{@code UPDATED}: an edit of the task fields or links, and a system-driven status remapping (when a status is
 * archived);</li>
 * <li>{@code DEADLINE_CHANGED}: published in addition to {@code UPDATED} when an edit moved the deadline
 * ({@code previousDeadline} -> {@code deadline});</li>
 * <li>{@code STATUS_CHANGED}: a user moved the task to another status ({@code previousStatusId} ->
 * {@code statusId});</li>
 * <li>{@code ASSIGNED} / {@code UNASSIGNED}: {@code assigneeId} was added to / removed from the active
 * assignees.</li>
 * </ul>
 * A no-op change publishes nothing. {@code actorId} is the user who made the change (never notified of it).
 *
 * @param kind             what happened
 * @param id               the task
 * @param spaceId          the space the task lives in
 * @param statusId         the task's current status
 * @param authorId         the creator of the task
 * @param actorId          who made the change (the nil system actor for system changes)
 * @param assigneeId       the added or removed assignee ({@code ASSIGNED}/{@code UNASSIGNED} only)
 * @param previousStatusId the status before ({@code STATUS_CHANGED} only)
 * @param previousDeadline the deadline before ({@code DEADLINE_CHANGED} only; may be null)
 * @param deadline         the task's current deadline (may be null)
 */
public record TaskEvent(Kind kind, UUID id, UUID spaceId, UUID statusId, UUID authorId, UUID actorId,
		UUID assigneeId, UUID previousStatusId, LocalDate previousDeadline, LocalDate deadline) {

	/** An event without change details (kept for existing publishers and tests). */
	public TaskEvent(Kind kind, UUID id, UUID spaceId, UUID statusId, UUID authorId) {
		this(kind, id, spaceId, statusId, authorId, null, null, null, null, null);
	}

	public enum Kind {
		CREATED, UPDATED, ARCHIVED, RESTORED, ASSIGNED, UNASSIGNED, DEADLINE_CHANGED, STATUS_CHANGED
	}

}
