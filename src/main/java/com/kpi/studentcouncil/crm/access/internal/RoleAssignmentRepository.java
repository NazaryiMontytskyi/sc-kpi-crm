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
	@Query("select new com.kpi.studentcouncil.crm.access.internal.RoleAssignmentRepository$RoleCount(a.roleId, count(a))"
			+ " from RoleAssignment a where a.archivedAt is null group by a.roleId")
	List<RoleCount> countActiveByRole();

	record RoleCount(UUID roleId, long total) {
	}

	Optional<RoleAssignment> findByUserIdAndRoleIdAndScopeOrgUnitIdIsNullAndArchivedAtIsNull(UUID userId, UUID roleId);

}
