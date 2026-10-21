package com.kpi.studentcouncil.crm.knowledge.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableRepository;

interface OnboardingListRepository extends ArchivableRepository<OnboardingList, UUID> {

	OnboardingList saveAndFlush(OnboardingList list);

	/** Active lists of the general scope and of the given departments; the general list first. */
	@Query("select l from OnboardingList l where l.archivedAt is null and (l.orgUnitId is null or l.orgUnitId in :unitIds) "
			+ "order by case when l.orgUnitId is null then 0 else 1 end, lower(l.title), l.id")
	List<OnboardingList> findActiveForUnits(@Param("unitIds") Collection<UUID> unitIds);

	@Query("select l from OnboardingList l where (:includeArchived = true or l.archivedAt is null) "
			+ "order by case when l.orgUnitId is null then 0 else 1 end, lower(l.title), l.id")
	List<OnboardingList> findAllForManage(@Param("includeArchived") boolean includeArchived);

	@Query("select count(l) > 0 from OnboardingList l where l.archivedAt is null and "
			+ "((:unitId is null and l.orgUnitId is null) or l.orgUnitId = :unitId)")
	boolean existsActiveForScope(@Param("unitId") UUID unitId);

}
