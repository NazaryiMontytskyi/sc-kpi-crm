package com.kpi.studentcouncil.crm.members;

import java.util.UUID;

/**
 * Domain event published (inside the transaction) after every change of a member profile
 * (MemberCreated, MemberUpdated, MemberArchived, MemberRestored).
 *
 * @param kind     what happened
 * @param memberId the member, equal to the user id
 */
public record MemberEvent(Kind kind, UUID memberId) {

	public enum Kind {
		CREATED, UPDATED, ARCHIVED, RESTORED
	}

}
