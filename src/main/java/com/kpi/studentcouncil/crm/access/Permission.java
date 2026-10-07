package com.kpi.studentcouncil.crm.access;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The fixed, code-defined permission catalog (CONTEXT.md section 5.2). The {@link #key()} strings are part of the
 * API contract and of stored role data: never rename a key, only add new ones.
 */
public enum Permission {

	MEMBERS_VIEW("members.view", PermissionGroup.MEMBERS),
	MEMBERS_CREATE("members.create", PermissionGroup.MEMBERS),
	MEMBERS_EDIT("members.edit", PermissionGroup.MEMBERS),
	MEMBERS_ARCHIVE("members.archive", PermissionGroup.MEMBERS),
	MEMBERS_ACTIVITY_SET("members.activity.set", PermissionGroup.MEMBERS),

	ORG_VIEW("org.view", PermissionGroup.ORG),
	ORG_MANAGE("org.manage", PermissionGroup.ORG),
	POSITIONS_MANAGE("positions.manage", PermissionGroup.ORG),
	POSITIONS_APPOINT("positions.appoint", PermissionGroup.ORG),

	ROLES_MANAGE("roles.manage", PermissionGroup.ROLES),
	ROLES_ASSIGN("roles.assign", PermissionGroup.ROLES),

	TASKS_VIEW("tasks.view", PermissionGroup.TASKS),
	TASKS_CREATE("tasks.create", PermissionGroup.TASKS),
	TASKS_EDIT_ANY("tasks.edit.any", PermissionGroup.TASKS),
	TASKS_ARCHIVE("tasks.archive", PermissionGroup.TASKS),
	SPACES_MANAGE("spaces.manage", PermissionGroup.TASKS),

	RESOLUTIONS_CREATE("resolutions.create", PermissionGroup.RESOLUTIONS),
	RESOLUTIONS_REDIRECT("resolutions.redirect", PermissionGroup.RESOLUTIONS),

	KB_VIEW("kb.view", PermissionGroup.KNOWLEDGE_BASE),
	KB_EDIT("kb.edit", PermissionGroup.KNOWLEDGE_BASE),
	KB_ARCHIVE("kb.archive", PermissionGroup.KNOWLEDGE_BASE),
	KB_RESTRICTED_VIEW("kb.restricted.view", PermissionGroup.KNOWLEDGE_BASE),
	ONBOARDING_MANAGE("onboarding.manage", PermissionGroup.KNOWLEDGE_BASE),

	MEETINGS_MANAGE("meetings.manage", PermissionGroup.MEETINGS_VOTING),
	POLLS_CREATE("polls.create", PermissionGroup.MEETINGS_VOTING),
	VOTING_CAST("voting.cast", PermissionGroup.MEETINGS_VOTING),

	HANDOVER_PERFORM("handover.perform", PermissionGroup.OTHER),
	DASHBOARD_VIEW("dashboard.view", PermissionGroup.OTHER),
	AUDIT_VIEW("audit.view", PermissionGroup.OTHER),
	SETTINGS_MANAGE("settings.manage", PermissionGroup.OTHER);

	private static final Set<String> ALL_KEYS = Arrays.stream(values()).map(Permission::key)
			.collect(Collectors.toUnmodifiableSet());

	private final String key;

	private final PermissionGroup group;

	Permission(String key, PermissionGroup group) {
		this.key = key;
		this.group = group;
	}

	/** Catalog group (CONTEXT.md section 5.2). */
	public PermissionGroup group() {
		return group;
	}

	/** Stable string key, also the Spring Security authority, e.g. {@code members.activity.set}. */
	public String key() {
		return key;
	}

	/** All permission keys. */
	public static Set<String> allKeys() {
		return ALL_KEYS;
	}

	public static boolean isKnown(String key) {
		return ALL_KEYS.contains(key);
	}

	public static Optional<Permission> fromKey(String key) {
		return Arrays.stream(values()).filter(p -> p.key.equals(key)).findFirst();
	}

}
