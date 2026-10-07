package com.kpi.studentcouncil.crm.shared.error;

import org.springframework.http.HttpStatus;

/** State conflict, e.g. duplicate or already-archived (409). */
public class ConflictException extends DomainException {

	public ConflictException(String code, Object... args) {
		super(code, HttpStatus.CONFLICT, args);
	}

}
