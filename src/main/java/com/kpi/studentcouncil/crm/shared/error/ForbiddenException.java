package com.kpi.studentcouncil.crm.shared.error;

import org.springframework.http.HttpStatus;

/** Operation not allowed for the current actor for a domain reason (403). Missing permissions use Spring Security. */
public class ForbiddenException extends DomainException {

	public ForbiddenException(String code, Object... args) {
		super(code, HttpStatus.FORBIDDEN, args);
	}

}
