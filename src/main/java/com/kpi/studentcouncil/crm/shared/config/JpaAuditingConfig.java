package com.kpi.studentcouncil.crm.shared.config;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** JPA auditing wiring: UTC timestamps from the injectable {@link Clock}; auditor from the {@code auditorAware} bean. */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

	/** Nil UUID used as the actor for system operations. */
	public static final UUID SYSTEM_ACTOR = new UUID(0L, 0L);

	@Bean
	@ConditionalOnMissingBean(Clock.class)
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	DateTimeProvider auditingDateTimeProvider(Clock clock) {
		return () -> Optional.of(clock.instant());
	}

	// The "auditorAware" bean (authenticated user, or SYSTEM_ACTOR when nobody is signed in) is provided by the
	// identity module (INC-004), which replaced the former system-actor placeholder defined here.

}
