package com.kpi.studentcouncil.crm.members.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request and response shapes of the faculty API. */
final class FacultyDtos {

	private FacultyDtos() {
	}

	/** Code: letters, digits, dot, underscore, hyphen; stored upper-case. */
	record FacultyRequest(@NotBlank @Size(max = 30) @Pattern(regexp = "[A-Za-z0-9._-]+") String code,
			@NotBlank @Size(max = 200) String nameUk, @NotBlank @Size(max = 200) String nameEn) {
	}

	record FacultyResponse(UUID id, String code, String nameUk, String nameEn, boolean archived, Instant archivedAt,
			Instant createdAt, Instant updatedAt) {
	}

}
