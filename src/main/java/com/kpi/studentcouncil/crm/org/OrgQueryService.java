package com.kpi.studentcouncil.crm.org;

import java.util.List;
import java.util.UUID;

/** Public membership queries of the org module for other modules (task spaces, search, notifications, ...). */
public interface OrgQueryService {

	/** Ids of the units in which the user currently has an active membership (several are possible). */
	List<UUID> unitIdsOfUser(UUID userId);

	/** User ids (equal to member ids) of the people with an active membership in the unit. */
	List<UUID> memberIdsOfUnit(UUID orgUnitId);

	/** True when the user has an active membership in the unit. */
	boolean isMember(UUID userId, UUID orgUnitId);

}
