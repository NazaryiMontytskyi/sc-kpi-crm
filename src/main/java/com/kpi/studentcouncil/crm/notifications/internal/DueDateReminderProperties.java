package com.kpi.studentcouncil.crm.notifications.internal;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Due-date reminder settings ({@code crm.notifications.due-reminders.*}).
 *
 * @param enabled  whether the scheduled job runs (the service can always be invoked directly)
 * @param leadTime how long before the end of the due day (Europe/Kyiv) the "due soon" notification is sent
 * @param cron     schedule of the job, evaluated in Europe/Kyiv
 */
@ConfigurationProperties("crm.notifications.due-reminders")
public record DueDateReminderProperties(@DefaultValue("true") boolean enabled,
		@DefaultValue("24h") Duration leadTime, @DefaultValue("0 0 * * * *") String cron) {
}
