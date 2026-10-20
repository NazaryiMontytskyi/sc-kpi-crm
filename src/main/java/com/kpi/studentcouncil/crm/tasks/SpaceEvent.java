package com.kpi.studentcouncil.crm.tasks;

import java.util.UUID;

/**
 * Domain event published (inside the transaction) after every change of a {@code Space}, including changes of its
 * statuses and custom fields (reported as {@link Kind#UPDATED}).
 *
 * @param kind      what happened
 * @param id        the space
 * @param orgUnitId the bound unit, or null
 * @param general   whether the space is general
 */
public record SpaceEvent(Kind kind, UUID id, UUID orgUnitId, boolean general) {

	public enum Kind {
		CREATED, UPDATED, ARCHIVED, RESTORED
	}

}
