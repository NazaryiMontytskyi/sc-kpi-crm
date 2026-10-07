package com.kpi.studentcouncil.crm.shared.domain;

import org.springframework.data.jpa.domain.Specification;

/** Reusable specifications for archivable entities. */
public final class ArchivableSpecs {

	private ArchivableSpecs() {
	}

	public static <T extends ArchivableEntity> Specification<T> notArchived() {
		return (root, query, cb) -> cb.isNull(root.get("archivedAt"));
	}

	public static <T extends ArchivableEntity> Specification<T> archived() {
		return (root, query, cb) -> cb.isNotNull(root.get("archivedAt"));
	}

}
