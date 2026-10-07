package com.kpi.studentcouncil.crm.shared.seed;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs all registered {@link Seeder} beans once per environment, after Flyway has migrated the schema.
 * {@link SeedScope#DEV_ONLY} seeders run only when the {@code dev} profile is active.
 */
public class SeedRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

	private static final Profiles DEV = Profiles.of("dev");

	private final List<Seeder> seeders;
	private final SeedProperties properties;
	private final Environment environment;
	private final JdbcTemplate jdbc;
	private final TransactionTemplate tx;
	private final Clock clock;

	public SeedRunner(List<Seeder> seeders, SeedProperties properties, Environment environment, JdbcTemplate jdbc,
			TransactionTemplate tx, Clock clock) {
		this.seeders = seeders;
		this.properties = properties;
		this.environment = environment;
		this.jdbc = jdbc;
		this.tx = tx;
		this.clock = clock;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!properties.enabled()) {
			log.info("Seeding disabled (crm.seed.enabled=false)");
			return;
		}
		validate();
		boolean dev = environment.acceptsProfiles(DEV);
		seeders.stream()
				.filter(s -> s.scope() == SeedScope.REFERENCE || dev)
				.sorted(Comparator.comparingInt(Seeder::order).thenComparing(Seeder::id))
				.forEach(this::apply);
	}

	private void validate() {
		Set<String> ids = new HashSet<>();
		for (Seeder s : seeders) {
			if (s.id() == null || s.id().isBlank() || s.id().length() > 150) {
				throw new IllegalStateException("Seeder " + s.getClass().getName() + " has an invalid id");
			}
			if (s.scope() == null) {
				throw new IllegalStateException("Seeder " + s.id() + " has no scope");
			}
			if (!ids.add(s.id())) {
				throw new IllegalStateException("Duplicate seeder id: " + s.id());
			}
		}
	}

	private void apply(Seeder seeder) {
		// The history row is inserted first: a concurrent instance blocks on it until we commit, then skips.
		// Any failure rolls back both the history row and the seeded data, and propagates to abort startup.
		Boolean applied = tx.execute(status -> {
			int inserted = jdbc.update(
					"insert into seed_history (seed_id, applied_at) values (?, ?) on conflict (seed_id) do nothing",
					seeder.id(), Timestamp.from(clock.instant()));
			if (inserted == 0) {
				return false;
			}
			seeder.run();
			return true;
		});
		if (Boolean.TRUE.equals(applied)) {
			log.info("Seeder applied: {}", seeder.id());
		}
		else {
			log.debug("Seeder already applied, skipped: {}", seeder.id());
		}
	}

}
