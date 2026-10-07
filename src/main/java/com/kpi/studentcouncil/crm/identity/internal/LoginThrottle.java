package com.kpi.studentcouncil.crm.identity.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory brute-force protection keyed by login+IP (see ADR-0002). After {@code maxAttempts} failures within
 * {@code window} the key is locked for {@code lockout}; every further lockout multiplies the duration
 * (exponential backoff, capped by {@code maxLockout}). The backoff level is forgotten after {@code resetAfter}
 * without activity, and on a successful sign-in. Failures are counted for unknown logins as well, so lockouts
 * cannot be used to probe which logins exist.
 */
public class LoginThrottle {

	private final IdentityProperties.Throttle config;

	private final Clock clock;

	private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

	public LoginThrottle(IdentityProperties.Throttle config, Clock clock) {
		this.config = config;
		this.clock = clock;
	}

	/** Throws {@link LoginLockedException} if the key is currently locked. */
	public void checkAllowed(String key) {
		Entry entry = entries.get(key);
		if (entry == null) {
			return;
		}
		Instant now = clock.instant();
		Instant lockedUntil = entry.lockedUntil;
		if (lockedUntil != null && now.isBefore(lockedUntil)) {
			long seconds = Duration.between(now, lockedUntil).toSeconds() + 1;
			throw new LoginLockedException(seconds);
		}
	}

	/** Registers a failed attempt; may start a lockout. */
	public void recordFailure(String key) {
		Instant now = clock.instant();
		purgeIfNeeded(now);
		entries.compute(key, (k, existing) -> {
			Entry e = existing == null ? new Entry(now) : existing;
			if (e.lockedUntil != null && now.isBefore(e.lockedUntil)) {
				return e; // already locked: attempts during a lockout are refused earlier and not counted
			}
			if (Duration.between(e.lastActivity, now).compareTo(config.resetAfter()) > 0) {
				e.level = 0;
			}
			if (Duration.between(e.windowStart, now).compareTo(config.window()) > 0) {
				e.failures = 0;
				e.windowStart = now;
			}
			e.lastActivity = now;
			e.failures++;
			if (e.failures >= config.maxAttempts()) {
				e.lockedUntil = now.plus(lockoutFor(e.level));
				e.level++;
				e.failures = 0;
				e.windowStart = now;
			}
			return e;
		});
	}

	/** Forgets the key (successful sign-in). */
	public void reset(String key) {
		entries.remove(key);
	}

	int trackedKeys() {
		return entries.size();
	}

	private Duration lockoutFor(int level) {
		double millis = config.lockout().toMillis() * Math.pow(config.backoffMultiplier(), level);
		long capped = (long) Math.min(millis, config.maxLockout().toMillis());
		return Duration.ofMillis(capped);
	}

	private void purgeIfNeeded(Instant now) {
		if (entries.size() <= config.maxTrackedKeys()) {
			return;
		}
		entries.entrySet().removeIf(e -> {
			Entry v = e.getValue();
			boolean lockExpired = v.lockedUntil == null || !now.isBefore(v.lockedUntil);
			return lockExpired && Duration.between(v.lastActivity, now).compareTo(config.resetAfter()) > 0;
		});
	}

	private static final class Entry {

		volatile Instant lockedUntil;

		Instant windowStart;

		Instant lastActivity;

		int failures;

		int level;

		Entry(Instant now) {
			this.windowStart = now;
			this.lastActivity = now;
		}

	}

}
