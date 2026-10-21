package com.kpi.studentcouncil.crm.notifications;

import java.util.Objects;
import java.util.UUID;

/**
 * Reference to the object a notification is about, used by the client for deep links.
 *
 * @param type logical type, e.g. "RESOLUTION", "TASK"
 * @param id   id of the object
 */
public record NotificationTarget(String type, UUID id) {

	public NotificationTarget {
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(id, "id");
	}

}
