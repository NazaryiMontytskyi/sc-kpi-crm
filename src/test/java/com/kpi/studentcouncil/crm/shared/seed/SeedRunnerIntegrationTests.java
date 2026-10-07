package com.kpi.studentcouncil.crm.shared.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.kpi.studentcouncil.crm.TestcontainersConfiguration;

/** Mechanism tests against a real database: idempotency, ordering, scope, rollback, disabling. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SeedRunnerIntegrationTests {

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	TransactionTemplate tx;

	@Autowired
	Clock clock;

	@BeforeEach
	void setUp() {
		jdbc.execute("create table if not exists seed_test_item (name varchar(100))");
		jdbc.update("delete from seed_test_item");
		jdbc.update("delete from seed_history where seed_id like 'test.%'");
	}

	private SeedRunner runner(boolean enabled, String profile, Seeder... seeders) {
		MockEnvironment env = new MockEnvironment();
		if (profile != null) {
			env.setActiveProfiles(profile);
		}
		return new SeedRunner(List.of(seeders), new SeedProperties(enabled), env, jdbc, tx, clock);
	}

	private Seeder seeder(String id, SeedScope scope, int order, List<String> log, Runnable action) {
		return new Seeder() {
			public String id() { return id; }
			public SeedScope scope() { return scope; }
			public int order() { return order; }
			public void run() {
				log.add(id);
				action.run();
			}
		};
	}

	private long items() {
		return jdbc.queryForObject("select count(*) from seed_test_item", Long.class);
	}

	private long history(String id) {
		return jdbc.queryForObject("select count(*) from seed_history where seed_id = ?", Long.class, id);
	}

	@Test
	void secondRunDoesNotDuplicateRows() throws Exception {
		List<String> log = new ArrayList<>();
		Seeder s = seeder("test.once", SeedScope.REFERENCE, 0, log,
				() -> jdbc.update("insert into seed_test_item values ('Test Item 01')"));

		runner(true, "test", s).run(null);
		runner(true, "test", s).run(null);

		assertThat(items()).isEqualTo(1);
		assertThat(history("test.once")).isEqualTo(1);
		assertThat(log).containsExactly("test.once");
	}

	@Test
	void runsInOrderThenById() throws Exception {
		List<String> log = new ArrayList<>();
		runner(true, "test",
				seeder("test.c", SeedScope.REFERENCE, 5, log, () -> { }),
				seeder("test.b", SeedScope.REFERENCE, 1, log, () -> { }),
				seeder("test.a", SeedScope.REFERENCE, 1, log, () -> { })).run(null);

		assertThat(log).containsExactly("test.a", "test.b", "test.c");
	}

	@Test
	void devOnlySeederRunsOnlyUnderDevProfile() throws Exception {
		List<String> log = new ArrayList<>();
		Seeder ref = seeder("test.ref", SeedScope.REFERENCE, 0, log, () -> { });
		Seeder dev = seeder("test.dev", SeedScope.DEV_ONLY, 0, log, () -> { });

		runner(true, "prod", ref, dev).run(null);
		assertThat(log).containsExactly("test.ref");
		assertThat(history("test.dev")).isZero();

		runner(true, "dev", ref, dev).run(null);
		assertThat(log).containsExactly("test.ref", "test.dev");
	}

	@Test
	void failureRollsBackSeederAndHistoryAndPropagates() {
		List<String> log = new ArrayList<>();
		Seeder bad = seeder("test.bad", SeedScope.REFERENCE, 0, log, () -> {
			jdbc.update("insert into seed_test_item values ('Test Item 02')");
			throw new IllegalStateException("boom");
		});

		assertThatThrownBy(() -> runner(true, "test", bad).run(null)).hasMessage("boom");
		assertThat(items()).isZero();
		assertThat(history("test.bad")).isZero();
	}

	@Test
	void disabledRunsNothing() throws Exception {
		List<String> log = new ArrayList<>();
		runner(false, "dev", seeder("test.off", SeedScope.REFERENCE, 0, log, () -> { })).run(null);

		assertThat(log).isEmpty();
		assertThat(history("test.off")).isZero();
	}

	@Test
	void duplicateIdsFailLoudly() {
		List<String> log = new ArrayList<>();
		Seeder a = seeder("test.dup", SeedScope.REFERENCE, 0, log, () -> { });
		Seeder b = seeder("test.dup", SeedScope.REFERENCE, 0, log, () -> { });

		assertThatThrownBy(() -> runner(true, "test", a, b).run(null)).hasMessageContaining("Duplicate seeder id");
		assertThat(log).isEmpty();
	}

}
