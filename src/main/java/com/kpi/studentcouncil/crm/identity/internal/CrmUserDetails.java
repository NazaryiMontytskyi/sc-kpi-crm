package com.kpi.studentcouncil.crm.identity.internal;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Session principal. Account status is verified by the authentication provider after the password check and again
 * on every request (see SessionUserRefreshFilter), so the Spring account flags below are always "true".
 */
public final class CrmUserDetails implements UserDetails {

	private static final long serialVersionUID = 1L;

	private final UUID id;

	private final String login;

	private final String passwordHash;

	private final String locale;

	private final boolean mustChangePassword;

	private final boolean canSignIn;

	private final Set<String> permissions;

	private CrmUserDetails(UUID id, String login, String passwordHash, String locale, boolean mustChangePassword,
			boolean canSignIn, Set<String> permissions) {
		this.id = id;
		this.login = login;
		this.passwordHash = passwordHash;
		this.locale = locale;
		this.mustChangePassword = mustChangePassword;
		this.canSignIn = canSignIn;
		this.permissions = Set.copyOf(permissions);
	}

	/** Principal used during authentication; carries the password hash. */
	static CrmUserDetails forAuthentication(User user) {
		return new CrmUserDetails(user.getId(), user.getLogin(), user.getPasswordHash(), user.getLocale(),
				user.isMustChangePassword(), user.canSignIn(), Set.of());
	}

	/** Session-safe copy: no password hash, current flags and permissions. */
	static CrmUserDetails forSession(User user, Set<String> permissions) {
		return new CrmUserDetails(user.getId(), user.getLogin(), "", user.getLocale(), user.isMustChangePassword(),
				user.canSignIn(), permissions);
	}

	public UUID id() {
		return id;
	}

	public String locale() {
		return locale;
	}

	public boolean mustChangePassword() {
		return mustChangePassword;
	}

	public Set<String> permissions() {
		return permissions;
	}

	boolean canSignIn() {
		return canSignIn;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return permissions.stream().map(SimpleGrantedAuthority::new).toList();
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return login;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}

	@Override
	public String toString() {
		return "CrmUserDetails[id=" + id + ", login=" + login + "]";
	}

}
