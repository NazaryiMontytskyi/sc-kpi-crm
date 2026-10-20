package com.kpi.studentcouncil.crm.tasks.internal;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.audit.AuditAction;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;
import com.kpi.studentcouncil.crm.shared.error.ConflictException;
import com.kpi.studentcouncil.crm.shared.error.NotFoundException;
import com.kpi.studentcouncil.crm.tasks.internal.SavedFilter.Criteria;

/**
 * Saved task filters of the current user. Every operation is scoped to the owner: a filter of somebody else is
 * indistinguishable from a missing one (404). Removing archives the filter.
 */
@Service
class SavedFilterService {

	static final String SAVED_FILTER = "SavedFilter";
	static final int MAX_PER_USER = 50;

	private final SavedFilterRepository filters;
	private final AuditPublisher audit;
	private final ActorProvider actors;
	private final Clock clock;

	SavedFilterService(SavedFilterRepository filters, AuditPublisher audit, ActorProvider actors, Clock clock) {
		this.filters = filters;
		this.audit = audit;
		this.actors = actors;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	Page<SavedFilter> list(Pageable pageable) {
		return filters.findByOwnerIdAndArchivedAtIsNull(actors.currentActorId(), pageable);
	}

	@Transactional(readOnly = true)
	SavedFilter get(UUID id) {
		return own(id);
	}

	@Transactional
	SavedFilter create(String name, Criteria criteria) {
		UUID me = actors.currentActorId();
		String cleanName = name.strip();
		if (filters.countByOwnerIdAndArchivedAtIsNull(me) >= MAX_PER_USER) {
			throw new BusinessRuleException("SAVED_FILTER_LIMIT", MAX_PER_USER);
		}
		assertNameFree(me, cleanName, null);
		SavedFilter saved = filters.save(new SavedFilter(me, cleanName, normalized(criteria)));
		audit.publish(AuditAction.CREATE, SAVED_FILTER, saved.getId(), null, saved.auditSnapshot());
		return saved;
	}

	@Transactional
	SavedFilter update(UUID id, String name, Criteria criteria) {
		SavedFilter filter = own(id);
		String cleanName = name.strip();
		assertNameFree(filter.getOwnerId(), cleanName, id);
		Map<String, Object> before = filter.auditSnapshot();
		filter.update(cleanName, normalized(criteria));
		SavedFilter saved = filters.save(filter);
		audit.publish(AuditAction.UPDATE, SAVED_FILTER, id, before, saved.auditSnapshot());
		return saved;
	}

	@Transactional
	void remove(UUID id) {
		SavedFilter filter = own(id);
		Map<String, Object> before = filter.auditSnapshot();
		filter.archive(actors.currentActorId(), clock.instant());
		SavedFilter saved = filters.save(filter);
		audit.publish(AuditAction.ARCHIVE, SAVED_FILTER, id, before, saved.auditSnapshot());
	}

	/** The active filter when it belongs to the current user; 404 for anything else. */
	private SavedFilter own(UUID id) {
		UUID me = actors.currentActorId();
		return filters.findByIdAndArchivedAtIsNull(id).filter(f -> f.getOwnerId().equals(me))
				.orElseThrow(() -> new NotFoundException(SAVED_FILTER, id));
	}

	private void assertNameFree(UUID owner, String name, UUID selfId) {
		filters.findByOwnerIdAndNameIgnoreCaseAndArchivedAtIsNull(owner, name).filter(f -> !f.getId().equals(selfId))
				.ifPresent(f -> {
					throw new ConflictException("SAVED_FILTER_NAME_TAKEN");
				});
	}

	private static Criteria normalized(Criteria c) {
		return new Criteria(c.spaceId(), c.statusId(), TaskRules.normalizeLabels(c.labels()), c.executorId(),
				c.authorId(), c.resolutionState(), c.view(), c.dueFrom(), c.dueTo());
	}

}
