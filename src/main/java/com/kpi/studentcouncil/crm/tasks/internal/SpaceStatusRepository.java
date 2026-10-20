package com.kpi.studentcouncil.crm.tasks.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableRepository;

interface SpaceStatusRepository extends ArchivableRepository<SpaceStatus, UUID> {

	List<SpaceStatus> findBySpaceIdAndArchivedAtIsNullOrderByPositionAscIdAsc(UUID spaceId);

	/** Statuses by id, archived ones included. */
	List<SpaceStatus> findByIdIn(Collection<UUID> ids);

}
