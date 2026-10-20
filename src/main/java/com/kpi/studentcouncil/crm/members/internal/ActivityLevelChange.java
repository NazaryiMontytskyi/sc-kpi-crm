package com.kpi.studentcouncil.crm.members.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.kpi.studentcouncil.crm.shared.domain.BaseEntity;

/** One entry of the append-only activity level history: no setters, no update, no delete (also enforced in the DB). */
@Entity
@Table(name = "activity_level_change")
class ActivityLevelChange extends BaseEntity {

	@Column(name = "member_id", nullable = false, updatable = false)
	private UUID memberId;

	@Column(name = "from_level_id", updatable = false)
	private UUID fromLevelId;

	@Column(name = "to_level_id", nullable = false, updatable = false)
	private UUID toLevelId;

	@Column(name = "comment", updatable = false, length = 1000)
	private String comment;

	@Column(name = "changed_by", nullable = false, updatable = false)
	private UUID changedBy;

	@Column(name = "changed_at", nullable = false, updatable = false)
	private Instant changedAt;

	protected ActivityLevelChange() {
	}

	ActivityLevelChange(UUID memberId, UUID fromLevelId, UUID toLevelId, String comment, UUID changedBy,
			Instant changedAt) {
		this.memberId = memberId;
		this.fromLevelId = fromLevelId;
		this.toLevelId = toLevelId;
		this.comment = comment;
		this.changedBy = changedBy;
		this.changedAt = changedAt;
	}

	UUID getMemberId() {
		return memberId;
	}

	UUID getFromLevelId() {
		return fromLevelId;
	}

	UUID getToLevelId() {
		return toLevelId;
	}

	String getComment() {
		return comment;
	}

	UUID getChangedBy() {
		return changedBy;
	}

	Instant getChangedAt() {
		return changedAt;
	}

}
