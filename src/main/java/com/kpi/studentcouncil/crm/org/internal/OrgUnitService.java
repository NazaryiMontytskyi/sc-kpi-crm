package com.kpi.studentcouncil.crm.org.internal;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.audit.AuditAction;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.org.OrgUnitArchiveGuard;
import com.kpi.studentcouncil.crm.org.OrgUnitEvent;
import com.kpi.studentcouncil.crm.org.OrgUnitInfo;
import com.kpi.studentcouncil.crm.org.OrgUnitType;
import com.kpi.studentcouncil.crm.org.internal.OrgDtos.OrgUnitTreeNode;
import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;
import com.kpi.studentcouncil.crm.shared.error.ConflictException;
import com.kpi.studentcouncil.crm.shared.error.NotFoundException;

/** Commands and queries of the org tree. Structural rules are in {@link OrgUnitPolicy}. */
@Service
class OrgUnitService {

	static final String ENTITY_TYPE = "OrgUnit";

	private final OrgUnitRepository units;
	private final ObjectProvider<OrgUnitArchiveGuard> archiveGuards;
	private final AuditPublisher audit;
	private final ActorProvider actors;
	private final ApplicationEventPublisher events;
	private final Clock clock;

	OrgUnitService(OrgUnitRepository units, ObjectProvider<OrgUnitArchiveGuard> archiveGuards, AuditPublisher audit,
			ActorProvider actors, ApplicationEventPublisher events, Clock clock) {
		this.units = units;
		this.archiveGuards = archiveGuards;
		this.audit = audit;
		this.actors = actors;
		this.events = events;
		this.clock = clock;
	}

	@Transactional
	OrgUnit create(String name, OrgUnitType type, UUID parentId, String description) {
		OrgUnit parent = activeParent(parentId);
		OrgUnitPolicy.assertPlacement(type, parent == null ? null : parent.getType());
		if (type == OrgUnitType.LEADERSHIP) {
			assertNoActiveLeadership();
		}
		OrgUnit saved = units.save(new OrgUnit(clean(name), type, parentId, blankToNull(description)));
		audit.publish(AuditAction.CREATE, ENTITY_TYPE, saved.getId(), null, snapshot(saved));
		publish(OrgUnitEvent.Kind.CREATED, saved);
		return saved;
	}

	@Transactional
	OrgUnit update(UUID id, String name, String description) {
		OrgUnit unit = requireActive(id);
		Map<String, Object> before = snapshot(unit);
		unit.update(clean(name), blankToNull(description));
		OrgUnit saved = units.save(unit);
		audit.publish(AuditAction.UPDATE, ENTITY_TYPE, id, before, snapshot(saved));
		publish(OrgUnitEvent.Kind.UPDATED, saved);
		return saved;
	}

	@Transactional
	OrgUnit move(UUID id, UUID newParentId) {
		OrgUnit unit = requireActive(id);
		OrgUnit parent = activeParent(newParentId);
		OrgUnitPolicy.assertPlacement(unit.getType(), parent == null ? null : parent.getType());
		OrgUnitPolicy.assertNoCycle(id, newParentId,
				candidate -> units.findById(candidate).map(OrgUnit::getParentId).orElse(null));
		if (Objects.equals(unit.getParentId(), newParentId)) {
			return unit;
		}
		Map<String, Object> before = snapshot(unit);
		unit.moveTo(newParentId);
		OrgUnit saved = units.save(unit);
		audit.publish(AuditAction.UPDATE, ENTITY_TYPE, id, before, snapshot(saved));
		publish(OrgUnitEvent.Kind.MOVED, saved);
		return saved;
	}

	@Transactional
	OrgUnit archive(UUID id) {
		OrgUnit unit = require(id);
		if (unit.isArchived()) {
			throw new ConflictException("ALREADY_ARCHIVED");
		}
		OrgUnitPolicy.assertCanArchive(units.countByParentIdAndArchivedAtIsNull(id));
		for (OrgUnitArchiveGuard guard : archiveGuards.orderedStream().toList()) {
			Optional<String> veto = guard.vetoArchive(id);
			if (veto.isPresent()) {
				throw new ConflictException("ORG_UNIT_ARCHIVE_BLOCKED", veto.get());
			}
		}
		Map<String, Object> before = snapshot(unit);
		unit.archive(actors.currentActorId(), clock.instant());
		OrgUnit saved = units.save(unit);
		audit.publish(AuditAction.ARCHIVE, ENTITY_TYPE, id, before, snapshot(saved));
		publish(OrgUnitEvent.Kind.ARCHIVED, saved);
		return saved;
	}

	@Transactional
	OrgUnit restore(UUID id) {
		OrgUnit unit = require(id);
		if (!unit.isArchived()) {
			throw new ConflictException("NOT_ARCHIVED");
		}
		activeParent(unit.getParentId());
		if (unit.getType() == OrgUnitType.LEADERSHIP) {
			assertNoActiveLeadership();
		}
		Map<String, Object> before = snapshot(unit);
		unit.restore();
		OrgUnit saved = units.save(unit);
		audit.publish(AuditAction.RESTORE, ENTITY_TYPE, id, before, snapshot(saved));
		publish(OrgUnitEvent.Kind.RESTORED, saved);
		return saved;
	}

	@Transactional(readOnly = true)
	OrgUnit get(UUID id) {
		return require(id);
	}

	/** Forest of units (roots: units without a parent), siblings sorted by name. */
	@Transactional(readOnly = true)
	List<OrgUnitTreeNode> tree(boolean includeArchived) {
		List<OrgUnit> all = includeArchived ? units.findAllByOrderByNameAsc()
				: units.findByArchivedAtIsNullOrderByNameAsc();
		Map<UUID, List<OrgUnit>> byParent = new LinkedHashMap<>();
		List<OrgUnit> roots = new ArrayList<>();
		Map<UUID, OrgUnit> byId = new LinkedHashMap<>();
		all.forEach(u -> byId.put(u.getId(), u));
		for (OrgUnit unit : all) {
			if (unit.getParentId() == null || !byId.containsKey(unit.getParentId())) {
				roots.add(unit);
			}
			else {
				byParent.computeIfAbsent(unit.getParentId(), k -> new ArrayList<>()).add(unit);
			}
		}
		Comparator<OrgUnit> order = Comparator.comparing((OrgUnit u) -> u.getName().toLowerCase())
				.thenComparing(OrgUnit::getId);
		roots.sort(order);
		return roots.stream().map(r -> node(r, byParent, order)).toList();
	}

	@Transactional(readOnly = true)
	OrgUnitInfo info(OrgUnit unit) {
		List<UUID> ancestors = new ArrayList<>();
		UUID cursor = unit.getParentId();
		while (cursor != null && !ancestors.contains(cursor) && !cursor.equals(unit.getId())) {
			ancestors.add(cursor);
			cursor = units.findById(cursor).map(OrgUnit::getParentId).orElse(null);
		}
		List<UUID> children = units.findByParentIdAndArchivedAtIsNull(unit.getId()).stream()
				.sorted(Comparator.comparing((OrgUnit u) -> u.getName().toLowerCase()).thenComparing(OrgUnit::getId))
				.map(OrgUnit::getId).toList();
		return new OrgUnitInfo(unit.getId(), unit.getName(), unit.getType(), unit.getParentId(),
				List.copyOf(ancestors), children, !unit.isArchived());
	}

	private OrgUnitTreeNode node(OrgUnit unit, Map<UUID, List<OrgUnit>> byParent, Comparator<OrgUnit> order) {
		List<OrgUnit> kids = new ArrayList<>(byParent.getOrDefault(unit.getId(), List.of()));
		kids.sort(order);
		return new OrgUnitTreeNode(unit.getId(), unit.getName(), unit.getType(), unit.getDescription(),
				unit.isArchived(), unit.getArchivedAt(), kids.stream().map(k -> node(k, byParent, order)).toList());
	}

	private OrgUnit require(UUID id) {
		return units.findById(id).orElseThrow(() -> new NotFoundException(ENTITY_TYPE, id));
	}

	private OrgUnit requireActive(UUID id) {
		OrgUnit unit = require(id);
		if (unit.isArchived()) {
			throw new ConflictException("ORG_UNIT_ARCHIVED");
		}
		return unit;
	}

	/** The parent must exist and be active; null id means no parent. */
	private OrgUnit activeParent(UUID parentId) {
		if (parentId == null) {
			return null;
		}
		OrgUnit parent = units.findById(parentId)
				.orElseThrow(() -> new BusinessRuleException("ORG_UNIT_PARENT_NOT_FOUND", parentId));
		if (parent.isArchived()) {
			throw new BusinessRuleException("ORG_UNIT_PARENT_ARCHIVED");
		}
		return parent;
	}

	private void assertNoActiveLeadership() {
		if (units.existsByTypeAndArchivedAtIsNull(OrgUnitType.LEADERSHIP)) {
			throw new ConflictException("ORG_UNIT_LEADERSHIP_EXISTS");
		}
	}

	private void publish(OrgUnitEvent.Kind kind, OrgUnit unit) {
		events.publishEvent(new OrgUnitEvent(kind, unit.getId(), unit.getType(), unit.getParentId()));
	}

	private static String clean(String name) {
		return name.strip();
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	private static Map<String, Object> snapshot(OrgUnit unit) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("name", unit.getName());
		map.put("type", unit.getType().name());
		map.put("parentId", unit.getParentId() == null ? null : unit.getParentId().toString());
		map.put("description", unit.getDescription());
		map.put("archivedAt", unit.getArchivedAt() == null ? null : unit.getArchivedAt().toString());
		return map;
	}

}
