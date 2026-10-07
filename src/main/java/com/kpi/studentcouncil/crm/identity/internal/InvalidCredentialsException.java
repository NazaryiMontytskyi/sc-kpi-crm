package com.kpi.studentcouncil.crm.identity.internal;

import org.springframework.http.HttpStatus;

import com.kpi.studentcouncil.crm.shared.error.DomainException;

/**
 * Sign-in failed. One generic response for unknown login, wrong password, blocked and archived accounts, so the
 * response never reveals whether the login exists (401, code INVALID_CREDENTIALS).
 */
public class InvalidCredentialsException extends DomainException {

	public InvalidCredentialsException() {
		super("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED);
	}

}
