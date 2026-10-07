package com.kpi.studentcouncil.crm.shared.config;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** JPA auditing wiring: UTC timestamps from the injectable {@link Clock}; auditor from {@link AuditorAware}. */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

	/** Nil UUID used as the actor for system operations until real authentication exists (INC-004). */
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

	/** Placeholder: always the system actor. INC-004 replaces it with the authenticated user. */
	@Bean
	@ConditionalOnMissingBean(name = "auditorAware")
	AuditorAware<UUID> auditorAware() {
		return () -> Optional.of(SYSTEM_ACTOR);
	}

}
