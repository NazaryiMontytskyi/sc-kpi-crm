package com.kpi.studentcouncil.crm.shared.error;

import org.springframework.http.HttpStatus;

/**
 * Base of all business errors. {@code code} is a stable, machine-readable identifier (part of the API contract);
 * the localized title/detail are looked up as {@code problem.<code>.title} / {@code problem.<code>.detail}.
 */
public abstract class DomainException extends RuntimeException {

	private final String code;
	private final HttpStatus status;
	private final transient Object[] args;

	protected DomainException(String code, HttpStatus status, Object... args) {
		super(code);
		this.code = code;
		this.status = status;
		this.args = args;
	}

	public String getCode() {
		return code;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public Object[] getArgs() {
		return args;
	}

}
