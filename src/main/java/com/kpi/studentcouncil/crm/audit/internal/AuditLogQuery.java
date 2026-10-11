package com.kpi.studentcouncil.crm.audit.internal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.kpi.studentcouncil.crm.audit.AuditAction;

/** Read-only filtered search over the audit log (newest first). Never writes. */
@Component
class AuditLogQuery {

	/** All fields optional; {@code from} inclusive, {@code to} exclusive. */
	record Filter(UUID actorId, String entityType, UUID entityId, AuditAction action, Instant from, Instant to) {
	}

	@PersistenceContext
	private EntityManager em;

	@Transactional(readOnly = true)
	Page<AuditLog> search(Filter f, Pageable pageable) {
		CriteriaBuilder cb = em.getCriteriaBuilder();

		CriteriaQuery<AuditLog> select = cb.createQuery(AuditLog.class);
		Root<AuditLog> root = select.from(AuditLog.class);
		select.select(root).where(predicates(cb, root, f).toArray(Predicate[]::new))
				.orderBy(cb.desc(root.get("at")), cb.desc(root.get("id")));
		List<AuditLog> content = em.createQuery(select).setFirstResult((int) pageable.getOffset())
				.setMaxResults(pageable.getPageSize()).getResultList();

		CriteriaQuery<Long> count = cb.createQuery(Long.class);
		Root<AuditLog> countRoot = count.from(AuditLog.class);
		count.select(cb.count(countRoot)).where(predicates(cb, countRoot, f).toArray(Predicate[]::new));
		long total = em.createQuery(count).getSingleResult();

		return new PageImpl<>(content, pageable, total);
	}

	private static List<Predicate> predicates(CriteriaBuilder cb, Root<AuditLog> root, Filter f) {
		List<Predicate> p = new ArrayList<>();
		if (f.actorId() != null) {
			p.add(cb.equal(root.get("actorId"), f.actorId()));
		}
		if (f.entityType() != null) {
			p.add(cb.equal(root.get("entityType"), f.entityType()));
		}
		if (f.entityId() != null) {
			p.add(cb.equal(root.get("entityId"), f.entityId()));
		}
		if (f.action() != null) {
			p.add(cb.equal(root.get("action"), f.action()));
		}
		if (f.from() != null) {
			p.add(cb.greaterThanOrEqualTo(root.<Instant>get("at"), f.from()));
		}
		if (f.to() != null) {
			p.add(cb.lessThan(root.<Instant>get("at"), f.to()));
		}
		return p;
	}

}
