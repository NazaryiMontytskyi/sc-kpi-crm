package com.kpi.studentcouncil.crm.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class LoginThrottleTest {

	private final MutableClock clock = new MutableClock();

	private final IdentityProperties.Throttle config = new IdentityProperties.Throttle(5, Duration.ofMinutes(15),
			Duration.ofMinutes(15), 2, Duration.ofHours(24), Duration.ofHours(24), 10_000);

	private final LoginThrottle throttle = new LoginThrottle(config, clock);

	private void fail(String key, int times) {
		for (int i = 0; i < times; i++) {
			throttle.checkAllowed(key);
			throttle.recordFailure(key);
		}
	}

	@Test
	void belowThresholdSignInStaysAllowed() {
		fail("a|1.1.1.1", 4);

		assertThatCode(() -> throttle.checkAllowed("a|1.1.1.1")).doesNotThrowAnyException();
	}

	@Test
	void fifthFailureLocksForFifteenMinutes() {
		fail("a|1.1.1.1", 5);

		assertThatThrownBy(() -> throttle.checkAllowed("a|1.1.1.1")).isInstanceOfSatisfying(LoginLockedException.class,
				ex -> assertThat(ex.getRetryAfterSeconds()).isBetween(14L * 60, 15L * 60 + 1));

		clock.advance(Duration.ofMinutes(15).plusSeconds(1));
		assertThatCode(() -> throttle.checkAllowed("a|1.1.1.1")).doesNotThrowAnyException();
	}

	@Test
	void keysAreIndependentPerLoginAndIp() {
		fail("a|1.1.1.1", 5);

		assertThatCode(() -> throttle.checkAllowed("a|2.2.2.2")).doesNotThrowAnyException();
		assertThatCode(() -> throttle.checkAllowed("b|1.1.1.1")).doesNotThrowAnyException();
	}

	@Test
	void repeatedLockoutsBackOffExponentially() {
		fail("a|1.1.1.1", 5);
		clock.advance(Duration.ofMinutes(16));
		fail("a|1.1.1.1", 5); // second lockout: 30 minutes

		clock.advance(Duration.ofMinutes(20));
		assertThatThrownBy(() -> throttle.checkAllowed("a|1.1.1.1")).isInstanceOf(LoginLockedException.class);
		clock.advance(Duration.ofMinutes(11));
		assertThatCode(() -> throttle.checkAllowed("a|1.1.1.1")).doesNotThrowAnyException();
	}

	@Test
	void lockoutIsCappedByMaxLockout() {
		LoginThrottle capped = new LoginThrottle(new IdentityProperties.Throttle(1, Duration.ofMinutes(15),
				Duration.ofMinutes(15), 10, Duration.ofMinutes(60), Duration.ofHours(24), 10_000), clock);
		for (int i = 0; i < 4; i++) {
			capped.recordFailure("k");
			clock.advance(Duration.ofMinutes(61));
		}
		capped.recordFailure("k");

		clock.advance(Duration.ofMinutes(59));
		assertThatThrownBy(() -> capped.checkAllowed("k")).isInstanceOf(LoginLockedException.class);
		clock.advance(Duration.ofMinutes(2));
		assertThatCode(() -> capped.checkAllowed("k")).doesNotThrowAnyException();
	}

	@Test
	void failuresOutsideTheWindowAreForgotten() {
		fail("a|1.1.1.1", 4);
		clock.advance(Duration.ofMinutes(16));
		fail("a|1.1.1.1", 4);

		assertThatCode(() -> throttle.checkAllowed("a|1.1.1.1")).doesNotThrowAnyException();
	}

	@Test
	void successfulSignInResetsTheCounter() {
		fail("a|1.1.1.1", 4);
		throttle.reset("a|1.1.1.1");
		fail("a|1.1.1.1", 4);

		assertThatCode(() -> throttle.checkAllowed("a|1.1.1.1")).doesNotThrowAnyException();
	}

	@Test
	void backoffLevelIsForgottenAfterQuietPeriod() {
		fail("a|1.1.1.1", 5);
		clock.advance(Duration.ofHours(25));
		fail("a|1.1.1.1", 5); // level 0 again: 15 minutes, not 30

		clock.advance(Duration.ofMinutes(16));
		assertThatCode(() -> throttle.checkAllowed("a|1.1.1.1")).doesNotThrowAnyException();
	}

	@Test
	void expiredEntriesArePurgedWhenTheMapGrowsTooLarge() {
		LoginThrottle small = new LoginThrottle(new IdentityProperties.Throttle(5, Duration.ofMinutes(15),
				Duration.ofMinutes(15), 2, Duration.ofHours(24), Duration.ofHours(1), 3), clock);
		for (int i = 0; i < 4; i++) {
			small.recordFailure("k" + i);
		}
		clock.advance(Duration.ofHours(2));
		small.recordFailure("new");

		assertThat(small.trackedKeys()).isEqualTo(1);
	}

	static final class MutableClock extends Clock {

		private Instant now = Instant.parse("2026-01-01T00:00:00Z");

		void advance(Duration d) {
			now = now.plus(d);
		}

		@Override
		public java.time.ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}

	}

}
