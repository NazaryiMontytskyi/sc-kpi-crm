package com.kpi.studentcouncil.crm.notifications.internal;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Deadline reminder settings ({@code crm.notifications.due-reminders.*}).
 *
 * @param enabled whether the scheduled job runs (the service can always be invoked directly)
 * @param offsets how long before the deadline (end of the deadline day, Europe/Kyiv) a "due soon" reminder is sent;
 *                one reminder per offset, default 3 days and 1 day [ASSUMPTION, spec §8 Q1]. Read only through
 *                {@link ReminderOffsetsProvider}; non-positive values are ignored.
 * @param cron    schedule of the job, evaluated in Europe/Kyiv
 */
@ConfigurationProperties("crm.notifications.due-reminders")
public record DueDateReminderProperties(@DefaultValue("true") boolean enabled,
		@DefaultValue({ "3d", "1d" }) List<Duration> offsets, @DefaultValue("0 0 * * * *") String cron) {

	public DueDateReminderProperties {
		offsets = offsets == null ? List.of() : List.copyOf(offsets);
	}

}
