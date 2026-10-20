package com.kpi.studentcouncil.crm.tasks.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableRepository;

interface SavedFilterRepository extends ArchivableRepository<SavedFilter, UUID> {

	/** Active filters of one owner. */
	Page<SavedFilter> findByOwnerIdAndArchivedAtIsNull(UUID ownerId, Pageable pageable);

	long countByOwnerIdAndArchivedAtIsNull(UUID ownerId);

	/** The owner's active filter with the name, ignoring case. */
	Optional<SavedFilter> findByOwnerIdAndNameIgnoreCaseAndArchivedAtIsNull(UUID ownerId, String name);

}
