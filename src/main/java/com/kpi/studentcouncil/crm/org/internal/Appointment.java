package com.kpi.studentcouncil.crm.org.internal;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.kpi.studentcouncil.crm.shared.domain.BaseEntity;
import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;
import com.kpi.studentcouncil.crm.shared.error.ConflictException;

/**
 * A person holding a Position for a period. Not archivable and never deleted: ending sets {@code endDate} and the
 * row stays as history (who held the position before). Dates are calendar days (Europe/Kyiv);
 * {@code endDate == null} means active.
 */
@Entity
@Table(name = "org_appointment")
class Appointment extends BaseEntity {

	@Column(name = "position_id", nullable = false, updatable = false)
	private UUID positionId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "start_date", nullable = false, updatable = false)
	private LocalDate startDate;

	@Column(name = "end_date")
	private LocalDate endDate;

	protected Appointment() {
	}

	Appointment(UUID positionId, UUID userId, LocalDate startDate) {
		this.positionId = positionId;
		this.userId = userId;
		this.startDate = startDate;
	}

	UUID getPositionId() {
		return positionId;
	}

	UUID getUserId() {
		return userId;
	}

	LocalDate getStartDate() {
		return startDate;
	}

	LocalDate getEndDate() {
		return endDate;
	}

	boolean isActive() {
		return endDate == null;
	}

	void end(LocalDate date) {
		if (!isActive()) {
			throw new ConflictException("APPOINTMENT_ALREADY_ENDED");
		}
		if (date.isBefore(startDate)) {
			throw new BusinessRuleException("APPOINTMENT_INVALID_PERIOD", startDate, date);
		}
		this.endDate = date;
	}

	Map<String, Object> auditSnapshot() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("positionId", positionId.toString());
		map.put("userId", userId.toString());
		map.put("startDate", startDate.toString());
		map.put("endDate", endDate == null ? null : endDate.toString());
		return map;
	}

}
