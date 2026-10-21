package com.kpi.studentcouncil.crm.notifications.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Wiring of the notifications module: reminder properties and scheduling support. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(DueDateReminderProperties.class)
class NotificationsConfig {
}
