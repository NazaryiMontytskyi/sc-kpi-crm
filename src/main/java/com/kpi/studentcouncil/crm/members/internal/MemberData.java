package com.kpi.studentcouncil.crm.members.internal;

import java.time.LocalDate;
import java.util.UUID;

/** Normalised, validated profile values handed to the entity. */
record MemberData(String lastName, String firstName, String middleName, UUID facultyId, String studyGroup,
		String telegram, LocalDate joinedAt, UUID avatarFileId, String bio) {
}
