package com.kpi.studentcouncil.crm.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.kpi.studentcouncil.crm.TestcontainersConfiguration;
import com.kpi.studentcouncil.crm.access.AccessService;
import com.kpi.studentcouncil.crm.access.Permission;

/**
 * The bootstrap needs an empty database, so this context lives in its own schema inside the shared container
 * (Flyway, Hibernate and the connection search_path all point at it); the public schema is never touched.
 */
@SpringBootTest(properties = { "crm.bootstrap.admin.login=First.Admin",
		"crm.bootstrap.admin.password=Bootstrap-pass-42", "crm.seed.enabled=false",
		"spring.flyway.default-schema=bootstrap_it", "spring.flyway.schemas=bootstrap_it",
		"spring.jpa.properties.hibernate.default_schema=bootstrap_it",
		"spring.datasource.hikari.connection-init-sql=SET search_path TO bootstrap_it" })
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FirstAdminBootstrapIntegrationTests {

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	UserAccountService accounts;

	@Autowired
	AccessService access;

	@Test
	void firstAdminIsCreatedFromEnvironmentOnEmptyDatabaseOnly() {
		assertThat(jdbc.queryForObject("select current_schema()", String.class)).isEqualTo("bootstrap_it");
		assertThat(jdbc.queryForObject("select count(*) from users", Long.class)).isEqualTo(1);
		UUID id = jdbc.queryForObject("select id from users where login = 'first.admin'", UUID.class);
		assertThat(jdbc.queryForObject("select must_change_password from users where id = ?", Boolean.class, id))
				.isTrue();
		assertThat(jdbc.queryForObject("select password_hash from users where id = ?", String.class, id))
				.startsWith("$2").doesNotContain("Bootstrap-pass-42");
		assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'CREATE' and entity_id = ?",
				Long.class, id)).isEqualTo(1);

		// The hardcoded Admin role is an assignment without a role row and grants every permission.
		assertThat(jdbc.queryForObject("select role_id from role_assignment where user_id = ?", UUID.class, id))
				.isEqualTo(AccessService.ADMIN_ROLE_ID);
		assertThat(jdbc.queryForObject("select count(*) from role where id = ?", Long.class,
				AccessService.ADMIN_ROLE_ID)).isZero();
		assertThat(access.effectivePermissions(id)).isEqualTo(Permission.allKeys());

		assertThat(accounts.createFirstAdminIfAbsent("another.admin", "Another-admin-pass-1")).isFalse();
		assertThat(jdbc.queryForObject("select count(*) from users", Long.class)).isEqualTo(1);
	}

}
