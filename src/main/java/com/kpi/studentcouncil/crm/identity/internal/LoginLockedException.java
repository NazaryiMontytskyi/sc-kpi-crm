package com.kpi.studentcouncil.crm.identity.internal;

import org.springframework.http.HttpStatus;

import com.kpi.studentcouncil.crm.shared.error.DomainException;

/** Too many failed attempts for a login+IP pair; sign-in is temporarily refused (429, code LOGIN_LOCKED). */
public class LoginLockedException extends DomainException {

	private final long retryAfterSeconds;

	public LoginLockedException(long retryAfterSeconds) {
		super("LOGIN_LOCKED", HttpStatus.TOO_MANY_REQUESTS, String.valueOf(Math.max(1, (retryAfterSeconds + 59) / 60)));
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}

}
