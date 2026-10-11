package com.kpi.studentcouncil.crm.org.internal;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.kpi.studentcouncil.crm.shared.domain.BaseEntity;
import com.kpi.studentcouncil.crm.shared.error.ConflictException;

/**
 * A person's membership in an OrgUnit. Not archivable and never deleted: ending sets {@code until} and the row
 * stays as history. Dates are calendar days (Europe/Kyiv); {@code until == null} means active.
 */
@Entity
@Table(name = "org_membership")
class Membership extends BaseEntity {

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "org_unit_id", nullable = false, updatable = false)
	private UUID orgUnitId;

	@Column(name = "since", nullable = false, updatable = false)
	private LocalDate since;

	@Column(name = "until")
	private LocalDate until;

	protected Membership() {
	}

	Membership(UUID userId, UUID orgUnitId, LocalDate since) {
		this.userId = userId;
		this.orgUnitId = orgUnitId;
		this.since = since;
	}

	UUID getUserId() {
		return userId;
	}

	UUID getOrgUnitId() {
		return orgUnitId;
	}

	LocalDate getSince() {
		return since;
	}

	LocalDate getUntil() {
		return until;
	}

	boolean isActive() {
		return until == null;
	}

	void end(LocalDate date) {
		if (!isActive()) {
			throw new ConflictException("MEMBERSHIP_ALREADY_ENDED");
		}
		MembershipPolicy.assertPeriod(since, date);
		this.until = date;
	}

	Map<String, Object> auditSnapshot() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("userId", userId.toString());
		map.put("orgUnitId", orgUnitId.toString());
		map.put("since", since.toString());
		map.put("until", until == null ? null : until.toString());
		return map;
	}

}
