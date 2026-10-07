package com.kpi.studentcouncil.crm.access.internal;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.kpi.studentcouncil.crm.identity.FirstAdminCreated;

/** Gives the bootstrapped first account the hardcoded Admin role, in the same transaction as its creation. */
@Component
class FirstAdminListener {

	private final AccessServiceImpl access;

	FirstAdminListener(AccessServiceImpl access) {
		this.access = access;
	}

	@EventListener
	void on(FirstAdminCreated event) {
		access.grantAdmin(event.userId());
	}

}
