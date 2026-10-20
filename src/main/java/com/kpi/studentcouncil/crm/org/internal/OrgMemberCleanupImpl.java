package com.kpi.studentcouncil.crm.org.internal;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kpi.studentcouncil.crm.org.OrgMemberCleanup;

@Service
class OrgMemberCleanupImpl implements OrgMemberCleanup {

	private final MembershipService memberships;

	private final AppointmentService appointments;

	OrgMemberCleanupImpl(MembershipService memberships, AppointmentService appointments) {
		this.memberships = memberships;
		this.appointments = appointments;
	}

	@Override
	@Transactional
	public void endAllOfUser(UUID userId) {
		memberships.endAllOfUser(userId);
		appointments.endAllOfUser(userId);
	}

}
