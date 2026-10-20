package com.kpi.studentcouncil.crm.members.internal;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.kpi.studentcouncil.crm.members.MemberEvent;
import com.kpi.studentcouncil.crm.org.OrgMemberCleanup;

/**
 * Ends all active memberships and appointments of an archived member through the org public API. A plain
 * synchronous listener: it runs inside the archiving transaction, so the changes and their audit rows commit (or
 * roll back) together with the archiving.
 */
@Component
class MemberArchivedOrgListener {

	private final OrgMemberCleanup org;

	MemberArchivedOrgListener(OrgMemberCleanup org) {
		this.org = org;
	}

	@EventListener
	void on(MemberEvent event) {
		if (event.kind() == MemberEvent.Kind.ARCHIVED) {
			org.endAllOfUser(event.memberId());
		}
	}

}
