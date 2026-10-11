package com.kpi.studentcouncil.crm.org;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Domain event published (inside the transaction) when a person starts or stops holding a {@code Position}
 * (AppointmentStarted / AppointmentEnded).
 *
 * @param kind          what happened
 * @param appointmentId the appointment record
 * @param positionId    the position
 * @param orgUnitId     the unit the position belongs to
 * @param userId        the person (user id, equal to the member id)
 * @param head          true when the position is the head position of the unit
 * @param date          the start date for STARTED, the end date for ENDED
 */
public record AppointmentEvent(Kind kind, UUID appointmentId, UUID positionId, UUID orgUnitId, UUID userId,
		boolean head, LocalDate date) {

	public enum Kind {
		STARTED, ENDED
	}

}
