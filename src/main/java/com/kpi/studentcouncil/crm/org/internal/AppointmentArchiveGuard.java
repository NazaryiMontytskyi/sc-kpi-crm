package com.kpi.studentcouncil.crm.org.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.kpi.studentcouncil.crm.org.OrgUnitArchiveGuard;

/**
 * Archive policy [ASSUMPTION], same style as {@link MembershipArchiveGuard}: a unit whose positions have active
 * appointments cannot be archived (409 ORG_UNIT_ARCHIVE_BLOCKED); end the appointments first. Vacant positions
 * do not block; they stay (an archived unit refuses new appointments).
 */
@Component
class AppointmentArchiveGuard implements OrgUnitArchiveGuard {

	private final AppointmentRepository appointments;

	AppointmentArchiveGuard(AppointmentRepository appointments) {
		this.appointments = appointments;
	}

	@Override
	public Optional<String> vetoArchive(UUID orgUnitId) {
		long active = appointments.countActiveInUnit(orgUnitId);
		return active > 0 ? Optional.of("it has " + active + " active appointment(s)") : Optional.empty();
	}

}
