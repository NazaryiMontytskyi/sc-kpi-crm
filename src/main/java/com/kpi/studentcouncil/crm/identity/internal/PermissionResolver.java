package com.kpi.studentcouncil.crm.identity.internal;

import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.kpi.studentcouncil.crm.identity.PermissionsProvider;

/** Effective permission keys of a user: the union over all {@link PermissionsProvider} beans (none until INC-005). */
@Component
class PermissionResolver {

	private final ObjectProvider<PermissionsProvider> providers;

	PermissionResolver(ObjectProvider<PermissionsProvider> providers) {
		this.providers = providers;
	}

	Set<String> permissionsOf(UUID userId) {
		return providers.orderedStream().flatMap(p -> p.permissionsOf(userId).stream())
				.collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

}
