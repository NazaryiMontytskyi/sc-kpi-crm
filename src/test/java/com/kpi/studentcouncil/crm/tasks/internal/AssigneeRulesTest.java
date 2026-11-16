package com.kpi.studentcouncil.crm.tasks.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.kpi.studentcouncil.crm.access.Permission;
import com.kpi.studentcouncil.crm.audit.ActorProvider;

/** AS-3: who may add or remove an assignee. */
class AssigneeRulesTest {

	private final UUID creator = UUID.randomUUID();
	private final UUID someone = UUID.randomUUID();
	private final UUID other = UUID.randomUUID();

	private static final Set<String> CREATE = Set.of("tasks.view", "tasks.create");
	private static final Set<String> EDIT_ANY = Set.of("tasks.view", "tasks.edit.any");
	private static final Set<String> ASSIGN = Set.of("tasks.view", "tasks.assign");

	@Test
	void theCreatorWithTasksCreateMaySelfAssignButNotAssignOthers() {
		assertThat(AssigneeRules.canManage(creator, creator, creator, CREATE)).isTrue();
		assertThat(AssigneeRules.canManage(creator, other, creator, CREATE)).isFalse();
	}

	@Test
	void theCreatorWithoutTasksCreateMayNotSelfAssign() {
		assertThat(AssigneeRules.canManage(creator, creator, creator, Set.of("tasks.view"))).isFalse();
	}

	@Test
	void aNonCreatorWithTasksCreateMayNotSelfAssign() {
		assertThat(AssigneeRules.canManage(someone, someone, creator, CREATE)).isFalse();
	}

	@Test
	void tasksEditAnyAllowsSelfAssignOnAnyTaskButNotOthers() {
		assertThat(AssigneeRules.canManage(someone, someone, creator, EDIT_ANY)).isTrue();
		assertThat(AssigneeRules.canManage(someone, other, creator, EDIT_ANY)).isFalse();
	}

	@Test
	void tasksAssignAllowsAnybodyIncludingOneself() {
		assertThat(AssigneeRules.canManage(someone, other, creator, ASSIGN)).isTrue();
		assertThat(AssigneeRules.canManage(someone, someone, creator, ASSIGN)).isTrue();
		assertThat(AssigneeRules.canManage(someone, creator, creator, ASSIGN)).isTrue();
	}

	@Test
	void anAdminHoldsEveryKeyAndMayAssignAnybody() {
		assertThat(AssigneeRules.canManage(someone, other, creator, Permission.allKeys())).isTrue();
	}

	@Test
	void theSystemActorAndMissingIdsNeverMatch() {
		assertThat(AssigneeRules.canManage(ActorProvider.SYSTEM_ACTOR, other, creator, Permission.allKeys())).isFalse();
		assertThat(AssigneeRules.canManage(null, other, creator, Permission.allKeys())).isFalse();
		assertThat(AssigneeRules.canManage(someone, null, creator, Permission.allKeys())).isFalse();
	}

}
