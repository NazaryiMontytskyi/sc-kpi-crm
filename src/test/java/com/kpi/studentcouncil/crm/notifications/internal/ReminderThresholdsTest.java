package com.kpi.studentcouncil.crm.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.notifications.internal.ReminderThresholds.Threshold;

/**
 * Pure rules of the deadline reminders with a fixed {@link Clock}: offsets 3d and 1d, most urgent only, overdue only
 * when past, Europe/Kyiv day boundaries and DST, legacy keys, recipients (NT-1..NT-4), and offset binding (NT-1).
 */
class ReminderThresholdsTest {

	private static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");
	private static final List<Duration> OFFSETS = List.of(Duration.ofDays(3), Duration.ofDays(1));
	private static final LocalDate DEADLINE = LocalDate.of(2030, 6, 10); // due at 11 June 00:00 Kyiv

	private static Instant kyiv(int month, int day, int hour, int minute) {
		return ZonedDateTime.of(2030, month, day, hour, minute, 0, 0, KYIV).toInstant();
	}

	private static String select(LocalDate deadline, Instant now) {
		Clock fixed = Clock.fixed(now, ZoneOffset.UTC);
		return ReminderThresholds.select(deadline, fixed.instant(), OFFSETS).map(Threshold::key).orElse(null);
	}

	@Test
	void nothingBeforeTheEarliestWindow() {
		assertThat(select(DEADLINE, kyiv(6, 7, 23, 59))).isNull();
	}

	@Test
	void threeDayWindowThenOneDayWindowThenOverdue() {
		assertThat(select(DEADLINE, kyiv(6, 8, 0, 0))).isEqualTo("DUE_SOON_PT72H");
		assertThat(select(DEADLINE, kyiv(6, 9, 23, 59))).isEqualTo("DUE_SOON_PT72H");
		assertThat(select(DEADLINE, kyiv(6, 10, 0, 0))).isEqualTo("DUE_SOON_PT24H");
		assertThat(select(DEADLINE, kyiv(6, 10, 23, 59))).isEqualTo("DUE_SOON_PT24H");
		assertThat(select(DEADLINE, kyiv(6, 11, 0, 0))).isEqualTo(ReminderThresholds.OVERDUE);
		assertThat(select(DEADLINE, kyiv(7, 1, 12, 0))).isEqualTo(ReminderThresholds.OVERDUE);
	}

	@Test
	void firstSeenInsideTheOneDayWindowYieldsOnlyTheMostUrgentOffset() {
		// both windows are open; only the 1-day one is selected, so the 3-day one is never sent afterwards
		Threshold t = ReminderThresholds.select(DEADLINE, kyiv(6, 10, 12, 0), OFFSETS).orElseThrow();
		assertThat(t.offset()).isEqualTo(Duration.ofDays(1));
		assertThat(t.isOverdue()).isFalse();
	}

	@Test
	void offsetOrderDoesNotMatter() {
		Instant now = kyiv(6, 10, 12, 0);
		assertThat(ReminderThresholds.select(DEADLINE, now, List.of(Duration.ofDays(1), Duration.ofDays(3))))
				.contains(Threshold.dueSoon(Duration.ofDays(1)));
	}

	@Test
	void withoutOffsetsOnlyOverdueIsSent() {
		assertThat(ReminderThresholds.select(DEADLINE, kyiv(6, 10, 23, 0), List.of())).isEmpty();
		assertThat(ReminderThresholds.select(DEADLINE, kyiv(6, 11, 0, 0), List.of())).contains(Threshold.overdue());
	}

	@Test
	void dayBoundaryFollowsKyivNotUtc() {
		// 21:30 UTC on 10 June is 00:30 on 11 June in Kyiv (UTC+3 in summer): overdue
		assertThat(select(DEADLINE, Instant.parse("2030-06-10T21:30:00Z"))).isEqualTo(ReminderThresholds.OVERDUE);
		assertThat(select(DEADLINE, Instant.parse("2030-06-10T20:59:59Z"))).isEqualTo("DUE_SOON_PT24H");
	}

	@Test
	void dstChangeDayIsHandled() {
		// 31 March 2030 is a 23-hour day in Kyiv; the deadline is still the next local midnight
		LocalDate due = LocalDate.of(2030, 3, 31);
		Instant end = ZonedDateTime.of(2030, 4, 1, 0, 0, 0, 0, KYIV).toInstant();
		assertThat(ReminderThresholds.dueAt(due)).isEqualTo(end);
		assertThat(select(due, end.minusSeconds(1))).isEqualTo("DUE_SOON_PT24H");
		assertThat(select(due, end)).isEqualTo(ReminderThresholds.OVERDUE);
	}

	@Test
	void scanBoundCoversTheLargestOffset() {
		Instant now = kyiv(6, 7, 12, 0);
		assertThat(ReminderThresholds.latestDeadlineToScan(now, OFFSETS)).isEqualTo(LocalDate.of(2030, 6, 10));
		assertThat(ReminderThresholds.latestDeadlineToScan(now, List.of())).isEqualTo(LocalDate.of(2030, 6, 7));
	}

	@Test
	void legacyDueSoonCoversOffsetsOfAtLeastOneDay() {
		assertThat(Threshold.dueSoon(Duration.ofDays(3)).coveredByLegacyDueSoon()).isTrue();
		assertThat(Threshold.dueSoon(Duration.ofDays(1)).coveredByLegacyDueSoon()).isTrue();
		assertThat(Threshold.dueSoon(Duration.ofHours(2)).coveredByLegacyDueSoon()).isFalse();
		// legacy OVERDUE uses the same key as the new one
		assertThat(Threshold.overdue().key()).isEqualTo("OVERDUE");
		assertThat(Threshold.overdue().coveredByLegacyDueSoon()).isFalse();
	}

	@Test
	void dueSoonGoesToAssigneesOnly() {
		UUID creator = UUID.randomUUID();
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		assertThat(ReminderThresholds.recipients(creator, Set.of(a, b), false)).containsExactlyInAnyOrder(a, b);
	}

	@Test
	void overdueAlsoGoesToTheCreatorWhoIsNotAnAssignee() {
		UUID creator = UUID.randomUUID();
		UUID a = UUID.randomUUID();
		assertThat(ReminderThresholds.recipients(creator, Set.of(a), true)).containsExactlyInAnyOrder(a, creator);
		// creator who is also an assignee: once
		assertThat(ReminderThresholds.recipients(a, Set.of(a), true)).containsExactly(a);
	}

	@Test
	void taskWithoutAssigneesNotifiesItsCreator() {
		UUID creator = UUID.randomUUID();
		assertThat(ReminderThresholds.recipients(creator, Set.of(), false)).containsExactly(creator);
		assertThat(ReminderThresholds.recipients(creator, Set.of(), true)).containsExactly(creator);
	}

	@Test
	void systemActorIsNeverARecipient() {
		assertThat(ReminderThresholds.recipients(ActorProvider.SYSTEM_ACTOR, Set.of(), true)).isEmpty();
		assertThat(ReminderThresholds.recipients(null, Set.of(), false)).isEmpty();
	}

	@Test
	void offsetsBindFromPropertiesWithDefaultsThreeDaysAndOneDay() {
		DueDateReminderProperties defaults = new Binder(new MapConfigurationPropertySource(Map.of()))
				.bindOrCreate("crm.notifications.due-reminders", DueDateReminderProperties.class);
		assertThat(defaults.offsets()).containsExactly(Duration.ofDays(3), Duration.ofDays(1));

		DueDateReminderProperties custom = new Binder(new MapConfigurationPropertySource(
				Map.of("crm.notifications.due-reminders.offsets", "6h,2d,0s,2d")))
				.bindOrCreate("crm.notifications.due-reminders", DueDateReminderProperties.class);
		assertThat(new ConfiguredReminderOffsetsProvider(custom).offsets()).containsExactly(Duration.ofDays(2),
				Duration.ofHours(6));
	}

}
