package com.kpi.studentcouncil.crm.members.internal;

import java.util.Optional;
import java.util.UUID;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableRepository;

interface MemberProfileRepository extends ArchivableRepository<MemberProfile, UUID> {

	/** Includes archived profiles. */
	Optional<MemberProfile> findByUserId(UUID userId);

}
