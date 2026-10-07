package com.kpi.studentcouncil.crm.identity.internal;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableEntity;

/** A system account. The password is stored only as a hash; it must never be logged, audited or serialised. */
@Entity
@Table(name = "users")
public class User extends ArchivableEntity {

	static final Set<String> LOCALES = Set.of("uk", "en");

	@Column(name = "login", nullable = false, updatable = false, length = 128)
	private String login;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 16)
	private UserStatus status;

	@Column(name = "locale", nullable = false, length = 2)
	private String locale;

	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	@Column(name = "must_change_password", nullable = false)
	private boolean mustChangePassword;

	@Column(name = "password_changed_at")
	private Instant passwordChangedAt;

	protected User() {
	}

	/** Creates an ACTIVE account. {@code login} must already be normalised (see {@link #normalizeLogin}). */
	static User create(String login, String passwordHash, String locale, boolean mustChangePassword) {
		User user = new User();
		user.login = login;
		user.passwordHash = passwordHash;
		user.status = UserStatus.ACTIVE;
		user.locale = requireLocale(locale);
		user.mustChangePassword = mustChangePassword;
		return user;
	}

	/** Logins are case-insensitive and trimmed; the stored form is the normalised one. */
	static String normalizeLogin(String login) {
		return login == null ? "" : login.strip().toLowerCase(java.util.Locale.ROOT);
	}

	static String requireLocale(String locale) {
		if (locale == null || !LOCALES.contains(locale)) {
			throw new IllegalArgumentException("Unsupported locale");
		}
		return locale;
	}

	public String getLogin() {
		return login;
	}

	String getPasswordHash() {
		return passwordHash;
	}

	public UserStatus getStatus() {
		return status;
	}

	public String getLocale() {
		return locale;
	}

	public Instant getLastLoginAt() {
		return lastLoginAt;
	}

	public boolean isMustChangePassword() {
		return mustChangePassword;
	}

	public Instant getPasswordChangedAt() {
		return passwordChangedAt;
	}

	boolean canSignIn() {
		return status == UserStatus.ACTIVE;
	}

	void markLoggedIn(Instant at) {
		this.lastLoginAt = at;
	}

	void changePassword(String newHash, Instant at) {
		this.passwordHash = newHash;
		this.mustChangePassword = false;
		this.passwordChangedAt = at;
	}

	void changeLocale(String newLocale) {
		this.locale = requireLocale(newLocale);
	}

	void block() {
		if (status == UserStatus.ACTIVE) {
			this.status = UserStatus.BLOCKED;
		}
	}

	void unblock() {
		if (status == UserStatus.BLOCKED) {
			this.status = UserStatus.ACTIVE;
		}
	}

	@Override
	public void archive(UUID actorId, Instant at) {
		super.archive(actorId, at);
		this.status = UserStatus.ARCHIVED;
	}

	@Override
	public void restore() {
		super.restore();
		this.status = UserStatus.ACTIVE;
	}

	/** Audit-safe view: never contains the password hash. */
	java.util.Map<String, Object> auditSnapshot() {
		java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
		m.put("login", login);
		m.put("status", status.name());
		m.put("locale", locale);
		m.put("mustChangePassword", mustChangePassword);
		return m;
	}

	@Override
	public String toString() {
		return "User[id=" + getId() + ", login=" + login + ", status=" + status + "]";
	}

}
