package com.kpi.studentcouncil.crm.shared.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/**
 * Base repository for archivable entities. Deliberately extends the marker {@link Repository} and not
 * {@code JpaRepository}, so no delete methods and no unfiltered {@code findAll} exist (CONTEXT.md section 5.11).
 * It deliberately does not extend JpaSpecificationExecutor either (that interface exposes delete(spec)).
 * Default reads exclude archived rows; archived rows are reachable only through the explicit archived-aware
 * methods and {@link #findById} (needed to restore).
 */
@NoRepositoryBean
public interface ArchivableRepository<T extends ArchivableEntity, ID> extends Repository<T, ID> {

	<S extends T> S save(S entity);

	/** Finds by id including archived rows (used to restore or show history). */
	Optional<T> findById(ID id);

	/** Specification query. Callers must add {@link ArchivableSpecs#notArchived()} themselves, or use {@link #findActive}. */
	Page<T> findAll(Specification<T> spec, Pageable pageable);

	Optional<T> findByIdAndArchivedAtIsNull(ID id);

	Page<T> findByArchivedAtIsNull(Pageable pageable);

	List<T> findByArchivedAtIsNull();

	Page<T> findByArchivedAtIsNotNull(Pageable pageable);

	/** Combines a caller specification with the non-archived default. */
	default Page<T> findActive(Specification<T> spec, Pageable pageable) {
		Specification<T> active = ArchivableSpecs.notArchived();
		return findAll(spec == null ? active : active.and(spec), pageable);
	}

}
