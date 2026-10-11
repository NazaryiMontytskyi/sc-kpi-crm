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

import com.kpi.studentcouncil.crm.audit.AuditAction;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.identity.UserDirectory;
import com.kpi.studentcouncil.crm.org.MembershipEvent;
import com.kpi.studentcouncil.crm.org.OrgService;
import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;
import com.kpi.studentcouncil.crm.shared.error.ConflictException;
import com.kpi.studentcouncil.crm.shared.error.NotFoundException;

/** Commands and queries of memberships (user <-> OrgUnit, M:N). */
@Service
class MembershipService {

	static final String ENTITY_TYPE = "Membership";
	private static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");

	private final MembershipRepository memberships;
	private final OrgService orgUnits;
	private final UserDirectory users;
	private final AuditPublisher audit;
	private final ApplicationEventPublisher events;
	private final Clock clock;

	MembershipService(MembershipRepository memberships, OrgService orgUnits, UserDirectory users,
			AuditPublisher audit, ApplicationEventPublisher events, Clock clock) {
		this.memberships = memberships;
		this.orgUnits = orgUnits;
		this.users = users;
		this.audit = audit;
		this.events = events;
		this.clock = clock;
	}

	/** Adds a person to a unit. {@code since} defaults to today (Europe/Kyiv). */
	@Transactional
	Membership start(UUID orgUnitId, UUID userId, LocalDate since) {
		orgUnits.require(orgUnitId);
		if (!orgUnits.isActive(orgUnitId)) {
			throw new ConflictException("MEMBERSHIP_ORG_UNIT_ARCHIVED");
		}
		if (!users.exists(userId)) {
			throw new BusinessRuleException("MEMBERSHIP_USER_NOT_FOUND", userId);
		}
		return begin(orgUnitId, userId, since == null ? today() : since);
	}

	/** Ends a membership at {@code until} (default today) and keeps the record. */
	@Transactional
	Membership end(UUID membershipId, LocalDate until) {
		Membership membership = require(membershipId);
		finish(membership, until == null ? defaultEnd(membership) : until);
		return membership;
	}

	/** Moves a person to another unit: ends the old membership and starts a new one on the same date. */
	@Transactional
	Membership move(UUID membershipId, UUID targetOrgUnitId, LocalDate date) {
		Membership current = require(membershipId);
		if (!current.isActive()) {
			throw new ConflictException("MEMBERSHIP_ALREADY_ENDED");
		}
		if (current.getOrgUnitId().equals(targetOrgUnitId)) {
			throw new BusinessRuleException("MEMBERSHIP_MOVE_SAME_UNIT");
		}
		orgUnits.require(targetOrgUnitId);
		if (!orgUnits.isActive(targetOrgUnitId)) {
			throw new ConflictException("MEMBERSHIP_ORG_UNIT_ARCHIVED");
		}
		if (memberships.existsByUserIdAndOrgUnitIdAndUntilIsNull(current.getUserId(), targetOrgUnitId)) {
			throw new ConflictException("MEMBERSHIP_DUPLICATE");
		}
		LocalDate when = date == null ? defaultEnd(current) : date;
		finish(current, when);
		return begin(targetOrgUnitId, current.getUserId(), when);
	}

	/** Ends every active membership of a user (the person was archived). Returns how many were ended. */
	@Transactional
	int endAllOfUser(UUID userId) {
		List<Membership> active = memberships.findByUserIdAndUntilIsNull(userId);
		for (Membership membership : active) {
			finish(membership, defaultEnd(membership));
		}
		return active.size();
	}

	@Transactional(readOnly = true)
	Membership get(UUID id) {
		return require(id);
	}

	@Transactional(readOnly = true)
	Page<Membership> listOfUnit(UUID orgUnitId, boolean includeEnded, Pageable pageable) {
		orgUnits.require(orgUnitId);
		return includeEnded ? memberships.findByOrgUnitId(orgUnitId, pageable)
				: memberships.findByOrgUnitIdAndUntilIsNull(orgUnitId, pageable);
	}

	@Transactional(readOnly = true)
	Page<Membership> listOfUser(UUID userId, boolean includeEnded, Pageable pageable) {
		return includeEnded ? memberships.findByUserId(userId, pageable)
				: memberships.findByUserIdAndUntilIsNull(userId, pageable);
	}

	private Membership begin(UUID orgUnitId, UUID userId, LocalDate since) {
		if (memberships.existsByUserIdAndOrgUnitIdAndUntilIsNull(userId, orgUnitId)) {
			throw new ConflictException("MEMBERSHIP_DUPLICATE");
		}
		Membership saved = memberships.save(new Membership(userId, orgUnitId, since));
		audit.publish(AuditAction.CREATE, ENTITY_TYPE, saved.getId(), null, saved.auditSnapshot());
		events.publishEvent(
				new MembershipEvent(MembershipEvent.Kind.STARTED, saved.getId(), userId, orgUnitId, since));
		return saved;
	}

	private void finish(Membership membership, LocalDate until) {
		Map<String, Object> before = membership.auditSnapshot();
		membership.end(until);
		Membership saved = memberships.save(membership);
		audit.publish(AuditAction.UPDATE, ENTITY_TYPE, saved.getId(), before, saved.auditSnapshot());
		events.publishEvent(new MembershipEvent(MembershipEvent.Kind.ENDED, saved.getId(), saved.getUserId(),
				saved.getOrgUnitId(), until));
	}

	/** Today, but never before the start (a membership may start in the future). */
	private LocalDate defaultEnd(Membership membership) {
		LocalDate today = today();
		return today.isBefore(membership.getSince()) ? membership.getSince() : today;
	}

	private LocalDate today() {
		return LocalDate.now(clock.withZone(KYIV));
	}

	private Membership require(UUID id) {
		return memberships.findById(id).orElseThrow(() -> new NotFoundException(ENTITY_TYPE, id));
	}

}
