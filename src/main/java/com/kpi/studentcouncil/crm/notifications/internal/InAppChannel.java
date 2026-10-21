package com.kpi.studentcouncil.crm.notifications.internal;

import org.springframework.stereotype.Component;

import com.kpi.studentcouncil.crm.notifications.NotificationChannel;
import com.kpi.studentcouncil.crm.notifications.NotificationMessage;

/** Delivers notifications into the user's in-app inbox (the {@code notification} table). */
@Component
class InAppChannel implements NotificationChannel {

	private final NotificationRepository repository;

	InAppChannel(NotificationRepository repository) {
		this.repository = repository;
	}

	@Override
	public void deliver(NotificationMessage message) {
		repository.save(new Notification(message.recipientId(), message.type(), message.target().type(),
				message.target().id(), message.payload()));
	}

}
