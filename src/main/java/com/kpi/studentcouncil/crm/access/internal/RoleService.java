package com.kpi.studentcouncil.crm.access.internal;

import java.time.Clock;
import java.util.Set;
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
import com.kpi.studentcouncil.crm.shared.error.NotFoundException;

/**
 * Role definitions (used by the role builder API in INC-012). The hardcoded Admin role and system rows can never
 * be changed or archived here. Not exposed over HTTP yet.
 */
@Service
@Transactional
class RoleService {

	static final String ENTITY = "Role";

	private final RoleRepository roles;

	private final PermissionCache cache;

	private final AuditPublisher audit;

	private final ActorProvider actors;

	private final Clock clock;

	RoleService(RoleRepository roles, PermissionCache cache, AuditPublisher audit, ActorProvider actors, Clock clock) {
		this.roles = roles;
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

	private Role mutable(UUID id) {
		if (AccessService.ADMIN_ROLE_ID.equals(id)) {
			throw new BusinessRuleException("SYSTEM_ROLE_IMMUTABLE");
		}
		Role role = roles.findByIdAndArchivedAtIsNull(id).orElseThrow(() -> new NotFoundException(ENTITY, id));
		if (role.isSystem()) {
			throw new BusinessRuleException("SYSTEM_ROLE_IMMUTABLE");
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
