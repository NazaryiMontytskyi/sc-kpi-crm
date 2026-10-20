package com.kpi.studentcouncil.crm.tasks;

import java.util.Set;
import java.util.UUID;

/**
 * Domain event published (inside the transaction) when a resolution changes. {@code notifications} subscribes to it and
 * needs the ids of everybody involved: author, executor and co-executors.
 *
 * @param kind          what happened
 * @param id            the resolution
 * @param taskId        the task it belongs to
 * @param authorId      who issued it
 * @param executorId    who has to carry it out
 * @param coExecutorIds the co-executors (never contains the executor)
 * @param actorId       the user who caused the event
 */
public record ResolutionEvent(Kind kind, UUID id, UUID taskId, UUID authorId, UUID executorId,
		Set<UUID> coExecutorIds, UUID actorId) {

	public enum Kind {
		/** A resolution was issued (state NEW). */
		ISSUED,
		/** The executor took it into work (also after rework). */
		ACCEPTED,
		/** The executor submitted the result with a completion note. */
		SUBMITTED,
		/** The author returned the result for rework. */
		REWORKED,
		/** The author accepted the result: the resolution is closed as done. */
		CLOSED,
		/** The author canceled it (also for descendants canceled by the cascade). */
		CANCELED
	}

}
