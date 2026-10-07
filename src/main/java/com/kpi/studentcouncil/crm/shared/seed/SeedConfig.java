package com.kpi.studentcouncil.crm.shared.seed;

import java.time.Clock;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Wires the {@link SeedRunner}. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SeedProperties.class)
public class SeedConfig {

	@Bean
	SeedRunner seedRunner(List<Seeder> seeders, SeedProperties properties, Environment environment,
			JdbcTemplate jdbc, TransactionTemplate tx, Clock clock) {
		return new SeedRunner(seeders, properties, environment, jdbc, tx, clock);
	}

}
