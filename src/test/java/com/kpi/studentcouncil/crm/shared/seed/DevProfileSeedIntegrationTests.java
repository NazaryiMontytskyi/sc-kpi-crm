package com.kpi.studentcouncil.crm.shared.seed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.kpi.studentcouncil.crm.TestcontainersConfiguration;

/** Under the dev profile the dev-only seeder runs. */
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class DevProfileSeedIntegrationTests {

	@Autowired
	JdbcTemplate jdbc;

	@AfterEach
	void removeDevMarker() {
		// The database is shared by all test contexts: remove the dev-only marker so contexts without the dev
		// profile (which assert it is absent) are not affected by this one, even if the assertion fails.
		jdbc.update("delete from seed_history where seed_id = 'shared.dev-demo'");
	}

	@Test
	void devDemoSeederIsApplied() {
		Long count = jdbc.queryForObject("select count(*) from seed_history where seed_id = 'shared.dev-demo'", Long.class);
		assertThat(count).isEqualTo(1);
	}

}
