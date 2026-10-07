package com.kpi.studentcouncil.crm.shared.error;

import org.springframework.http.HttpStatus;

/** Resource does not exist (404). */
public class NotFoundException extends DomainException {

	public NotFoundException(String entityType, Object id) {
		super("NOT_FOUND", HttpStatus.NOT_FOUND, entityType, id);
	}

}
