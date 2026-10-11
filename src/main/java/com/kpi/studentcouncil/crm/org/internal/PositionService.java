package com.kpi.studentcouncil.crm.org.internal;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kpi.studentcouncil.crm.access.AccessService;
import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.audit.AuditAction;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.org.OrgService;
import com.kpi.studentcouncil.crm.shared.error.ConflictException;
import com.kpi.studentcouncil.crm.shared.error.ForbiddenException;
import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;
import com.kpi.studentcouncil.crm.shared.error.NotFoundException;

/** Commands and queries of positions (posts of an OrgUnit, independent of the people holding them). */
@Service
class PositionService {

	static final String ENTITY_TYPE = "Position";

	private final PositionRepository positions;
	private final AppointmentRepository appointments;
	private final OrgService orgUnits;
	private final AccessService access;
	private final AuditPublisher audit;
	private final ActorProvider actors;
	private final Clock clock;

	PositionService(PositionRepository positions, AppointmentRepository appointments, OrgService orgUnits,
			AccessService access, AuditPublisher audit, ActorProvider actors, Clock clock) {
		this.positions = positions;
		this.appointments = appointments;
		this.orgUnits = orgUnits;
		this.access = access;
		this.audit = audit;
		this.actors = actors;
		this.clock = clock;
	}

	/** Creates a position. Capacity defaults to 1 [ASSUMPTION: one holder]. */
	@Transactional
	Position create(UUID orgUnitId, String title, boolean head, Integer capacity, Collection<UUID> roleIds) {
		orgUnits.require(orgUnitId);
		if (!orgUnits.isActive(orgUnitId)) {
			throw new ConflictException("ORG_UNIT_ARCHIVED");
		}
		int cap = capacity == null ? 1 : capacity;
		PositionPolicy.assertCapacity(head, cap);
		if (head) {
			assertNoHead(orgUnitId);
		}
		Set<UUID> roles = validRoles(roleIds);
		Position saved = positions.save(new Position(title.strip(), orgUnitId, head, cap, roles));
		audit.publish(AuditAction.CREATE, ENTITY_TYPE, saved.getId(), null, saved.auditSnapshot());
		return saved;
	}

	/**
	 * Updates title, capacity and bound roles. A role set change is applied to the current holders: added roles
	 * are granted and removed roles revoked, each from the holder's own appointment source.
	 */
	@Transactional
	Position update(UUID id, String title, Integer capacity, Collection<UUID> roleIds) {
		Position position = positions.lockById(id).orElseThrow(() -> new NotFoundException(ENTITY_TYPE, id));
		if (position.isArchived()) {
			throw new ConflictException("POSITION_ARCHIVED");
		}
		int cap = capacity == null ? position.getCapacity() : capacity;
		PositionPolicy.assertCapacity(position.isHead(), cap);
		List<Appointment> holders = appointments.findByPositionIdAndEndDateIsNull(id);
		PositionPolicy.assertCapacityCoversHolders(holders.size(), cap);
		Set<UUID> roles = roleIds == null ? position.getDefaultRoleIds() : validRoles(roleIds);
		PositionPolicy.RoleDiff diff = PositionPolicy.diff(position.getDefaultRoleIds(), roles);

		Map<String, Object> before = position.auditSnapshot();
		position.update(title.strip(), cap, roles);
		Position saved = positions.save(position);
		for (Appointment holder : holders) {
			var source = AppointmentService.sourceOf(holder.getId());
			diff.removed().forEach(roleId -> access.revokeRole(holder.getUserId(), roleId, source));
			diff.added().forEach(roleId -> access.grantRole(holder.getUserId(), roleId, source));
		}
		audit.publish(AuditAction.UPDATE, ENTITY_TYPE, id, before, saved.auditSnapshot());
		return saved;
	}

	/** Archives a vacant position; end the appointments first (409 otherwise). */
	@Transactional
	Position archive(UUID id) {
		Position position = positions.lockById(id).orElseThrow(() -> new NotFoundException(ENTITY_TYPE, id));
		if (position.isArchived()) {
			throw new ConflictException("ALREADY_ARCHIVED");
		}
		long active = appointments.countByPositionIdAndEndDateIsNull(id);
		if (active > 0) {
			throw new ConflictException("POSITION_HAS_ACTIVE_APPOINTMENTS", active);
		}
		Map<String, Object> before = position.auditSnapshot();
		position.archive(actors.currentActorId(), clock.instant());
		Position saved = positions.save(position);
		audit.publish(AuditAction.ARCHIVE, ENTITY_TYPE, id, before, saved.auditSnapshot());
		return saved;
	}

	@Transactional
	Position restore(UUID id) {
		Position position = positions.lockById(id).orElseThrow(() -> new NotFoundException(ENTITY_TYPE, id));
		if (!position.isArchived()) {
			throw new ConflictException("NOT_ARCHIVED");
		}
		if (!orgUnits.isActive(position.getOrgUnitId())) {
			throw new ConflictException("ORG_UNIT_ARCHIVED");
		}
		if (position.isHead()) {
			assertNoHead(position.getOrgUnitId());
		}
		Map<String, Object> before = position.auditSnapshot();
		position.restore();
		Position saved = positions.save(position);
		audit.publish(AuditAction.RESTORE, ENTITY_TYPE, id, before, saved.auditSnapshot());
		return saved;
	}

	@Transactional(readOnly = true)
	Position get(UUID id) {
		return positions.findById(id).orElseThrow(() -> new NotFoundException(ENTITY_TYPE, id));
	}

	@Transactional(readOnly = true)
	Page<Position> listOfUnit(UUID orgUnitId, boolean includeArchived, Pageable pageable) {
		orgUnits.require(orgUnitId);
		return includeArchived ? positions.findByOrgUnitId(orgUnitId, pageable)
				: positions.findByOrgUnitIdAndArchivedAtIsNull(orgUnitId, pageable);
	}

	/** Active holders (user ids) per position id, for the given positions. */
	@Transactional(readOnly = true)
	Map<UUID, List<UUID>> activeHolders(Collection<UUID> positionIds) {
		if (positionIds.isEmpty()) {
			return Map.of();
		}
		return appointments.findByPositionIdInAndEndDateIsNull(positionIds).stream().collect(
				Collectors.groupingBy(Appointment::getPositionId,
						Collectors.mapping(Appointment::getUserId, Collectors.toList())));
	}

	private void assertNoHead(UUID orgUnitId) {
		if (positions.existsByOrgUnitIdAndHeadTrueAndArchivedAtIsNull(orgUnitId)) {
			throw new ConflictException("POSITION_HEAD_EXISTS");
		}
	}

	/** Bound roles must exist and be assignable; the hardcoded Admin role can never be bound (ADR-0003). */
	private Set<UUID> validRoles(Collection<UUID> roleIds) {
		Set<UUID> roles = roleIds == null ? Set.of() : Set.copyOf(roleIds);
		for (UUID roleId : roles) {
			if (AccessService.ADMIN_ROLE_ID.equals(roleId)) {
				throw new ForbiddenException("ADMIN_ROLE_RESERVED");
			}
			if (!access.isAssignableRole(roleId)) {
				throw new BusinessRuleException("POSITION_ROLE_UNAVAILABLE", roleId);
			}
		}
		return roles;
	}

}
