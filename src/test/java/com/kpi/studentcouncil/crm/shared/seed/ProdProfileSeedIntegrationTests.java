package com.kpi.studentcouncil.crm.shared.seed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.kpi.studentcouncil.crm.TestcontainersConfiguration;

/** The prod profile never loads dev data. */
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@ActiveProfiles("prod")
@Import(TestcontainersConfiguration.class)
class ProdProfileSeedIntegrationTests {

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void devDemoSeederIsNeverApplied() {
		Long count = jdbc.queryForObject("select count(*) from seed_history where seed_id = 'shared.dev-demo'", Long.class);
		assertThat(count).isEqualTo(0);
	}

}
