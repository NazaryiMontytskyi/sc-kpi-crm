package com.kpi.studentcouncil.crm.shared.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import com.kpi.studentcouncil.crm.shared.error.ConflictException;

/**
 * Soft-deletable entity (CONTEXT.md section 5.11): business entities are archived, never deleted.
 * Default "non-archived only" reads are provided by {@link ArchivableRepository}.
 */
@MappedSuperclass
public abstract class ArchivableEntity extends BaseEntity {

	@Column(name = "archived_at")
	private Instant archivedAt;

	@Column(name = "archived_by")
	private UUID archivedBy;

	public Instant getArchivedAt() {
		return archivedAt;
	}

	public UUID getArchivedBy() {
		return archivedBy;
	}

	public boolean isArchived() {
		return archivedAt != null;
	}

	/** Archives the entity. Fails with code {@code ALREADY_ARCHIVED} if it is already archived. */
	public void archive(UUID actorId, Instant at) {
		if (isArchived()) {
			throw new ConflictException("ALREADY_ARCHIVED");
		}
		this.archivedAt = at;
		this.archivedBy = actorId;
	}

	/** Restores an archived entity. Fails with code {@code NOT_ARCHIVED} if it is not archived. */
	public void restore() {
		if (!isArchived()) {
			throw new ConflictException("NOT_ARCHIVED");
		}
		this.archivedAt = null;
		this.archivedBy = null;
	}

}
