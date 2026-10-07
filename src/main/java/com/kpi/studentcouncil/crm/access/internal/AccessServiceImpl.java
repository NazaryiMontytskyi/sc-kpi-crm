package com.kpi.studentcouncil.crm.access.internal;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
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
import com.kpi.studentcouncil.crm.identity.PermissionsProvider;
import com.kpi.studentcouncil.crm.identity.UserDirectory;
import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;
import com.kpi.studentcouncil.crm.shared.error.NotFoundException;

/** Computes effective permissions and manages role assignments (audited, cache-invalidating). */
@Service
@Transactional
class AccessServiceImpl implements AccessService, PermissionsProvider {

	static final String ASSIGNMENT_ENTITY = "RoleAssignment";

	static final String ADMIN_ROLE_NAME = "Admin";

	private final RoleRepository roles;

	private final RoleAssignmentRepository assignments;

	private final PermissionCache cache;

	private final AuditPublisher audit;

	private final ActorProvider actors;

	private final Clock clock;

	private final UserDirectory users;

	AccessServiceImpl(RoleRepository roles, RoleAssignmentRepository assignments, PermissionCache cache,
			AuditPublisher audit, ActorProvider actors, Clock clock, UserDirectory users) {
		this.roles = roles;
		this.assignments = assignments;
		this.cache = cache;
		this.audit = audit;
		this.actors = actors;
		this.clock = clock;
		this.users = users;
	}

	@Override
	@Transactional(readOnly = true)
	public Set<String> effectivePermissions(UUID userId) {
		return cache.get(userId, () -> compute(userId));
	}

	@Override
	@Transactional(readOnly = true)
	public Set<String> permissionsOf(UUID userId) {
		return effectivePermissions(userId);
	}

	@Override
	@Transactional(readOnly = true)
	public boolean hasPermission(UUID userId, String permissionKey) {
		return effectivePermissions(userId).contains(permissionKey);
	}

	private Set<String> compute(UUID userId) {
		List<UUID> roleIds = assignments.findActiveRoleIds(userId);
		boolean admin = roleIds.contains(ADMIN_ROLE_ID);
		List<Set<String>> rolePermissions = roles
				.findByIdInAndArchivedAtIsNull(roleIds.stream().filter(id -> !ADMIN_ROLE_ID.equals(id)).toList())
				.stream().map(Role::getPermissionKeys).toList();
		return union(admin, rolePermissions);
	}

	/** Pure rule: an Admin has every permission; otherwise the union of the roles' permissions. */
	static Set<String> union(boolean admin, List<Set<String>> rolePermissions) {
		if (admin) {
			return Permission.allKeys();
		}
		Set<String> result = new HashSet<>();
		rolePermissions.forEach(result::addAll);
		return Set.copyOf(result);
	}

	@Override
	public void grantRole(UUID userId, UUID roleId) {
		if (ADMIN_ROLE_ID.equals(roleId)) {
			throw new BusinessRuleException("ADMIN_ROLE_RESERVED");
		}
		Role role = roles.findByIdAndArchivedAtIsNull(roleId).orElseThrow(() -> new NotFoundException("Role", roleId));
		if (!users.exists(userId)) {
			throw new NotFoundException("User", userId);
		}
		grant(userId, roleId, role.getName());
	}

	/** Grants the hardcoded Admin role; internal system use only (first administrator). */
	void grantAdmin(UUID userId) {
		grant(userId, ADMIN_ROLE_ID, ADMIN_ROLE_NAME);
	}

	private void grant(UUID userId, UUID roleId, String roleName) {
		if (assignments.findByUserIdAndRoleIdAndScopeOrgUnitIdIsNullAndArchivedAtIsNull(userId, roleId).isPresent()) {
			return;
		}
		RoleAssignment assignment = assignments.save(RoleAssignment.create(userId, roleId));
		audit.publish(new AuditEvent(AuditAction.ROLE_CHANGE, ASSIGNMENT_ENTITY, assignment.getId(), null,
				assignment.auditSnapshot(roleName)));
		cache.invalidateAll();
	}

	@Override
	public void revokeRole(UUID userId, UUID roleId) {
		if (ADMIN_ROLE_ID.equals(roleId)) {
			throw new BusinessRuleException("ADMIN_ROLE_RESERVED");
		}
		RoleAssignment assignment = assignments
				.findByUserIdAndRoleIdAndScopeOrgUnitIdIsNullAndArchivedAtIsNull(userId, roleId).orElse(null);
		if (assignment == null) {
			return;
		}
		String roleName = roles.findById(roleId).map(Role::getName).orElse(null);
		var before = assignment.auditSnapshot(roleName);
		assignment.archive(actors.currentActorId(), clock.instant());
		audit.publish(new AuditEvent(AuditAction.ROLE_CHANGE, ASSIGNMENT_ENTITY, assignment.getId(), before,
				assignment.auditSnapshot(roleName)));
		cache.invalidateAll();
	}

}
