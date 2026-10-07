package com.kpi.studentcouncil.crm.identity.internal;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Identity beans: password hashing and policy, brute-force throttle, pluggable authentication, JPA auditor. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ IdentityProperties.class, BootstrapAdminProperties.class })
class IdentityConfig {

	@Bean
	PasswordEncoder passwordEncoder(IdentityProperties properties) {
		return new BCryptPasswordEncoder(properties.password().bcryptStrength());
	}

	@Bean
	PasswordPolicy passwordPolicy(IdentityProperties properties) {
		return new PasswordPolicy(properties.password().minLength());
	}

	@Bean
	LoginThrottle loginThrottle(IdentityProperties properties, Clock clock) {
		return new LoginThrottle(properties.throttle(), clock);
	}

	/**
	 * Login + password provider. Account status is checked <em>after</em> the password, so a blocked account answers
	 * exactly like a wrong password (no information leak, same timing).
	 */
	@Bean
	DaoAuthenticationProvider passwordAuthenticationProvider(CrmUserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		provider.setPostAuthenticationChecks(user -> {
			if (user instanceof CrmUserDetails crm && !crm.canSignIn()) {
				throw new DisabledException("account cannot sign in");
			}
		});
		return provider;
	}

	/**
	 * Extension point: every {@link AuthenticationProvider} bean takes part in authentication, so an OIDC provider
	 * (KPI ID, CONTEXT.md section 7, [LATER]) can be added as one more bean without touching this module.
	 */
	@Bean
	AuthenticationManager authenticationManager(List<AuthenticationProvider> providers) {
		return new ProviderManager(providers);
	}

	/** Created/updated-by of JPA auditing: the signed-in user, or the system actor outside a user session. */
	@Bean
	AuditorAware<UUID> auditorAware(SecurityContextActor actor) {
		return () -> Optional.of(actor.currentActorId());
	}

}
