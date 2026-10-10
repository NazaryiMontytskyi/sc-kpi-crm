package com.kpi.studentcouncil.crm.members;

import java.util.UUID;

/**
 * Domain event published (inside the transaction) after every change of a {@code Faculty}.
 *
 * @param kind what happened
 * @param id   the faculty
 * @param code the faculty code after the change
 */
public record FacultyEvent(Kind kind, UUID id, String code) {

	public enum Kind {
		CREATED, UPDATED, ARCHIVED, RESTORED
	}

}
