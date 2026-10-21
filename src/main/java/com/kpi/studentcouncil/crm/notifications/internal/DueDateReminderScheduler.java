package com.kpi.studentcouncil.crm.notifications.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Triggers {@link DueDateReminderService} on the configured cron (Europe/Kyiv). Disabled via configuration. */
@Component
@ConditionalOnProperty(prefix = "crm.notifications.due-reminders", name = "enabled", havingValue = "true",
		matchIfMissing = true)
class DueDateReminderScheduler {

	private final DueDateReminderService service;

	DueDateReminderScheduler(DueDateReminderService service) {
		this.service = service;
	}

	@Scheduled(cron = "${crm.notifications.due-reminders.cron:0 0 * * * *}", zone = "Europe/Kyiv")
	void run() {
		service.run();
	}

}
