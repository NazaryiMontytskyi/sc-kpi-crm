package com.kpi.studentcouncil.crm.search.internal;

import com.kpi.studentcouncil.crm.access.Permission;

/** Kinds of indexed entities; each is visible to callers holding its view permission. */
enum SearchType {

	TASK(Permission.TASKS_VIEW),
	/**
	 * Legacy: resolutions are no longer indexed (INC-080, RR-5); the value stays accepted in {@code types} so existing
	 * clients keep working, and its group is always empty. Removed with resolutions (INC-081).
	 */
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

	/** Whether documents of this type exist in the index; a group of a type that is not indexed is always empty. */
	boolean indexed() {
		return this != RESOLUTION;
	}

}
