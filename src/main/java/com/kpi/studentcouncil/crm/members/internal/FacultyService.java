package com.kpi.studentcouncil.crm.members.internal;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.audit.AuditAction;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.members.FacultyEvent;
import com.kpi.studentcouncil.crm.shared.error.ConflictException;
import com.kpi.studentcouncil.crm.shared.error.NotFoundException;

/**
 * Commands and queries of the faculty dictionary. Archiving is always soft and never touches profiles that
 * reference the faculty: only selection of the faculty for new references is blocked ({@code FacultyLookup}).
 */
@Service
class FacultyService {

	static final String ENTITY_TYPE = "Faculty";
	/** Id that matches no row, used by uniqueness checks on create. */
	private static final UUID NO_ID = new UUID(0L, 0L);

	private final FacultyRepository faculties;
	private final AuditPublisher audit;
	private final ActorProvider actors;
	private final ApplicationEventPublisher events;
	private final Clock clock;

	FacultyService(FacultyRepository faculties, AuditPublisher audit, ActorProvider actors,
			ApplicationEventPublisher events, Clock clock) {
		this.faculties = faculties;
		this.audit = audit;
		this.actors = actors;
		this.events = events;
		this.clock = clock;
	}

	@Transactional
	Faculty create(String code, String nameUk, String nameEn) {
		String c = normalizeCode(code);
		String uk = nameUk.strip();
		String en = nameEn.strip();
		assertUnique(c, uk, en, NO_ID);
		Faculty saved = faculties.save(new Faculty(c, uk, en));
		audit.publish(AuditAction.CREATE, ENTITY_TYPE, saved.getId(), null, snapshot(saved));
		publish(FacultyEvent.Kind.CREATED, saved);
		return saved;
	}

	@Transactional
	Faculty update(UUID id, String code, String nameUk, String nameEn) {
		Faculty faculty = require(id);
		if (faculty.isArchived()) {
			throw new ConflictException("FACULTY_ARCHIVED");
		}
		String c = normalizeCode(code);
		String uk = nameUk.strip();
		String en = nameEn.strip();
		assertUnique(c, uk, en, id);
		Map<String, Object> before = snapshot(faculty);
		faculty.update(c, uk, en);
		Faculty saved = faculties.save(faculty);
		audit.publish(AuditAction.UPDATE, ENTITY_TYPE, id, before, snapshot(saved));
		publish(FacultyEvent.Kind.UPDATED, saved);
		return saved;
	}

	@Transactional
	Faculty archive(UUID id) {
		Faculty faculty = require(id);
		Map<String, Object> before = snapshot(faculty);
		faculty.archive(actors.currentActorId(), clock.instant());
		Faculty saved = faculties.save(faculty);
		audit.publish(AuditAction.ARCHIVE, ENTITY_TYPE, id, before, snapshot(saved));
		publish(FacultyEvent.Kind.ARCHIVED, saved);
		return saved;
	}

	@Transactional
	Faculty restore(UUID id) {
		Faculty faculty = require(id);
		if (!faculty.isArchived()) {
			throw new ConflictException("NOT_ARCHIVED");
		}
		// an active faculty may have taken the code or a name in the meantime
		assertUnique(faculty.getCode(), faculty.getNameUk(), faculty.getNameEn(), id);
		Map<String, Object> before = snapshot(faculty);
		faculty.restore();
		Faculty saved = faculties.save(faculty);
		audit.publish(AuditAction.RESTORE, ENTITY_TYPE, id, before, snapshot(saved));
		publish(FacultyEvent.Kind.RESTORED, saved);
		return saved;
	}

	@Transactional(readOnly = true)
	Faculty get(UUID id) {
		return require(id);
	}

	@Transactional(readOnly = true)
	Page<Faculty> list(boolean includeArchived, Pageable pageable) {
		if (includeArchived) {
			return faculties.findAll((root, query, cb) -> cb.conjunction(), pageable);
		}
		return faculties.findByArchivedAtIsNull(pageable);
	}

	private Faculty require(UUID id) {
		return faculties.findById(id).orElseThrow(() -> new NotFoundException(ENTITY_TYPE, id));
	}

	private void assertUnique(String code, String nameUk, String nameEn, UUID selfId) {
		if (faculties.existsByCodeIgnoreCaseAndArchivedAtIsNullAndIdNot(code, selfId)) {
			throw new ConflictException("FACULTY_CODE_TAKEN", code);
		}
		if (faculties.existsByNameUkIgnoreCaseAndArchivedAtIsNullAndIdNot(nameUk, selfId)) {
			throw new ConflictException("FACULTY_NAME_TAKEN", "uk", nameUk);
		}
		if (faculties.existsByNameEnIgnoreCaseAndArchivedAtIsNullAndIdNot(nameEn, selfId)) {
			throw new ConflictException("FACULTY_NAME_TAKEN", "en", nameEn);
		}
	}

	static String normalizeCode(String code) {
		return code.strip().toUpperCase(Locale.ROOT);
	}

	private void publish(FacultyEvent.Kind kind, Faculty faculty) {
		events.publishEvent(new FacultyEvent(kind, faculty.getId(), faculty.getCode()));
	}

	private static Map<String, Object> snapshot(Faculty faculty) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("code", faculty.getCode());
		map.put("nameUk", faculty.getNameUk());
		map.put("nameEn", faculty.getNameEn());
		map.put("archivedAt", faculty.getArchivedAt() == null ? null : faculty.getArchivedAt().toString());
		return map;
	}

}
