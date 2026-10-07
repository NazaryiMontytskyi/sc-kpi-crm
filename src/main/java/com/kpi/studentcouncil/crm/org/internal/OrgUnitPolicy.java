package com.kpi.studentcouncil.crm.org.internal;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import com.kpi.studentcouncil.crm.org.OrgUnitType;
import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;
import com.kpi.studentcouncil.crm.shared.error.ConflictException;

/**
 * All structural rules of the org tree in one place (pure, no persistence). Reading of CONTEXT.md 2.2, least
 * restrictive where the spec is silent:
 * <ul>
 * <li>LEADERSHIP: never has a parent; at most one active (checked by the service and a unique index).</li>
 * <li>DEPARTMENT: parent is the LEADERSHIP unit, or none (a department may exist before the leadership unit).</li>
 * <li>DIVISION: parent is required and must be a DEPARTMENT.</li>
 * <li>WORKING_GROUP: outside the hierarchy; no parent, or any unit as an organisational anchor.</li>
 * </ul>
 */
final class OrgUnitPolicy {

	private OrgUnitPolicy() {
	}

	/** @param parentType type of the chosen parent, or null when there is no parent */
	static void assertPlacement(OrgUnitType type, OrgUnitType parentType) {
		switch (type) {
			case LEADERSHIP -> {
				if (parentType != null) {
					throw parentNotAllowed(type, parentType);
				}
			}
			case DEPARTMENT -> {
				if (parentType != null && parentType != OrgUnitType.LEADERSHIP) {
					throw parentNotAllowed(type, parentType);
				}
			}
			case DIVISION -> {
				if (parentType == null) {
					throw new BusinessRuleException("ORG_UNIT_PARENT_REQUIRED", type);
				}
				if (parentType != OrgUnitType.DEPARTMENT) {
					throw parentNotAllowed(type, parentType);
				}
			}
			case WORKING_GROUP -> {
				// no requirement
			}
		}
	}

	/** Rejects moving a unit below itself or one of its descendants. */
	static void assertNoCycle(UUID unitId, UUID newParentId, Function<UUID, UUID> parentOf) {
		Set<UUID> seen = new HashSet<>();
		UUID cursor = newParentId;
		while (cursor != null) {
			if (cursor.equals(unitId)) {
				throw new BusinessRuleException("ORG_UNIT_CYCLE");
			}
			if (!seen.add(cursor)) {
				return; // pre-existing loop elsewhere, not caused by this move
			}
			cursor = parentOf.apply(cursor);
		}
	}

	/** Archive policy [ASSUMPTION]: refuse while active children exist. Change here to cascade or to allow. */
	static void assertCanArchive(long activeChildren) {
		if (activeChildren > 0) {
			throw new ConflictException("ORG_UNIT_HAS_ACTIVE_CHILDREN", activeChildren);
		}
	}

	private static BusinessRuleException parentNotAllowed(OrgUnitType type, OrgUnitType parentType) {
		return new BusinessRuleException("ORG_UNIT_PARENT_NOT_ALLOWED", type, parentType);
	}

}
