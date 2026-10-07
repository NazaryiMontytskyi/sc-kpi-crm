package com.kpi.studentcouncil.crm.identity.internal;

/** Account state. Only {@link #ACTIVE} accounts can sign in. */
public enum UserStatus {
	ACTIVE,
	ARCHIVED,
	BLOCKED
}
