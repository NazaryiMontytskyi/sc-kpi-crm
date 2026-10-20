package com.kpi.studentcouncil.crm.tasks.internal;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Key of a symmetric link: the two task ids in canonical order ({@code low} sorts before {@code high}). */
@Embeddable
record TaskLinkId(@Column(name = "task_low", nullable = false) UUID low,
		@Column(name = "task_high", nullable = false) UUID high) implements Serializable {

	/**
	 * Canonical key of the pair. The order is the one PostgreSQL uses for {@code uuid} (unsigned bytes, i.e. the
	 * lower-case hex text), not {@link UUID#compareTo}, which compares signed longs.
	 */
	static TaskLinkId of(UUID a, UUID b) {
		return a.toString().compareTo(b.toString()) < 0 ? new TaskLinkId(a, b) : new TaskLinkId(b, a);
	}

	UUID other(UUID self) {
		return low.equals(self) ? high : low;
	}

}
