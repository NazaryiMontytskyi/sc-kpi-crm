package com.kpi.studentcouncil.crm.tasks.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * One state transition of a resolution: who, from, to, comment, when. {@code from} is null for the creation entry.
 * {@code causedByResolutionId} is the resolution whose cancel cascaded to this one, otherwise null.
 */
@Embeddable
record ResolutionHistoryEntry(
		@Column(name = "actor_id", nullable = false) UUID actorId,
		@Enumerated(EnumType.STRING) @Column(name = "from_state", length = 20) ResolutionState from,
		@Enumerated(EnumType.STRING) @Column(name = "to_state", nullable = false, length = 20) ResolutionState to,
		@Column(name = "comment") String comment,
		@Column(name = "occurred_at", nullable = false) Instant at,
		@Column(name = "caused_by_resolution_id") UUID causedByResolutionId) {
}
