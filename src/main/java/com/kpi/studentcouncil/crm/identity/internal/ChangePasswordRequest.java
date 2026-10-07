package com.kpi.studentcouncil.crm.identity.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Password change. {@code toString} never prints the passwords. */
public record ChangePasswordRequest(@NotBlank @Size(max = 256) String currentPassword,
		@NotBlank @Size(max = 256) String newPassword) {

	@Override
	public String toString() {
		return "ChangePasswordRequest[***]";
	}

}
