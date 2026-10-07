package com.kpi.studentcouncil.crm.access;

import java.util.Set;
import java.util.UUID;

/**
 * Public API of the access module for other modules (position-bound roles, member management, ...).
 * Permission checks for HTTP calls use {@code @PreAuthorize("hasAuthority('x.y')")}; this API is for programmatic
 * checks and role granting.
 */
public interface AccessService {

	/**
	 * Reserved id of the hardcoded {@code Admin} system role. It has no row in the {@code role} table: the role is
	 * defined in code (all permissions) and referenced by {@code role_assignment.role_id} only.
	 */
	UUID ADMIN_ROLE_ID = new UUID(0L, 1L);

	/** Effective permission keys: union over active role assignments, all keys for an Admin. Never null. */
	Set<String> effectivePermissions(UUID userId);

	boolean hasPermission(UUID userId, String permissionKey);

	default boolean hasPermission(UUID userId, Permission permission) {
		return hasPermission(userId, permission.key());
	}

	/**
	 * Grants a (non-Admin) role. Idempotent. The Admin role cannot be granted through this API.
	 *
	 * @throws com.kpi.studentcouncil.crm.shared.error.NotFoundException    unknown or archived role, or unknown user
	 * @throws com.kpi.studentcouncil.crm.shared.error.BusinessRuleException {@code ADMIN_ROLE_RESERVED}
	 */
	void grantRole(UUID userId, UUID roleId);

	/** Revokes a role; no-op when it is not granted. The Admin role cannot be revoked through this API. */
	void revokeRole(UUID userId, UUID roleId);

}
