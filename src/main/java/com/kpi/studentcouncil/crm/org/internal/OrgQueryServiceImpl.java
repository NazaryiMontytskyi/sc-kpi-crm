package com.kpi.studentcouncil.crm.org.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kpi.studentcouncil.crm.org.OrgQueryService;

@Service
class OrgQueryServiceImpl implements OrgQueryService {

	private final MembershipRepository memberships;

	private final AppointmentRepository appointments;

	OrgQueryServiceImpl(MembershipRepository memberships, AppointmentRepository appointments) {
		this.memberships = memberships;
		this.appointments = appointments;
	}

	@Override
	@Transactional(readOnly = true)
	public List<UUID> unitIdsOfUser(UUID userId) {
		return memberships.findByUserIdAndUntilIsNull(userId).stream().map(Membership::getOrgUnitId).distinct()
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<UUID> memberIdsOfUnit(UUID orgUnitId) {
		return memberships.findByOrgUnitIdAndUntilIsNull(orgUnitId).stream().map(Membership::getUserId).distinct()
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public boolean isMember(UUID userId, UUID orgUnitId) {
		return memberships.existsByUserIdAndOrgUnitIdAndUntilIsNull(userId, orgUnitId);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<UUID> headOfUnit(UUID orgUnitId) {
		return appointments.activeHeadUserIds(orgUnitId).stream().findFirst();
	}

	@Override
	@Transactional(readOnly = true)
	public List<UUID> holderIdsOfPosition(UUID positionId) {
		return appointments.findByPositionIdAndEndDateIsNull(positionId).stream().map(Appointment::getUserId)
				.distinct().toList();
	}

}
