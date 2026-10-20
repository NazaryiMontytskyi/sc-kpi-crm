package com.kpi.studentcouncil.crm.tasks.internal;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.audit.AuditAction;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.org.OrgService;
import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;
import com.kpi.studentcouncil.crm.shared.error.ConflictException;
import com.kpi.studentcouncil.crm.shared.error.NotFoundException;
import com.kpi.studentcouncil.crm.tasks.CustomFieldType;
import com.kpi.studentcouncil.crm.tasks.SpaceEvent;
import com.kpi.studentcouncil.crm.tasks.SpaceStatusUsageGuard;
import com.kpi.studentcouncil.crm.tasks.StatusCategory;

/**
 * Commands and queries of spaces, their ordered statuses and custom field definitions. Every command on a space
 * takes a row lock on it, so the status invariants (see {@link SpacePolicy}) hold under concurrency.
 */
@Service
class SpaceService {

	static final String SPACE = "Space";
	static final String STATUS = "SpaceStatus";
	static final String FIELD = "CustomFieldDefinition";

	private static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("uk");
	private static final List<DefaultStatus> DEFAULT_STATUSES = List.of(
			new DefaultStatus("space.status.default.todo", StatusCategory.TODO),
			new DefaultStatus("space.status.default.in_progress", StatusCategory.IN_PROGRESS),
			new DefaultStatus("space.status.default.done", StatusCategory.DONE),
			new DefaultStatus("space.status.default.canceled", StatusCategory.CANCELED));

	private record DefaultStatus(String messageKey, StatusCategory category) {
	}

	private final SpaceRepository spaces;
	private final SpaceStatusRepository statuses;
	private final CustomFieldDefinitionRepository fields;
	private final OrgService orgUnits;
	private final ObjectProvider<SpaceStatusUsageGuard> usageGuards;
	private final MessageSource messages;
	private final AuditPublisher audit;
	private final ActorProvider actors;
	private final ApplicationEventPublisher events;
	private final Clock clock;

	SpaceService(SpaceRepository spaces, SpaceStatusRepository statuses, CustomFieldDefinitionRepository fields,
			OrgService orgUnits, ObjectProvider<SpaceStatusUsageGuard> usageGuards, MessageSource messages,
			AuditPublisher audit, ActorProvider actors, ApplicationEventPublisher events, Clock clock) {
		this.spaces = spaces;
		this.statuses = statuses;
		this.fields = fields;
		this.orgUnits = orgUnits;
		this.usageGuards = usageGuards;
		this.messages = messages;
		this.audit = audit;
		this.actors = actors;
		this.events = events;
		this.clock = clock;
	}

	// ---- spaces ----

	/** Creates a space with the default statuses (To do, In progress, Done, Canceled in the default locale). */
	@Transactional
	Space create(String name, UUID orgUnitId, boolean general) {
		SpacePolicy.assertPlacement(general, orgUnitId);
		assertUnitUsable(orgUnitId);
		Space saved = spaces.save(new Space(name.strip(), orgUnitId, general));
		audit.publish(AuditAction.CREATE, SPACE, saved.getId(), null, saved.auditSnapshot());
		int position = 0;
		for (DefaultStatus def : DEFAULT_STATUSES) {
			String statusName = messages.getMessage(def.messageKey(), null, def.messageKey(), DEFAULT_LOCALE);
			SpaceStatus status = statuses.save(new SpaceStatus(saved.getId(), statusName, def.category(), position++));
			audit.publish(AuditAction.CREATE, STATUS, status.getId(), null, status.auditSnapshot());
		}
		publish(SpaceEvent.Kind.CREATED, saved);
		return saved;
	}

	@Transactional
	Space update(UUID id, String name, UUID orgUnitId, boolean general) {
		Space space = lockActive(id);
		SpacePolicy.assertPlacement(general, orgUnitId);
		if (orgUnitId != null && !orgUnitId.equals(space.getOrgUnitId())) {
			assertUnitUsable(orgUnitId);
		}
		Map<String, Object> before = space.auditSnapshot();
		space.update(name.strip(), orgUnitId, general);
		Space saved = spaces.save(space);
		audit.publish(AuditAction.UPDATE, SPACE, id, before, saved.auditSnapshot());
		publish(SpaceEvent.Kind.UPDATED, saved);
		return saved;
	}

	@Transactional
	Space archive(UUID id) {
		Space space = lock(id);
		if (space.isArchived()) {
			throw new ConflictException("ALREADY_ARCHIVED");
		}
		Map<String, Object> before = space.auditSnapshot();
		space.archive(actors.currentActorId(), clock.instant());
		Space saved = spaces.save(space);
		audit.publish(AuditAction.ARCHIVE, SPACE, id, before, saved.auditSnapshot());
		publish(SpaceEvent.Kind.ARCHIVED, saved);
		return saved;
	}

	@Transactional
	Space restore(UUID id) {
		Space space = lock(id);
		if (!space.isArchived()) {
			throw new ConflictException("NOT_ARCHIVED");
		}
		if (space.getOrgUnitId() != null && !orgUnits.isActive(space.getOrgUnitId())) {
			throw new ConflictException("SPACE_ORG_UNIT_ARCHIVED");
		}
		Map<String, Object> before = space.auditSnapshot();
		space.restore();
		Space saved = spaces.save(space);
		audit.publish(AuditAction.RESTORE, SPACE, id, before, saved.auditSnapshot());
		publish(SpaceEvent.Kind.RESTORED, saved);
		return saved;
	}

	@Transactional(readOnly = true)
	Space get(UUID id) {
		return spaces.findById(id).orElseThrow(() -> new NotFoundException(SPACE, id));
	}

	@Transactional(readOnly = true)
	Page<Space> list(boolean includeArchived, UUID orgUnitId, Pageable pageable) {
		Specification<Space> spec = orgUnitId == null ? null
				: (root, query, cb) -> cb.equal(root.get("orgUnitId"), orgUnitId);
		if (includeArchived) {
			Specification<Space> any = spec == null ? (root, query, cb) -> cb.conjunction() : spec;
			return spaces.findAll(any, pageable);
		}
		return spaces.findActive(spec, pageable);
	}

	// ---- statuses ----

	@Transactional(readOnly = true)
	List<SpaceStatus> statusesOf(UUID spaceId) {
		get(spaceId);
		return statuses.findBySpaceIdAndArchivedAtIsNullOrderByPositionAscIdAsc(spaceId);
	}

	@Transactional
	SpaceStatus addStatus(UUID spaceId, String name, StatusCategory category) {
		Space space = lockActive(spaceId);
		List<SpaceStatus> active = activeStatuses(spaceId);
		String cleaned = name.strip();
		assertStatusNameFree(active, cleaned, null);
		int position = active.stream().mapToInt(SpaceStatus::getPosition).max().orElse(-1) + 1;
		SpaceStatus saved = statuses.save(new SpaceStatus(spaceId, cleaned, category, position));
		audit.publish(AuditAction.CREATE, STATUS, saved.getId(), null, saved.auditSnapshot());
		publish(SpaceEvent.Kind.UPDATED, space);
		return saved;
	}

	/** Renames a status and/or changes its category; the space must keep a TODO and a DONE status. */
	@Transactional
	SpaceStatus updateStatus(UUID spaceId, UUID statusId, String name, StatusCategory category) {
		Space space = lockActive(spaceId);
		List<SpaceStatus> active = activeStatuses(spaceId);
		SpaceStatus status = find(active, spaceId, statusId);
		String cleaned = name.strip();
		assertStatusNameFree(active, cleaned, statusId);
		SpacePolicy.assertRequiredCategories(active.stream()
				.map(s -> s.getId().equals(statusId) ? category : s.getCategory()).toList());
		Map<String, Object> before = status.auditSnapshot();
		status.update(cleaned, category);
		SpaceStatus saved = statuses.save(status);
		audit.publish(AuditAction.UPDATE, STATUS, statusId, before, saved.auditSnapshot());
		publish(SpaceEvent.Kind.UPDATED, space);
		return saved;
	}

	/** Persists the order: {@code orderedIds} must contain every active status of the space exactly once. */
	@Transactional
	List<SpaceStatus> reorderStatuses(UUID spaceId, List<UUID> orderedIds) {
		Space space = lockActive(spaceId);
		List<SpaceStatus> active = activeStatuses(spaceId);
		SpacePolicy.assertCompleteOrder(active.stream().map(SpaceStatus::getId).toList(), orderedIds);
		Map<UUID, SpaceStatus> byId = new java.util.HashMap<>();
		active.forEach(s -> byId.put(s.getId(), s));
		List<UUID> before = active.stream().map(SpaceStatus::getId).toList();
		List<SpaceStatus> result = new ArrayList<>();
		for (int i = 0; i < orderedIds.size(); i++) {
			SpaceStatus status = byId.get(orderedIds.get(i));
			status.moveTo(i);
			result.add(statuses.save(status));
		}
		audit.publish(AuditAction.UPDATE, SPACE, spaceId, Map.of("statusOrder", ids(before)),
				Map.of("statusOrder", ids(orderedIds)));
		publish(SpaceEvent.Kind.UPDATED, space);
		return result;
	}

	/**
	 * Archives a status. While tasks use it, a {@code replacementStatusId} (another active status of the same space)
	 * is required and the tasks are moved to it; the space must keep a TODO and a DONE status.
	 */
	@Transactional
	SpaceStatus archiveStatus(UUID spaceId, UUID statusId, UUID replacementStatusId) {
		Space space = lockActive(spaceId);
		List<SpaceStatus> active = activeStatuses(spaceId);
		SpaceStatus status = find(active, spaceId, statusId);
		SpacePolicy.assertRequiredCategories(
				active.stream().filter(s -> !s.getId().equals(statusId)).map(SpaceStatus::getCategory).toList());
		List<SpaceStatusUsageGuard> guards = usageGuards.orderedStream().toList();
		long usages = guards.stream().mapToLong(g -> g.countUsages(spaceId, statusId)).sum();
		if (usages > 0) {
			if (replacementStatusId == null) {
				throw new ConflictException("SPACE_STATUS_IN_USE", usages);
			}
			if (replacementStatusId.equals(statusId)
					|| active.stream().noneMatch(s -> s.getId().equals(replacementStatusId))) {
				throw new BusinessRuleException("SPACE_STATUS_REPLACEMENT_INVALID");
			}
			guards.forEach(g -> g.reassign(spaceId, statusId, replacementStatusId));
		}
		Map<String, Object> before = status.auditSnapshot();
		status.archive(actors.currentActorId(), clock.instant());
		SpaceStatus saved = statuses.save(status);
		audit.publish(AuditAction.ARCHIVE, STATUS, statusId, before, saved.auditSnapshot());
		publish(SpaceEvent.Kind.UPDATED, space);
		return saved;
	}

	// ---- custom fields ----

	@Transactional(readOnly = true)
	List<CustomFieldDefinition> fieldsOf(UUID spaceId) {
		get(spaceId);
		return fields.findBySpaceIdAndArchivedAtIsNullOrderByCreatedAtAscIdAsc(spaceId);
	}

	@Transactional
	CustomFieldDefinition addField(UUID spaceId, String name, CustomFieldType type, boolean required,
			List<String> options) {
		Space space = lockActive(spaceId);
		String cleaned = name.strip();
		assertFieldNameFree(activeFields(spaceId), cleaned, null);
		List<String> normalized = SpacePolicy.normalizeOptions(type, options);
		CustomFieldDefinition saved = fields.save(new CustomFieldDefinition(spaceId, cleaned, type, required, normalized));
		audit.publish(AuditAction.CREATE, FIELD, saved.getId(), null, saved.auditSnapshot());
		publish(SpaceEvent.Kind.UPDATED, space);
		return saved;
	}

	/** Updates name, required flag and options; the type is fixed. */
	@Transactional
	CustomFieldDefinition updateField(UUID spaceId, UUID fieldId, String name, boolean required, List<String> options) {
		Space space = lockActive(spaceId);
		List<CustomFieldDefinition> active = activeFields(spaceId);
		CustomFieldDefinition field = findField(active, spaceId, fieldId);
		String cleaned = name.strip();
		assertFieldNameFree(active, cleaned, fieldId);
		List<String> normalized = SpacePolicy.normalizeOptions(field.getType(), options);
		Map<String, Object> before = field.auditSnapshot();
		field.update(cleaned, required, normalized);
		CustomFieldDefinition saved = fields.save(field);
		audit.publish(AuditAction.UPDATE, FIELD, fieldId, before, saved.auditSnapshot());
		publish(SpaceEvent.Kind.UPDATED, space);
		return saved;
	}

	@Transactional
	CustomFieldDefinition archiveField(UUID spaceId, UUID fieldId) {
		Space space = lockActive(spaceId);
		CustomFieldDefinition field = findField(activeFields(spaceId), spaceId, fieldId);
		Map<String, Object> before = field.auditSnapshot();
		field.archive(actors.currentActorId(), clock.instant());
		CustomFieldDefinition saved = fields.save(field);
		audit.publish(AuditAction.ARCHIVE, FIELD, fieldId, before, saved.auditSnapshot());
		publish(SpaceEvent.Kind.UPDATED, space);
		return saved;
	}

	// ---- helpers ----

	private Space lock(UUID id) {
		return spaces.lockById(id).orElseThrow(() -> new NotFoundException(SPACE, id));
	}

	private Space lockActive(UUID id) {
		Space space = lock(id);
		if (space.isArchived()) {
			throw new ConflictException("SPACE_ARCHIVED");
		}
		return space;
	}

	private void assertUnitUsable(UUID orgUnitId) {
		if (orgUnitId == null) {
			return;
		}
		orgUnits.require(orgUnitId);
		if (!orgUnits.isActive(orgUnitId)) {
			throw new ConflictException("SPACE_ORG_UNIT_ARCHIVED");
		}
	}

	private List<SpaceStatus> activeStatuses(UUID spaceId) {
		return statuses.findBySpaceIdAndArchivedAtIsNullOrderByPositionAscIdAsc(spaceId);
	}

	private List<CustomFieldDefinition> activeFields(UUID spaceId) {
		return fields.findBySpaceIdAndArchivedAtIsNullOrderByCreatedAtAscIdAsc(spaceId);
	}

	private static SpaceStatus find(List<SpaceStatus> active, UUID spaceId, UUID statusId) {
		return active.stream().filter(s -> s.getId().equals(statusId)).findFirst()
				.orElseThrow(() -> new NotFoundException(STATUS, statusId));
	}

	private static CustomFieldDefinition findField(List<CustomFieldDefinition> active, UUID spaceId, UUID fieldId) {
		return active.stream().filter(f -> f.getId().equals(fieldId)).findFirst()
				.orElseThrow(() -> new NotFoundException(FIELD, fieldId));
	}

	private static void assertStatusNameFree(List<SpaceStatus> active, String name, UUID exceptId) {
		List<String> others = active.stream().filter(s -> !s.getId().equals(exceptId)).map(SpaceStatus::getName)
				.toList();
		if (SpacePolicy.nameTaken(others, name)) {
			throw new ConflictException("SPACE_STATUS_NAME_TAKEN", name);
		}
	}

	private static void assertFieldNameFree(List<CustomFieldDefinition> active, String name, UUID exceptId) {
		List<String> others = active.stream().filter(f -> !f.getId().equals(exceptId))
				.map(CustomFieldDefinition::getName).toList();
		if (SpacePolicy.nameTaken(others, name)) {
			throw new ConflictException("CUSTOM_FIELD_NAME_TAKEN", name);
		}
	}

	private static List<String> ids(List<UUID> ids) {
		return ids.stream().map(UUID::toString).toList();
	}

	private void publish(SpaceEvent.Kind kind, Space space) {
		events.publishEvent(new SpaceEvent(kind, space.getId(), space.getOrgUnitId(), space.isGeneral()));
	}

}
