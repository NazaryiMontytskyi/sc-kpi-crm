package com.kpi.studentcouncil.crm.identity.internal;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Loads accounts by (case-insensitive) login for the password authentication provider. */
@Service
class CrmUserDetailsService implements UserDetailsService {

	private final UserRepository users;

	CrmUserDetailsService(UserRepository users) {
		this.users = users;
	}

	@Override
	public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
		return users.findByLogin(User.normalizeLogin(login)).map(CrmUserDetails::forAuthentication)
				.orElseThrow(() -> new UsernameNotFoundException("unknown login"));
	}

}
