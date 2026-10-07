package com.kpi.studentcouncil.crm.shared.error;

import org.springframework.http.HttpStatus;

/** A domain rule was violated, e.g. invalid resolution transition (422). */
public class BusinessRuleException extends DomainException {

	public BusinessRuleException(String code, Object... args) {
		super(code, HttpStatus.UNPROCESSABLE_CONTENT, args);
	}

}
