package com.kpi.studentcouncil.crm.tasks.internal;

import java.util.List;
import java.util.UUID;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableRepository;

interface CustomFieldDefinitionRepository extends ArchivableRepository<CustomFieldDefinition, UUID> {

	List<CustomFieldDefinition> findBySpaceIdAndArchivedAtIsNullOrderByCreatedAtAscIdAsc(UUID spaceId);

}
