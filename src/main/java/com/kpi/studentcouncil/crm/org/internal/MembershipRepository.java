package com.kpi.studentcouncil.crm.org.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/** Deliberately without delete methods: memberships are history (CONTEXT.md 5.11). */
interface MembershipRepository extends Repository<Membership, UUID> {

	Membership save(Membership membership);

	Optional<Membership> findById(UUID id);

	boolean existsByUserIdAndOrgUnitIdAndUntilIsNull(UUID userId, UUID orgUnitId);

	long countByOrgUnitIdAndUntilIsNull(UUID orgUnitId);

	List<Membership> findByUserIdAndUntilIsNull(UUID userId);

	List<Membership> findByOrgUnitIdAndUntilIsNull(UUID orgUnitId);

	Page<Membership> findByOrgUnitId(UUID orgUnitId, Pageable pageable);

	Page<Membership> findByOrgUnitIdAndUntilIsNull(UUID orgUnitId, Pageable pageable);

	Page<Membership> findByUserId(UUID userId, Pageable pageable);

	Page<Membership> findByUserIdAndUntilIsNull(UUID userId, Pageable pageable);

}
