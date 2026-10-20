package com.kpi.studentcouncil.crm.members.internal;

import java.util.UUID;

/**
 * Directory filters. All set criteria are combined with AND; unset (null) criteria are ignored.
 *
 * @param search          free text, matched case-insensitively against last, first and middle name and Telegram
 * @param facultyId       faculty dictionary entry
 * @param departmentId    members with an active membership in the unit or in any of its descendants
 * @param divisionId      same semantics as {@code departmentId}; a separate criterion so both can be combined
 * @param positionId      current holders of the position
 * @param activityLevelId activity level reference (definitions arrive with INC-022)
 * @param status          which members to include
 */
record MemberFilter(String search, UUID facultyId, UUID departmentId, UUID divisionId, UUID positionId,
		UUID activityLevelId, Status status) {

	enum Status {
		ACTIVE, ARCHIVED, ALL
	}

}
