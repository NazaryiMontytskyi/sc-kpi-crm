package com.kpi.studentcouncil.crm.access;

/** Groups of the permission catalog as listed in CONTEXT.md section 5.2, in display order. */
public enum PermissionGroup {

	MEMBERS("members"),
	ORG("org"),
	ROLES("roles"),
	TASKS("tasks"),
	RESOLUTIONS("resolutions"),
	KNOWLEDGE_BASE("kb"),
	MEETINGS_VOTING("voting"),
	OTHER("other");

	private final String code;

	PermissionGroup(String code) {
		this.code = code;
	}

	/** Stable group code used in the API and in message keys ({@code permission.group.<code>}). */
	public String code() {
		return code;
	}

}
