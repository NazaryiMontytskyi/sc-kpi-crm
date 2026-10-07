package com.kpi.studentcouncil.crm.audit.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.kpi.studentcouncil.crm.audit.ActorProvider;

@Configuration(proxyBeanMethods = false)
class AuditConfig {

	/** Fallback until INC-004 provides the authenticated-user actor. */
	@Bean
	@ConditionalOnMissingBean(ActorProvider.class)
	ActorProvider systemActorProvider() {
		return () -> ActorProvider.SYSTEM_ACTOR;
	}

}
