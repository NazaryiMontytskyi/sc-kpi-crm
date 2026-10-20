package com.kpi.studentcouncil.crm.members.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request and response shapes of the activity level API. */
final class ActivityLevelDtos {

	private ActivityLevelDtos() {
	}

	record DefinitionRequest(@NotBlank @Size(max = 30) @Pattern(regexp = "[A-Za-z0-9._-]+") String code,
			@NotBlank @Size(max = 100) String labelUk, @NotBlank @Size(max = 100) String labelEn,
			@NotNull @Min(0) @Max(100000) Integer sortOrder) {
	}

	record DefinitionResponse(UUID id, String code, String labelUk, String labelEn, int sortOrder, boolean archived,
			Instant archivedAt) {
	}

	/** Compact reference, also valid for archived definitions that history still points to. */
	record LevelRef(UUID id, String code, String labelUk, String labelEn, boolean archived) {
	}

	record SetLevelRequest(@NotNull UUID levelId, @Size(max = 1000) String comment) {
	}

	record ChangeResponse(UUID id, UUID memberId, LevelRef from, LevelRef to, String comment, UUID changedById,
			String changedByLogin, Instant changedAt) {
	}

}
