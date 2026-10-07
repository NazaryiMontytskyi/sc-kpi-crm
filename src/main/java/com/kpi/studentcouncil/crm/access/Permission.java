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

	MEMBERS_VIEW("members.view"),
	MEMBERS_CREATE("members.create"),
	MEMBERS_EDIT("members.edit"),
	MEMBERS_ARCHIVE("members.archive"),
	MEMBERS_ACTIVITY_SET("members.activity.set"),

	ORG_VIEW("org.view"),
	ORG_MANAGE("org.manage"),
	POSITIONS_MANAGE("positions.manage"),
	POSITIONS_APPOINT("positions.appoint"),

	ROLES_MANAGE("roles.manage"),
	ROLES_ASSIGN("roles.assign"),

	TASKS_VIEW("tasks.view"),
	TASKS_CREATE("tasks.create"),
	TASKS_EDIT_ANY("tasks.edit.any"),
	TASKS_ARCHIVE("tasks.archive"),
	SPACES_MANAGE("spaces.manage"),

	RESOLUTIONS_CREATE("resolutions.create"),
	RESOLUTIONS_REDIRECT("resolutions.redirect"),

	KB_VIEW("kb.view"),
	KB_EDIT("kb.edit"),
	KB_ARCHIVE("kb.archive"),
	KB_RESTRICTED_VIEW("kb.restricted.view"),
	ONBOARDING_MANAGE("onboarding.manage"),

	MEETINGS_MANAGE("meetings.manage"),
	POLLS_CREATE("polls.create"),
	VOTING_CAST("voting.cast"),

	HANDOVER_PERFORM("handover.perform"),
	DASHBOARD_VIEW("dashboard.view"),
	AUDIT_VIEW("audit.view"),
	SETTINGS_MANAGE("settings.manage");

	private static final Set<String> ALL_KEYS = Arrays.stream(values()).map(Permission::key)
			.collect(Collectors.toUnmodifiableSet());

	private final String key;

	Permission(String key) {
		this.key = key;
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
