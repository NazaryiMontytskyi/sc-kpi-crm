package com.kpi.studentcouncil.crm.identity.internal;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Identity settings ({@code crm.identity.*}); every value is overridable in {@code application.yml}/environment.
 *
 * @param publicApiDocs whether {@code /v3/api-docs} and Swagger UI are reachable without signing in (dev/test only)
 */
@ConfigurationProperties("crm.identity")
public record IdentityProperties(@DefaultValue Throttle throttle, @DefaultValue Password password,
		@DefaultValue("false") boolean publicApiDocs) {

	/**
	 * Brute-force protection per login+IP.
	 *
	 * @param maxAttempts       failed attempts within {@code window} that trigger a lockout
	 * @param window            period in which failures are counted
	 * @param lockout           first lockout duration
	 * @param backoffMultiplier each repeated lockout lasts this many times longer than the previous one
	 * @param maxLockout        upper bound of a lockout
	 * @param resetAfter        quiet period after which the backoff level is forgotten
	 * @param maxTrackedKeys    soft cap of tracked login+IP pairs (expired entries are purged above it)
	 */
	public record Throttle(@DefaultValue("5") int maxAttempts, @DefaultValue("15m") Duration window,
			@DefaultValue("15m") Duration lockout, @DefaultValue("2") double backoffMultiplier,
			@DefaultValue("24h") Duration maxLockout, @DefaultValue("24h") Duration resetAfter,
			@DefaultValue("10000") int maxTrackedKeys) {
	}

	/**
	 * Password policy and hashing.
	 *
	 * @param minLength      minimum number of characters
	 * @param bcryptStrength BCrypt cost factor
	 */
	public record Password(@DefaultValue("10") int minLength, @DefaultValue("12") int bcryptStrength) {
	}

}
