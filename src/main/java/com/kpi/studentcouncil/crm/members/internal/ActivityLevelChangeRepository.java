package com.kpi.studentcouncil.crm.members.internal;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/** Append-only: deliberately exposes only save and reads, no delete. */
interface ActivityLevelChangeRepository extends Repository<ActivityLevelChange, UUID> {

	ActivityLevelChange save(ActivityLevelChange change);

	Page<ActivityLevelChange> findByMemberId(UUID memberId, Pageable pageable);

}
