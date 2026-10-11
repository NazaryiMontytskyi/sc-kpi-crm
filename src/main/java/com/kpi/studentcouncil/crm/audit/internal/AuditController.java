package com.kpi.studentcouncil.crm.audit.internal;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.kpi.studentcouncil.crm.audit.AuditAction;
import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;
import com.kpi.studentcouncil.crm.shared.error.ForbiddenException;
import com.kpi.studentcouncil.crm.shared.pagination.PageParams;
import com.kpi.studentcouncil.crm.shared.pagination.PageResponse;
import com.kpi.studentcouncil.crm.shared.web.ApiV1;

/**
 * Read-only audit log API (CONTEXT.md 5.10). The global log needs {@code audit.view}; the history of a single
 * object is visible to every authenticated user (openness by default), except for entity types listed in
 * {@link #RESTRICTED_ENTITY_TYPES}.
 */
@ApiV1
@RequestMapping("/audit")
@Tag(name = "Audit", description = "Audit log and object history (read-only)")
class AuditController {

	/**
	 * Extension point: entity type name -> permission key required to read its history. Empty today because no
	 * entity type with a non-default view permission exists yet. When one is introduced (e.g. restricted knowledge
	 * base pages guarded by {@code kb.restricted.view}), register it here so that object history cannot bypass the
	 * permission of the object itself.
	 */
	static final Map<String, String> RESTRICTED_ENTITY_TYPES = Map.of();

	private final AuditLogQuery query;

	AuditController(AuditLogQuery query) {
		this.query = query;
	}

	/** {@code ip} is only populated in the global log (never in object history). */
	record AuditEntryResponse(UUID id, UUID actorId, AuditAction action, String entityType, UUID entityId,
			Map<String, Object> before, Map<String, Object> after, Instant at, String ip) {
	}

	@GetMapping
	@PreAuthorize("hasAuthority('audit.view')")
	@Operation(summary = "Global audit log, newest first (paginated)",
			description = "Filters (all optional): actorId, entityId (UUIDs), entityType, action (AuditAction "
					+ "name), from (inclusive) and to (exclusive) as ISO-8601 instant or date (date = start of "
					+ "that UTC day). Invalid values yield 422 INVALID_FILTER.")
	PageResponse<AuditEntryResponse> list(@Valid PageParams page, @RequestParam(required = false) String actorId,
			@RequestParam(required = false) String entityType, @RequestParam(required = false) String entityId,
			@RequestParam(required = false) String action, @RequestParam(required = false) String from,
			@RequestParam(required = false) String to) {
		Instant fromInstant = parseInstant("from", from);
		Instant toInstant = parseInstant("to", to);
		if (fromInstant != null && toInstant != null && fromInstant.isAfter(toInstant)) {
			throw new BusinessRuleException("INVALID_FILTER", "from");
		}
		var filter = new AuditLogQuery.Filter(parseUuid("actorId", actorId), blankToNull(entityType),
				parseUuid("entityId", entityId), parseAction(action), fromInstant, toInstant);
		return PageResponse.of(query.search(filter, page.toPageable(Set.of(), Sort.unsorted())),
				e -> toResponse(e, true));
	}

	@GetMapping("/entities/{type}/{id}")
	@Operation(summary = "History of one object, newest first (paginated); any authenticated user",
			description = "403 for entity types registered as restricted when the permission is missing.")
	PageResponse<AuditEntryResponse> history(@PathVariable String type, @PathVariable UUID id,
			@Valid PageParams page, Authentication authentication) {
		String required = RESTRICTED_ENTITY_TYPES.get(type);
		if (required != null && (authentication == null
				|| authentication.getAuthorities().stream().noneMatch(a -> required.equals(a.getAuthority())))) {
			throw new ForbiddenException("FORBIDDEN");
		}
		var filter = new AuditLogQuery.Filter(null, type, id, null, null, null);
		return PageResponse.of(query.search(filter, page.toPageable(Set.of(), Sort.unsorted())),
				e -> toResponse(e, false));
	}

	private static AuditEntryResponse toResponse(AuditLog e, boolean withIp) {
		return new AuditEntryResponse(e.getId(), e.getActorId(), e.getAction(), e.getEntityType(), e.getEntityId(),
				e.getBefore(), e.getAfter(), e.getAt(), withIp ? e.getIp() : null);
	}

	private static String blankToNull(String v) {
		return v == null || v.isBlank() ? null : v.trim();
	}

	private static UUID parseUuid(String name, String v) {
		if (blankToNull(v) == null) {
			return null;
		}
		try {
			return UUID.fromString(v.trim());
		}
		catch (IllegalArgumentException ex) {
			throw new BusinessRuleException("INVALID_FILTER", name);
		}
	}

	private static AuditAction parseAction(String v) {
		if (blankToNull(v) == null) {
			return null;
		}
		try {
			return AuditAction.valueOf(v.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			throw new BusinessRuleException("INVALID_FILTER", "action");
		}
	}

	private static Instant parseInstant(String name, String v) {
		if (blankToNull(v) == null) {
			return null;
		}
		String s = v.trim();
		try {
			return OffsetDateTime.parse(s).toInstant();
		}
		catch (DateTimeException ex) {
			try {
				return LocalDate.parse(s).atStartOfDay(ZoneOffset.UTC).toInstant();
			}
			catch (DateTimeException ex2) {
				throw new BusinessRuleException("INVALID_FILTER", name);
			}
		}
	}

}
