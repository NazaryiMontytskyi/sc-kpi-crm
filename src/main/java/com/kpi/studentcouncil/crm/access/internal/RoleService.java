package com.kpi.studentcouncil.crm.access.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kpi.studentcouncil.crm.access.AccessService;
import com.kpi.studentcouncil.crm.access.Permission;
import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.audit.AuditAction;
import com.kpi.studentcouncil.crm.audit.AuditEvent;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;
import com.kpi.studentcouncil.crm.shared.error.ConflictException;
import com.kpi.studentcouncil.crm.shared.error.ForbiddenException;
import com.kpi.studentcouncil.crm.shared.error.NotFoundException;

/**
 * Role definitions of the role builder. The hardcoded Admin role and system rows can never be changed or
 * archived here (403).
 */
@Service
@Transactional
class RoleService {

	static final String ENTITY = "Role";

	private final RoleRepository roles;

	private final RoleAssignmentRepository assignments;

	private final PermissionCache cache;

	private final AuditPublisher audit;

	private final ActorProvider actors;

	private final Clock clock;

	RoleService(RoleRepository roles, RoleAssignmentRepository assignments, PermissionCache cache,
			AuditPublisher audit, ActorProvider actors, Clock clock) {
		this.roles = roles;
		this.assignments = assignments;
		this.cache = cache;
		this.audit = audit;
		this.actors = actors;
		this.clock = clock;
	}

	Role create(String name, String description, Set<String> permissionKeys) {
		String clean = validName(name);
		validateKeys(permissionKeys);
		if (roles.existsByNameIgnoreCaseAndArchivedAtIsNull(clean)) {
			throw new ConflictException("ROLE_NAME_TAKEN", clean);
		}
		Role role = roles.save(Role.create(clean, description, permissionKeys));
		audit.publish(new AuditEvent(AuditAction.ROLE_CHANGE, ENTITY, role.getId(), null, role.auditSnapshot()));
		cache.invalidateAll();
		return role;
	}

	Role update(UUID id, String name, String description, Set<String> permissionKeys) {
		Role role = mutable(id);
		String clean = validName(name);
		validateKeys(permissionKeys);
		if (roles.existsByNameIgnoreCaseAndArchivedAtIsNullAndIdNot(clean, id)) {
			throw new ConflictException("ROLE_NAME_TAKEN", clean);
		}
		var before = role.auditSnapshot();
		role.update(clean, description, permissionKeys);
		audit.publish(new AuditEvent(AuditAction.ROLE_CHANGE, ENTITY, id, before, role.auditSnapshot()));
		cache.invalidateAll();
		return role;
	}

	void archive(UUID id) {
		Role role = mutable(id);
		var before = role.auditSnapshot();
		role.archive(actors.currentActorId(), clock.instant());
		audit.publish(new AuditEvent(AuditAction.ROLE_CHANGE, ENTITY, id, before, role.auditSnapshot()));
		cache.invalidateAll();
	}

	/** A role (including archived ones) or the virtual Admin role. */
	@Transactional(readOnly = true)
	RoleView get(UUID id) {
		if (AccessService.ADMIN_ROLE_ID.equals(id)) {
			return adminView(counts());
		}
		Role role = roles.findById(id).orElseThrow(() -> new NotFoundException(ENTITY, id));
		return view(role, counts().getOrDefault(id, 0L));
	}

	/** All roles: the virtual Admin role first, then stored roles by name. Archived ones only on request. */
	@Transactional(readOnly = true)
	List<RoleView> list(boolean includeArchived) {
		Map<UUID, Long> counts = counts();
		List<RoleView> result = new ArrayList<>();
		result.add(adminView(counts));
		(includeArchived ? roles.findAllRoles() : roles.findByArchivedAtIsNull()).stream()
				.sorted(Comparator.comparing(r -> r.getName().toLowerCase(Locale.ROOT)))
				.forEach(r -> result.add(view(r, counts.getOrDefault(r.getId(), 0L))));
		return result;
	}

	/** Active role assignments of a user (the Admin one included), archived roles marked as such. */
	@Transactional(readOnly = true)
	List<RoleView> rolesOfUser(UUID userId) {
		Map<UUID, Long> counts = counts();
		List<RoleView> result = new ArrayList<>();
		for (UUID roleId : assignments.findActiveRoleIds(userId).stream().distinct().toList()) {
			if (AccessService.ADMIN_ROLE_ID.equals(roleId)) {
				result.add(adminView(counts));
			}
			else {
				roles.findById(roleId).ifPresent(r -> result.add(view(r, counts.getOrDefault(roleId, 0L))));
			}
		}
		result.sort(Comparator.comparing(v -> v.name().toLowerCase(Locale.ROOT)));
		return result;
	}

	private Map<UUID, Long> counts() {
		Map<UUID, Long> counts = new HashMap<>();
		assignments.countActiveByRole().forEach(c -> counts.put(c.roleId(), c.total()));
		return counts;
	}

	private static RoleView view(Role r, long assigned) {
		return new RoleView(r.getId(), r.getName(), r.getDescription(), r.isSystem(),
				new TreeSet<>(r.getPermissionKeys()), r.isArchived(), r.getArchivedAt(), assigned, r.getCreatedAt(),
				r.getUpdatedAt());
	}

	private static RoleView adminView(Map<UUID, Long> counts) {
		return new RoleView(AccessService.ADMIN_ROLE_ID, AccessServiceImpl.ADMIN_ROLE_NAME, null, true,
				new TreeSet<>(Permission.allKeys()), false, null, counts.getOrDefault(AccessService.ADMIN_ROLE_ID, 0L),
				null, null);
	}

	/** Read model of a role; {@code permissions} are sorted keys. */
	record RoleView(UUID id, String name, String description, boolean system, Set<String> permissions,
			boolean archived, Instant archivedAt, long assignedUserCount, Instant createdAt, Instant updatedAt) {
	}

	private Role mutable(UUID id) {
		if (AccessService.ADMIN_ROLE_ID.equals(id)) {
			throw new ForbiddenException("SYSTEM_ROLE_IMMUTABLE");
		}
		Role role = roles.findByIdAndArchivedAtIsNull(id).orElseThrow(() -> new NotFoundException(ENTITY, id));
		if (role.isSystem()) {
			throw new ForbiddenException("SYSTEM_ROLE_IMMUTABLE");
		}
		return role;
	}

	private static String validName(String name) {
		String clean = name == null ? "" : name.strip();
		if (clean.isEmpty() || clean.length() > 100) {
			throw new BusinessRuleException("ROLE_NAME_INVALID");
		}
		if (clean.equalsIgnoreCase(AccessServiceImpl.ADMIN_ROLE_NAME)) {
			throw new BusinessRuleException("ROLE_NAME_RESERVED");
		}
		return clean;
	}

	private static void validateKeys(Set<String> keys) {
		for (String key : keys) {
			if (!Permission.isKnown(key)) {
				throw new BusinessRuleException("UNKNOWN_PERMISSION", key);
			}
		}
	}

}
