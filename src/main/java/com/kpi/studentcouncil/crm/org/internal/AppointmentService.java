package com.kpi.studentcouncil.crm.org.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kpi.studentcouncil.crm.access.AccessService;
import com.kpi.studentcouncil.crm.access.RoleGrantSource;
import com.kpi.studentcouncil.crm.audit.AuditAction;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.identity.UserDirectory;
import com.kpi.studentcouncil.crm.org.AppointmentEvent;
import com.kpi.studentcouncil.crm.org.OrgService;
import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;
import com.kpi.studentcouncil.crm.shared.error.ConflictException;
import com.kpi.studentcouncil.crm.shared.error.NotFoundException;

/**
 * Appointing a person to a position and ending the appointment. Appointing grants the position's default roles
 * through {@link AccessService} with the appointment as the grant source; ending revokes exactly those grants.
 */
@Service
class AppointmentService {

	static final String ENTITY_TYPE = "Appointment";
	static final String SOURCE_TYPE = "APPOINTMENT";
	private static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");

	private final AppointmentRepository appointments;
	private final PositionRepository positions;
	private final OrgService orgUnits;
	private final UserDirectory users;
	private final AccessService access;
	private final AuditPublisher audit;
	private final ApplicationEventPublisher events;
	private final Clock clock;

	AppointmentService(AppointmentRepository appointments, PositionRepository positions, OrgService orgUnits,
			UserDirectory users, AccessService access, AuditPublisher audit, ApplicationEventPublisher events,
			Clock clock) {
		this.appointments = appointments;
		this.positions = positions;
		this.orgUnits = orgUnits;
		this.users = users;
		this.access = access;
		this.audit = audit;
		this.events = events;
		this.clock = clock;
	}

	static RoleGrantSource sourceOf(UUID appointmentId) {
		return new RoleGrantSource(SOURCE_TYPE, appointmentId);
	}

	/** Appoints a person; {@code startDate} defaults to today (Europe/Kyiv) and must not be in the future. */
	@Transactional
	Appointment appoint(UUID positionId, UUID userId, LocalDate startDate) {
		Position position = positions.lockById(positionId)
				.orElseThrow(() -> new NotFoundException(PositionService.ENTITY_TYPE, positionId));
		if (position.isArchived()) {
			throw new ConflictException("POSITION_ARCHIVED");
		}
		if (!orgUnits.isActive(position.getOrgUnitId())) {
			throw new ConflictException("ORG_UNIT_ARCHIVED");
		}
		if (!users.exists(userId)) {
			throw new BusinessRuleException("APPOINTMENT_USER_NOT_FOUND", userId);
		}
		LocalDate start = startDate == null ? today() : startDate;
		if (start.isAfter(today())) {
			throw new BusinessRuleException("APPOINTMENT_DATE_IN_FUTURE");
		}
		if (appointments.existsByPositionIdAndUserIdAndEndDateIsNull(positionId, userId)) {
			throw new ConflictException("APPOINTMENT_DUPLICATE");
		}
		PositionPolicy.assertHasRoom(appointments.countByPositionIdAndEndDateIsNull(positionId),
				position.getCapacity());
		for (UUID roleId : position.getDefaultRoleIds()) {
			if (!access.isAssignableRole(roleId)) {
				throw new BusinessRuleException("POSITION_ROLE_UNAVAILABLE", roleId);
			}
		}
		Appointment saved = appointments.save(new Appointment(positionId, userId, start));
		RoleGrantSource source = sourceOf(saved.getId());
		position.getDefaultRoleIds().forEach(roleId -> access.grantRole(userId, roleId, source));

		Map<String, Object> after = saved.auditSnapshot();
		after.put("grantedRoleIds", position.getDefaultRoleIds().stream().map(UUID::toString).sorted().toList());
		audit.publish(AuditAction.APPOINT, ENTITY_TYPE, saved.getId(), null, after);
		events.publishEvent(new AppointmentEvent(AppointmentEvent.Kind.STARTED, saved.getId(), positionId,
				position.getOrgUnitId(), userId, position.isHead(), start));
		return saved;
	}

	/** Ends an appointment at {@code endDate} (default today, never in the future) and revokes its role grants. */
	@Transactional
	Appointment end(UUID appointmentId, LocalDate endDate) {
		Appointment appointment = require(appointmentId);
		LocalDate when = endDate == null ? today() : endDate;
		if (when.isAfter(today())) {
			throw new BusinessRuleException("APPOINTMENT_DATE_IN_FUTURE");
		}
		finish(appointment, when);
		return appointment;
	}

	/** Ends every active appointment of a user (the person was archived). Returns how many were ended. */
	@Transactional
	int endAllOfUser(UUID userId) {
		List<Appointment> active = appointments.findByUserIdAndEndDateIsNull(userId);
		for (Appointment appointment : active) {
			LocalDate today = today();
			finish(appointment, today.isBefore(appointment.getStartDate()) ? appointment.getStartDate() : today);
		}
		return active.size();
	}

	@Transactional(readOnly = true)
	Appointment get(UUID id) {
		return require(id);
	}

	@Transactional(readOnly = true)
	Page<Appointment> listOfPosition(UUID positionId, boolean includeEnded, Pageable pageable) {
		positions.findById(positionId)
				.orElseThrow(() -> new NotFoundException(PositionService.ENTITY_TYPE, positionId));
		return includeEnded ? appointments.findByPositionId(positionId, pageable)
				: appointments.findByPositionIdAndEndDateIsNull(positionId, pageable);
	}

	@Transactional(readOnly = true)
	Page<Appointment> listOfUser(UUID userId, boolean includeEnded, Pageable pageable) {
		return includeEnded ? appointments.findByUserId(userId, pageable)
				: appointments.findByUserIdAndEndDateIsNull(userId, pageable);
	}

	private void finish(Appointment appointment, LocalDate when) {
		Map<String, Object> before = appointment.auditSnapshot();
		appointment.end(when);
		Appointment saved = appointments.save(appointment);
		int revoked = access.revokeAllGrantedBy(sourceOf(saved.getId()));
		Map<String, Object> after = saved.auditSnapshot();
		after.put("revokedRoleGrants", revoked);
		audit.publish(AuditAction.DISMISS, ENTITY_TYPE, saved.getId(), before, after);
		Position position = positions.findById(saved.getPositionId())
				.orElseThrow(() -> new NotFoundException(PositionService.ENTITY_TYPE, saved.getPositionId()));
		events.publishEvent(new AppointmentEvent(AppointmentEvent.Kind.ENDED, saved.getId(), saved.getPositionId(),
				position.getOrgUnitId(), saved.getUserId(), position.isHead(), when));
	}

	private Appointment require(UUID id) {
		return appointments.findById(id).orElseThrow(() -> new NotFoundException(ENTITY_TYPE, id));
	}

	private LocalDate today() {
		return LocalDate.now(clock.withZone(KYIV));
	}

}
