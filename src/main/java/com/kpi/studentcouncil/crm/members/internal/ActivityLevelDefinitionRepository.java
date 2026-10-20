package com.kpi.studentcouncil.crm.members.internal;

import java.util.UUID;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableRepository;

interface ActivityLevelDefinitionRepository extends ArchivableRepository<ActivityLevelDefinition, UUID> {

	boolean existsByCodeIgnoreCase(String code);

	boolean existsByCodeIgnoreCaseAndArchivedAtIsNullAndIdNot(String code, UUID id);

}
