package com.kpi.studentcouncil.crm.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class PermissionTest {

	/** Mirror of the CONTEXT.md section 5.2 table; adding a permission must be a conscious change here too. */
	private static final List<String> EXPECTED = List.of("members.view", "members.create", "members.edit",
			"members.archive", "members.activity.set", "org.view", "org.manage", "positions.manage",
			"positions.appoint", "roles.manage", "roles.assign", "tasks.view", "tasks.create", "tasks.edit.any",
			"tasks.archive", "spaces.manage", "resolutions.create", "resolutions.redirect", "kb.view", "kb.edit",
			"kb.archive", "kb.restricted.view", "onboarding.manage", "meetings.manage", "polls.create", "voting.cast",
			"handover.perform", "dashboard.view", "audit.view", "settings.manage");

	@Test
	void catalogMatchesTheSpecificationExactly() {
		assertThat(Arrays.stream(Permission.values()).map(Permission::key)).containsExactlyElementsOf(EXPECTED);
		assertThat(Permission.allKeys()).containsExactlyInAnyOrderElementsOf(EXPECTED);
	}

	@Test
	void keysAreUniqueAndLookupWorks() {
		assertThat(Permission.allKeys()).hasSameSizeAs(Permission.values());
		assertThat(Permission.fromKey("members.activity.set")).contains(Permission.MEMBERS_ACTIVITY_SET);
		assertThat(Permission.fromKey("nope")).isEmpty();
		assertThat(Permission.isKnown("voting.cast")).isTrue();
	}

}
