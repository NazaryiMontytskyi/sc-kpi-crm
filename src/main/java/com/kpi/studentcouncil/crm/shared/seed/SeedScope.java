package com.kpi.studentcouncil.crm.shared.seed;

/** Where a {@link Seeder} may run. */
public enum SeedScope {

	/** Reference data required by the application (roles, faculties...); runs in every environment. */
	REFERENCE,

	/** Fake sample data for local development; runs only under the {@code dev} profile. */
	DEV_ONLY

}
