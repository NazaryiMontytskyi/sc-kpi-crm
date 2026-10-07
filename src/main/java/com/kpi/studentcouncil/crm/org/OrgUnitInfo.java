package com.kpi.studentcouncil.crm.org;

import java.util.List;
import java.util.UUID;

/**
 * Read-only view of an {@code OrgUnit} for other modules (the entity stays internal).
 *
 * @param id          unit id
 * @param name        unit name
 * @param type        unit type
 * @param parentId    parent id, or null for a root
 * @param ancestorIds ids of all ancestors, nearest parent first, root last
 * @param childIds    ids of the active direct children
 * @param active      false when the unit is archived (a disbanded working group is archived)
 */
public record OrgUnitInfo(UUID id, String name, OrgUnitType type, UUID parentId, List<UUID> ancestorIds,
		List<UUID> childIds, boolean active) {
}
