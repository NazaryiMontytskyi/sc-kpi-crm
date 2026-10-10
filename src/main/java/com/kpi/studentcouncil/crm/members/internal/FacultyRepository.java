package com.kpi.studentcouncil.crm.members.internal;

import java.util.List;
import java.util.UUID;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableRepository;

interface FacultyRepository extends ArchivableRepository<Faculty, UUID> {

	List<Faculty> findByArchivedAtIsNullOrderByNameUkAsc();

	boolean existsByCodeIgnoreCase(String code);

	boolean existsByCodeIgnoreCaseAndArchivedAtIsNullAndIdNot(String code, UUID id);

	boolean existsByNameUkIgnoreCaseAndArchivedAtIsNullAndIdNot(String nameUk, UUID id);

	boolean existsByNameEnIgnoreCaseAndArchivedAtIsNullAndIdNot(String nameEn, UUID id);

}
