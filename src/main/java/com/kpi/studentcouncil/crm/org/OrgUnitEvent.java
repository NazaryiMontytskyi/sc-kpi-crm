package com.kpi.studentcouncil.crm.org;

import java.util.UUID;

/**
 * Domain event published (inside the transaction) after every change of an {@code OrgUnit}.
 *
 * @param kind     what happened
 * @param id       the unit
 * @param type     the unit type
 * @param parentId parent after the change, or null
 */
public record OrgUnitEvent(Kind kind, UUID id, OrgUnitType type, UUID parentId) {

	public enum Kind {
		CREATED, UPDATED, MOVED, ARCHIVED, RESTORED
	}

}
