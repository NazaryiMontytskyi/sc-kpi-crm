package com.kpi.studentcouncil.crm.org;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Domain event published (inside the transaction) when a person joins or leaves an {@code OrgUnit}
 * (MembershipStarted / MembershipEnded). A move is an ENDED followed by a STARTED event.
 *
 * @param kind         what happened
 * @param membershipId the membership record
 * @param userId       the person (user id, equal to the member id)
 * @param orgUnitId    the unit
 * @param date         the start date for STARTED, the end date for ENDED
 */
public record MembershipEvent(Kind kind, UUID membershipId, UUID userId, UUID orgUnitId, LocalDate date) {

	public enum Kind {
		STARTED, ENDED
	}

}
