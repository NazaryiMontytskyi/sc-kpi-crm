package com.kpi.studentcouncil.crm.identity.internal;

import java.util.UUID;

import org.springframework.context.annotation.Primary;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.kpi.studentcouncil.crm.audit.ActorProvider;

/**
 * The authenticated user as audit actor (replaces the audit module's system-actor fallback; {@code @Primary} makes
 * the choice independent of bean registration order). Falls back to the system actor when nobody is signed in.
 */
@Component
@Primary
class SecurityContextActor implements ActorProvider {

	@Override
	public UUID currentActorId() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null && auth.getPrincipal() instanceof CrmUserDetails user) {
			return user.id();
		}
		return SYSTEM_ACTOR;
	}

}
