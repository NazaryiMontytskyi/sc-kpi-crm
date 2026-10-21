package com.kpi.studentcouncil.crm.notifications.internal;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.kpi.studentcouncil.crm.notifications.NotificationChannel;
import com.kpi.studentcouncil.crm.notifications.NotificationMessage;
import com.kpi.studentcouncil.crm.notifications.NotificationTarget;
import com.kpi.studentcouncil.crm.notifications.NotificationType;

/**
 * Fans a notification out to the recipients over every registered {@link NotificationChannel}. The actor who caused
 * the event is never notified about their own action; recipients are de-duplicated.
 */
@Component
class NotificationDispatcher {

	private final List<NotificationChannel> channels;

	NotificationDispatcher(List<NotificationChannel> channels) {
		this.channels = channels;
	}

	void dispatch(Set<UUID> recipients, UUID actorId, NotificationType type, NotificationTarget target,
			Map<String, Object> payload) {
		Set<UUID> unique = new LinkedHashSet<>();
		for (UUID recipient : recipients) {
			if (recipient != null && !Objects.equals(recipient, actorId)) {
				unique.add(recipient);
			}
		}
		for (UUID recipient : unique) {
			NotificationMessage message = new NotificationMessage(recipient, type, target, payload);
			channels.forEach(channel -> channel.deliver(message));
		}
	}

}
