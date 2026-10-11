package com.kpi.studentcouncil.crm.org.internal;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.kpi.studentcouncil.crm.members.MemberEvent;

/**
 * Ends all active memberships and appointments of an archived member (appointments revoke the roles they granted). A plain synchronous listener: it runs inside the
 * archiving transaction, so the memberships and their audit rows commit (or roll back) together with it.
 */
@Component
class MemberArchivedListener {

	private final MembershipService service;

	private final AppointmentService appointmentService;

	MemberArchivedListener(MembershipService service, AppointmentService appointmentService) {
		this.service = service;
		this.appointmentService = appointmentService;
	}

	@EventListener
	void on(MemberEvent event) {
		if (event.kind() == MemberEvent.Kind.ARCHIVED) {
			service.endAllOfUser(event.memberId());
			appointmentService.endAllOfUser(event.memberId());
		}
	}

}
