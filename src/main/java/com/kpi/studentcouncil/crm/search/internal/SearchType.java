package com.kpi.studentcouncil.crm.search.internal;

import com.kpi.studentcouncil.crm.access.Permission;

/** Kinds of indexed entities; each is visible to callers holding its view permission. */
enum SearchType {

	TASK(Permission.TASKS_VIEW),
	RESOLUTION(Permission.TASKS_VIEW),
	ARTICLE(Permission.KB_VIEW),
	PERSON(Permission.MEMBERS_VIEW),
	ORG_UNIT(Permission.ORG_VIEW);

	private final Permission viewPermission;

	SearchType(Permission viewPermission) {
		this.viewPermission = viewPermission;
	}

	String viewPermissionKey() {
		return viewPermission.key();
	}

}
