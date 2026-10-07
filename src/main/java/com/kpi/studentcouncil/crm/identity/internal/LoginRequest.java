package com.kpi.studentcouncil.crm.identity.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Sign-in credentials. {@code toString} never prints the password. */
public record LoginRequest(@NotBlank @Size(max = 128) String login, @NotBlank @Size(max = 256) String password) {

	@Override
	public String toString() {
		return "LoginRequest[login=" + login + ", password=***]";
	}

}
