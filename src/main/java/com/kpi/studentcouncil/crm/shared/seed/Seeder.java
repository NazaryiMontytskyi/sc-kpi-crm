package com.kpi.studentcouncil.crm.shared.seed;

/**
 * Hook point for modules to register seed data: implement this interface and expose the implementation as a Spring
 * bean. Each seeder runs at most once per environment (tracked in {@code seed_history} by {@link #id()}), inside one
 * transaction together with its history record; a failure aborts startup and rolls the seeder back.
 *
 * <p>Seeders should still be written defensively (check before insert) so they stay safe if the id is ever renamed.
 * Dev seeders must use obviously fake data only (names like "Test User 01", {@code example.invalid} addresses).
 */
public interface Seeder {

	/** Stable unique id, e.g. {@code access.reference-roles}. Never change it after release. */
	String id();

	/** Where this seeder may run. */
	SeedScope scope();

	/** Execution order among seeders to run; lower first, ties broken by id. */
	default int order() {
		return 0;
	}

	/** Performs the seeding; executed inside a transaction. */
	void run();

}
