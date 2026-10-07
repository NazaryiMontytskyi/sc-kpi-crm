package com.kpi.studentcouncil.crm.identity;

import java.util.UUID;

/** Public lookup API of the identity module for other modules. */
public interface UserDirectory {

	/** True when an account with this id exists (any status, including archived). */
	boolean exists(UUID userId);

}
