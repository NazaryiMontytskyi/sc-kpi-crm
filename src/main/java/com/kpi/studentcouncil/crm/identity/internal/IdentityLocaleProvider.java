package com.kpi.studentcouncil.crm.identity.internal;

import java.util.Locale;
import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.kpi.studentcouncil.crm.shared.web.UserLocaleProvider;

/** Supplies the signed-in user's saved locale to the kernel locale resolver (takes precedence over Accept-Language). */
@Component
class IdentityLocaleProvider implements UserLocaleProvider {

	@Override
	public Optional<Locale> currentUserLocale() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null && auth.getPrincipal() instanceof CrmUserDetails user) {
			return Optional.of(Locale.of(user.locale()));
		}
		return Optional.empty();
	}

}
