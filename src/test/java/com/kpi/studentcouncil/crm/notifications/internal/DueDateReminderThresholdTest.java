package com.kpi.studentcouncil.crm.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

/** Threshold rules of the due-date reminders: Europe/Kyiv day boundaries, lead time, DST. */
class DueDateReminderThresholdTest {

	private static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");

	private final DueDateReminderService service = new DueDateReminderService(null, null, null, null,
			new DueDateReminderProperties(true, Duration.ofHours(24), "0 0 * * * *"), Clock.systemUTC());

	private static Instant kyiv(int y, int m, int d, int h, int min) {
		return ZonedDateTime.of(y, m, d, h, min, 0, 0, KYIV).toInstant();
	}

	@Test
	void nothingBeforeTheLeadWindow() {
		// due 10 June: deadline is 11 June 00:00 Kyiv, window opens 10 June 00:00
		assertThat(service.threshold(LocalDate.of(2026, 6, 10), kyiv(2026, 6, 9, 23, 59))).isNull();
	}

	@Test
	void dueSoonFromWindowStartUntilEndOfDueDay() {
		LocalDate due = LocalDate.of(2026, 6, 10);
		assertThat(service.threshold(due, kyiv(2026, 6, 10, 0, 0))).isEqualTo(DueDateReminderService.DUE_SOON);
		assertThat(service.threshold(due, kyiv(2026, 6, 10, 23, 59))).isEqualTo(DueDateReminderService.DUE_SOON);
	}

	@Test
	void overdueOnlyAfterTheDueDayEnded() {
		LocalDate due = LocalDate.of(2026, 6, 10);
		assertThat(service.threshold(due, kyiv(2026, 6, 11, 0, 0))).isEqualTo(DueDateReminderService.OVERDUE);
		assertThat(service.threshold(due, kyiv(2026, 7, 1, 12, 0))).isEqualTo(DueDateReminderService.OVERDUE);
	}

	@Test
	void dayBoundaryFollowsKyivNotUtc() {
		// 21:30 UTC on 10 June is already 00:30 on 11 June in Kyiv (UTC+3 in summer): overdue for due date 10 June
		Instant utcEvening = Instant.parse("2026-06-10T21:30:00Z");
		assertThat(service.threshold(LocalDate.of(2026, 6, 10), utcEvening)).isEqualTo(DueDateReminderService.OVERDUE);
	}

	@Test
	void dstChangeDayIsHandled() {
		// 29 March 2026 is a 23-hour day in Kyiv; the deadline is still the next local midnight
		LocalDate due = LocalDate.of(2026, 3, 29);
		Instant lastMoment = kyiv(2026, 3, 30, 0, 0).minusSeconds(1);
		assertThat(service.threshold(due, lastMoment)).isEqualTo(DueDateReminderService.DUE_SOON);
		assertThat(service.threshold(due, kyiv(2026, 3, 30, 0, 0))).isEqualTo(DueDateReminderService.OVERDUE);
	}

}
