package com.kpi.studentcouncil.crm.notifications;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A notification for one recipient, independent of the delivery channel and of any locale. Texts are rendered
 * from {@code notification.<type>} message keys plus the payload.
 *
 * @param recipientId who is notified
 * @param type        what happened
 * @param target      the object it is about
 * @param payload     JSON-serialisable, locale-independent details (ids, never translated text)
 */
public record NotificationMessage(UUID recipientId, NotificationType type, NotificationTarget target,
		Map<String, Object> payload) {

	public NotificationMessage {
		Objects.requireNonNull(recipientId, "recipientId");
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(target, "target");
		payload = payload == null ? Map.of() : Map.copyOf(payload);
	}

}
