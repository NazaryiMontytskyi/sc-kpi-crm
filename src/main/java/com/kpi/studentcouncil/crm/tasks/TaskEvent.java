package com.kpi.studentcouncil.crm.tasks;

import java.util.UUID;

/**
 * Domain event published (inside the transaction) after every change of a {@code Task}: created, updated (including
 * status changes and changes of related links), archived and restored. {@code search} and {@code notifications}
 * subscribe to it.
 *
 * @param kind     what happened
 * @param id       the task
 * @param spaceId  the space the task lives in
 * @param statusId the task's current status
 * @param authorId the user who created the task
 */
public record TaskEvent(Kind kind, UUID id, UUID spaceId, UUID statusId, UUID authorId) {

	public enum Kind {
		CREATED, UPDATED, ARCHIVED, RESTORED
	}

}
