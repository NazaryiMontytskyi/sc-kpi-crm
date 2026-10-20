package com.kpi.studentcouncil.crm.tasks.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.kpi.studentcouncil.crm.tasks.CustomFieldType;
import com.kpi.studentcouncil.crm.tasks.StatusCategory;

/** Request and response shapes of the spaces API. */
final class TasksDtos {

	private TasksDtos() {
	}

	/** {@code general} defaults to false; a general space has no {@code orgUnitId}. */
	record CreateSpaceRequest(@NotBlank @Size(max = 200) String name, UUID orgUnitId, Boolean general) {
	}

	/** Replaces name, unit binding and general flag ({@code general} defaults to false). */
	record UpdateSpaceRequest(@NotBlank @Size(max = 200) String name, UUID orgUnitId, Boolean general) {
	}

	record SpaceSummaryResponse(UUID id, String name, UUID orgUnitId, boolean general, boolean archived,
			Instant archivedAt, Instant createdAt, Instant updatedAt) {
	}

	/** A space with its active statuses (in order) and active custom fields. */
	record SpaceResponse(UUID id, String name, UUID orgUnitId, boolean general, boolean archived, Instant archivedAt,
			Instant createdAt, Instant updatedAt, List<SpaceStatusResponse> statuses,
			List<CustomFieldResponse> customFields) {
	}

	record StatusRequest(@NotBlank @Size(max = 100) String name, @NotNull StatusCategory category) {
	}

	/** Every active status of the space, each exactly once, in the new order. */
	record ReorderStatusesRequest(@NotEmpty List<@NotNull UUID> statusIds) {
	}

	/** {@code replacementStatusId} is required while tasks still have the status. */
	record ArchiveStatusRequest(UUID replacementStatusId) {
	}

	record SpaceStatusResponse(UUID id, UUID spaceId, String name, StatusCategory category, int position,
			boolean archived, Instant createdAt, Instant updatedAt) {
	}

	/** {@code options} are required for SELECT and forbidden for the other types. */
	record CreateCustomFieldRequest(@NotBlank @Size(max = 100) String name, @NotNull CustomFieldType type,
			Boolean required, List<String> options) {
	}

	/** The type cannot change. {@code options} are required for SELECT fields. */
	record UpdateCustomFieldRequest(@NotBlank @Size(max = 100) String name, Boolean required, List<String> options) {
	}

	record CustomFieldResponse(UUID id, UUID spaceId, String name, CustomFieldType type, boolean required,
			List<String> options, boolean archived, Instant createdAt, Instant updatedAt) {
	}

}
