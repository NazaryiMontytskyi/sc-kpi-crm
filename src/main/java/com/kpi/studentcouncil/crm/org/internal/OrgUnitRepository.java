package com.kpi.studentcouncil.crm.org.internal;

import java.util.List;
import java.util.UUID;

import com.kpi.studentcouncil.crm.org.OrgUnitType;
import com.kpi.studentcouncil.crm.shared.domain.ArchivableRepository;

interface OrgUnitRepository extends ArchivableRepository<OrgUnit, UUID> {

	/** Every unit including archived ones (history view of the tree). */
	List<OrgUnit> findAllByOrderByNameAsc();

	List<OrgUnit> findByArchivedAtIsNullOrderByNameAsc();

	List<OrgUnit> findByParentIdAndArchivedAtIsNull(UUID parentId);

	long countByParentIdAndArchivedAtIsNull(UUID parentId);

	boolean existsByTypeAndArchivedAtIsNull(OrgUnitType type);

}
