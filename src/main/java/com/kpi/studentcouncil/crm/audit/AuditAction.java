package com.kpi.studentcouncil.crm.audit;

/** Fixed set of audited actions (CONTEXT.md 5.10). Stored by name; never rename existing values. */
public enum AuditAction {
	CREATE,
	UPDATE,
	ARCHIVE,
	RESTORE,
	HARD_DELETE,
	LOGIN,
	LOGIN_FAILED,
	LOGOUT,
	PASSWORD_CHANGE,
	ROLE_CHANGE,
	PERMISSION_CHANGE,
	APPOINT,
	DISMISS,
	VOTE,
	VOTE_CHANGE,
	HANDOVER
}
