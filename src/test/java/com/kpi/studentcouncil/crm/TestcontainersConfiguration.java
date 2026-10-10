package com.kpi.studentcouncil.crm;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Provides one PostgreSQL container per test JVM, shared by every Spring test context. The container is started
 * once in the static initializer and removed by Testcontainers' Ryuk when the JVM exits (reuse mode is off).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:16"))
		.withCommand("postgres", "-c", "max_connections=300");

	static {
		POSTGRES.start();
	}

	// destroyMethod "": the container is shared, so a closing context must not stop it for the others
	@Bean(destroyMethod = "")
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return POSTGRES;
	}

}
