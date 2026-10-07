package com.kpi.studentcouncil.crm.access.internal;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.kpi.studentcouncil.crm.audit.ActorProvider;

/** SpEL helper for {@code @PreAuthorize}: is the signed-in user the given user. */
@Component("permissionGuard")
public class PermissionGuard {

	private final ActorProvider actors;

	PermissionGuard(ActorProvider actors) {
		this.actors = actors;
	}

	public boolean isSelf(UUID userId) {
		UUID actor = actors.currentActorId();
		return !ActorProvider.SYSTEM_ACTOR.equals(actor) && actor.equals(userId);
	}

}
