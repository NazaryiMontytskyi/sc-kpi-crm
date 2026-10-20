package com.kpi.studentcouncil.crm.identity;

import java.util.UUID;

/** Public lookup API of the identity module for other modules. */
public interface UserDirectory {

	/** True when an account with this id exists (any status, including archived). */
	boolean exists(UUID userId);

	/** True when the account exists and is ACTIVE (neither archived nor blocked); such a user can be mentioned.
	 * The default (for lambdas in tests) falls back to {@link #exists}; the identity implementation checks the status. */
	default boolean isActive(UUID userId) {
		return exists(userId);
	}

}
