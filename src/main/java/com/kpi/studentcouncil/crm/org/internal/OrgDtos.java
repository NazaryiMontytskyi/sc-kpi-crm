package com.kpi.studentcouncil.crm.org.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.kpi.studentcouncil.crm.org.OrgUnitType;

/** Request and response shapes of the org API. */
final class OrgDtos {

	private OrgDtos() {
	}

	record CreateOrgUnitRequest(@NotBlank @Size(max = 200) String name, @NotNull OrgUnitType type, UUID parentId,
			@Size(max = 2000) String description) {
	}

	record UpdateOrgUnitRequest(@NotBlank @Size(max = 200) String name, @Size(max = 2000) String description) {
	}

	/** {@code parentId} null moves the unit to the top level (allowed only for types without a required parent). */
	record MoveOrgUnitRequest(UUID parentId) {
	}

	record OrgUnitResponse(UUID id, String name, OrgUnitType type, UUID parentId, String description,
			boolean archived, Instant archivedAt, Instant createdAt, Instant updatedAt, List<UUID> ancestorIds,
			List<UUID> childIds) {
	}

	record OrgUnitTreeNode(UUID id, String name, OrgUnitType type, String description, boolean archived,
			Instant archivedAt, List<OrgUnitTreeNode> children) {
	}

	/** {@code since} defaults to today (Europe/Kyiv). */
	record StartMembershipRequest(@NotNull UUID userId, LocalDate since) {
	}

	/** {@code until} defaults to today (Europe/Kyiv). */
	record EndMembershipRequest(LocalDate until) {
	}

	/** {@code date} (end of the old and start of the new membership) defaults to today (Europe/Kyiv). */
	record MoveMembershipRequest(@NotNull UUID targetOrgUnitId, LocalDate date) {
	}

	record MembershipResponse(UUID id, UUID userId, UUID orgUnitId, LocalDate since, LocalDate until, boolean active,
			Instant createdAt, Instant updatedAt) {
	}

}
