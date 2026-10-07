package com.kpi.studentcouncil.crm.identity.internal;

import java.util.List;
import java.util.UUID;

/**
 * The signed-in user. {@code permissions} lists effective permission keys (empty until the access module exists).
 */
public record MeResponse(UUID id, String login, String locale, List<String> permissions, boolean mustChangePassword) {

	static MeResponse of(CrmUserDetails user) {
		return new MeResponse(user.id(), user.getUsername(), user.locale(), user.permissions().stream().sorted().toList(),
				user.mustChangePassword());
	}

}
