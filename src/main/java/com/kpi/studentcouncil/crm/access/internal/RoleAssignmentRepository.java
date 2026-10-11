package com.kpi.studentcouncil.crm.access.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableRepository;

interface RoleAssignmentRepository extends ArchivableRepository<RoleAssignment, UUID> {

	@Query("select a.roleId from RoleAssignment a where a.userId = :userId and a.archivedAt is null")
	List<UUID> findActiveRoleIds(@Param("userId") UUID userId);

	/** Number of active assignments per role. */
	@Query("select new com.kpi.studentcouncil.crm.access.internal.RoleAssignmentRepository$RoleCount(a.roleId, count(distinct a.userId))"
			+ " from RoleAssignment a where a.archivedAt is null group by a.roleId")
	List<RoleCount> countActiveByRole();

	record RoleCount(UUID roleId, long total) {
	}

	/** The active MANUAL assignment (no source); grants made on behalf of a record are separate rows (ADR-0006). */
	@Query("select a from RoleAssignment a where a.userId = :userId and a.roleId = :roleId"
			+ " and a.scopeOrgUnitId is null and a.sourceType is null and a.archivedAt is null")
	Optional<RoleAssignment> findByUserIdAndRoleIdAndScopeOrgUnitIdIsNullAndArchivedAtIsNull(
			@Param("userId") UUID userId, @Param("roleId") UUID roleId);

	Optional<RoleAssignment> findByUserIdAndRoleIdAndScopeOrgUnitIdIsNullAndSourceTypeAndSourceIdAndArchivedAtIsNull(
			UUID userId, UUID roleId, String sourceType, UUID sourceId);

	List<RoleAssignment> findBySourceTypeAndSourceIdAndArchivedAtIsNull(String sourceType, UUID sourceId);

}
