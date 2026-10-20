package com.kpi.studentcouncil.crm.members.internal;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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

/**
 * The activity level scale (editable definitions) and the append-only change history of members. Setting a level
 * writes a history row, updates the current level on the profile and emits an audit event in one transaction.
 */
@Service
class ActivityLevelService {

	static final String DEFINITION_TYPE = "ActivityLevelDefinition";
	static final String CHANGE_TYPE = "ActivityLevelChange";
	private static final UUID NO_ID = new UUID(0L, 0L);

	private final ActivityLevelDefinitionRepository definitions;
	private final ActivityLevelChangeRepository changes;
	private final MemberProfileRepository profiles;
	private final AuditPublisher audit;
	private final ActorProvider actors;
	private final Clock clock;

	ActivityLevelService(ActivityLevelDefinitionRepository definitions, ActivityLevelChangeRepository changes,
			MemberProfileRepository profiles, AuditPublisher audit, ActorProvider actors, Clock clock) {
		this.definitions = definitions;
		this.changes = changes;
		this.profiles = profiles;
		this.audit = audit;
		this.actors = actors;
		this.clock = clock;
	}

	// ---- definitions -------------------------------------------------------------------------------------------

	@Transactional
	ActivityLevelDefinition create(String code, String labelUk, String labelEn, int sortOrder) {
		String c = normalizeCode(code);
		assertCodeFree(c, NO_ID);
		ActivityLevelDefinition saved = definitions
				.save(new ActivityLevelDefinition(c, labelUk.strip(), labelEn.strip(), sortOrder));
		audit.publish(AuditAction.CREATE, DEFINITION_TYPE, saved.getId(), null, snapshot(saved));
		return saved;
	}

	@Transactional
	ActivityLevelDefinition update(UUID id, String code, String labelUk, String labelEn, int sortOrder) {
		ActivityLevelDefinition level = requireDefinition(id);
		if (level.isArchived()) {
			throw new ConflictException("ACTIVITY_LEVEL_ARCHIVED");
		}
		String c = normalizeCode(code);
		assertCodeFree(c, id);
		Map<String, Object> before = snapshot(level);
		level.update(c, labelUk.strip(), labelEn.strip(), sortOrder);
		ActivityLevelDefinition saved = definitions.save(level);
		audit.publish(AuditAction.UPDATE, DEFINITION_TYPE, id, before, snapshot(saved));
		return saved;
	}

	@Transactional
	ActivityLevelDefinition archive(UUID id) {
		ActivityLevelDefinition level = requireDefinition(id);
		Map<String, Object> before = snapshot(level);
		level.archive(actors.currentActorId(), clock.instant());
		ActivityLevelDefinition saved = definitions.save(level);
		audit.publish(AuditAction.ARCHIVE, DEFINITION_TYPE, id, before, snapshot(saved));
		return saved;
	}

	@Transactional
	ActivityLevelDefinition restore(UUID id) {
		ActivityLevelDefinition level = requireDefinition(id);
		if (!level.isArchived()) {
			throw new ConflictException("NOT_ARCHIVED");
		}
		assertCodeFree(level.getCode(), id);
		Map<String, Object> before = snapshot(level);
		level.restore();
		ActivityLevelDefinition saved = definitions.save(level);
		audit.publish(AuditAction.RESTORE, DEFINITION_TYPE, id, before, snapshot(saved));
		return saved;
	}

	@Transactional(readOnly = true)
	ActivityLevelDefinition getDefinition(UUID id) {
		return requireDefinition(id);
	}

	@Transactional(readOnly = true)
	Optional<ActivityLevelDefinition> findDefinition(UUID id) {
		return id == null ? Optional.empty() : definitions.findById(id);
	}

	@Transactional(readOnly = true)
	Page<ActivityLevelDefinition> listDefinitions(boolean includeArchived, Pageable pageable) {
		if (includeArchived) {
			return definitions.findAll((root, query, cb) -> cb.conjunction(), pageable);
		}
		return definitions.findByArchivedAtIsNull(pageable);
	}

	// ---- member levels -----------------------------------------------------------------------------------------

	/** Sets the member's level; appends a history row. Requires an active member and an active, different level. */
	@Transactional
	ActivityLevelChange setLevel(UUID memberId, UUID levelId, String comment) {
		MemberProfile profile = profiles.findByUserId(memberId)
				.orElseThrow(() -> new NotFoundException("Member", memberId));
		if (profile.isArchived()) {
			throw new ConflictException("MEMBER_ARCHIVED");
		}
		ActivityLevelDefinition target = definitions.findByIdAndArchivedAtIsNull(levelId)
				.orElseThrow(() -> new BusinessRuleException("ACTIVITY_LEVEL_NOT_SELECTABLE"));
		UUID from = profile.getActivityLevelId();
		if (target.getId().equals(from)) {
			throw new BusinessRuleException("ACTIVITY_LEVEL_UNCHANGED");
		}
		String note = comment == null || comment.isBlank() ? null : comment.strip();
		profile.setActivityLevelId(target.getId());
		profiles.save(profile);
		ActivityLevelChange saved = changes.save(new ActivityLevelChange(memberId, from, target.getId(), note,
				actors.currentActorId(), clock.instant()));
		Map<String, Object> after = new LinkedHashMap<>();
		after.put("memberId", memberId.toString());
		after.put("fromLevelId", from == null ? null : from.toString());
		after.put("toLevelId", target.getId().toString());
		after.put("comment", note);
		audit.publish(AuditAction.CREATE, CHANGE_TYPE, saved.getId(), null, after);
		return saved;
	}

	@Transactional(readOnly = true)
	Page<ActivityLevelChange> history(UUID memberId, Pageable pageable) {
		if (profiles.findByUserId(memberId).isEmpty()) {
			throw new NotFoundException("Member", memberId);
		}
		return changes.findByMemberId(memberId, pageable);
	}

	// ---- helpers -----------------------------------------------------------------------------------------------

	private ActivityLevelDefinition requireDefinition(UUID id) {
		return definitions.findById(id).orElseThrow(() -> new NotFoundException(DEFINITION_TYPE, id));
	}

	private void assertCodeFree(String code, UUID selfId) {
		if (definitions.existsByCodeIgnoreCaseAndArchivedAtIsNullAndIdNot(code, selfId)) {
			throw new ConflictException("ACTIVITY_LEVEL_CODE_TAKEN", code);
		}
	}

	static String normalizeCode(String code) {
		return code.strip().toUpperCase(Locale.ROOT);
	}

	private static Map<String, Object> snapshot(ActivityLevelDefinition level) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("code", level.getCode());
		map.put("labelUk", level.getLabelUk());
		map.put("labelEn", level.getLabelEn());
		map.put("sortOrder", level.getSortOrder());
		map.put("archivedAt", level.getArchivedAt() == null ? null : level.getArchivedAt().toString());
		return map;
	}

}
