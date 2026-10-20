package com.kpi.studentcouncil.crm.tasks.internal;

import java.util.List;
import java.util.UUID;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableRepository;

interface SpaceStatusRepository extends ArchivableRepository<SpaceStatus, UUID> {

	List<SpaceStatus> findBySpaceIdAndArchivedAtIsNullOrderByPositionAscIdAsc(UUID spaceId);

}
