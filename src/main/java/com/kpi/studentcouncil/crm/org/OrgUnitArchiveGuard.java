package com.kpi.studentcouncil.crm.org;

import java.util.Optional;
import java.util.UUID;

/**
 * Extension point: other parts of the system (e.g. memberships, INC-018) can veto archiving of a unit by
 * registering a bean of this type. Active children are checked by the org module itself.
 */
public interface OrgUnitArchiveGuard {

	/**
	 * @return a short reason (shown in the problem detail) when the unit must not be archived now, otherwise empty
	 */
	Optional<String> vetoArchive(UUID orgUnitId);

}
