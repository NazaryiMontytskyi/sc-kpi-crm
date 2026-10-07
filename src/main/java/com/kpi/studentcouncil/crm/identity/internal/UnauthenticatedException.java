package com.kpi.studentcouncil.crm.identity.internal;

import org.springframework.http.HttpStatus;

import com.kpi.studentcouncil.crm.shared.error.DomainException;

/** No identity-module principal in the security context (401, code UNAUTHORIZED). */
class UnauthenticatedException extends DomainException {

	UnauthenticatedException() {
		super("UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
	}

}
