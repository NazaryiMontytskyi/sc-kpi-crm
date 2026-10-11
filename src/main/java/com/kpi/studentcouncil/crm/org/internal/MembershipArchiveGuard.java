package com.kpi.studentcouncil.crm.org.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.kpi.studentcouncil.crm.org.OrgUnitArchiveGuard;

/**
 * Archive policy [ASSUMPTION] resolving INC-017's extension point: a unit with active memberships cannot be
 * archived (409 ORG_UNIT_ARCHIVE_BLOCKED); end or move the memberships first. Never silently ends memberships.
 */
@Component
class MembershipArchiveGuard implements OrgUnitArchiveGuard {

	private final MembershipRepository memberships;

	MembershipArchiveGuard(MembershipRepository memberships) {
		this.memberships = memberships;
	}

	@Override
	public Optional<String> vetoArchive(UUID orgUnitId) {
		long active = memberships.countByOrgUnitIdAndUntilIsNull(orgUnitId);
		return active > 0 ? Optional.of("it has " + active + " active membership(s)") : Optional.empty();
	}

}
