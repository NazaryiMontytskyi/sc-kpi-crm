package com.kpi.studentcouncil.crm.org;

import java.util.Optional;
import java.util.UUID;

/** Public read API of the org module for other modules (task spaces, positions, search, ...). */
public interface OrgService {

	/** The unit including archived ones (check {@link OrgUnitInfo#active()}), or empty if it does not exist. */
	Optional<OrgUnitInfo> find(UUID id);

	/**
	 * @throws com.kpi.studentcouncil.crm.shared.error.NotFoundException unknown unit
	 */
	OrgUnitInfo require(UUID id);

	/** True when the unit exists and is not archived. */
	boolean isActive(UUID id);

}
