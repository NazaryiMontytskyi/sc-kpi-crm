package com.kpi.studentcouncil.crm.tasks.internal;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.kpi.studentcouncil.crm.access.AccessService;
import com.kpi.studentcouncil.crm.access.Permission;

/**
 * THE single place of the tasks module that recognises an {@code Admin}. The Admin role is hardcoded and implies every
 * permission of the catalog; the access module has no dedicated "is admin" query yet, so Admin is recognised by
 * holding all of them. Replace the body with {@code AccessService.isAdmin} once it exists.
 */
@Component
class AdminCheck {

	private final AccessService access;

	AdminCheck(AccessService access) {
		this.access = access;
	}

	boolean isAdmin(UUID userId) {
		return access.effectivePermissions(userId).containsAll(Permission.allKeys());
	}

}
