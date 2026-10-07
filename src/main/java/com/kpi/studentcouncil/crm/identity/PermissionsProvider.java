package com.kpi.studentcouncil.crm.identity;

import java.util.Set;
import java.util.UUID;

/**
 * Extension point: the access module (INC-005) implements it to supply the effective permission keys of a user.
 * Identity turns them into {@code GrantedAuthority} values of the session principal and lists them in
 * {@code GET /api/v1/auth/me}. Until a bean exists, every user has an empty permission set.
 */
public interface PermissionsProvider {

	Set<String> permissionsOf(UUID userId);

}
