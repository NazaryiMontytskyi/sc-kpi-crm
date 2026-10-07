package com.kpi.studentcouncil.crm.access.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableRepository;

interface RoleRepository extends ArchivableRepository<Role, UUID> {

	List<Role> findByIdInAndArchivedAtIsNull(Collection<UUID> ids);

	boolean existsByNameIgnoreCaseAndArchivedAtIsNull(String name);

	boolean existsByNameIgnoreCaseAndArchivedAtIsNullAndIdNot(String name, UUID id);

}
