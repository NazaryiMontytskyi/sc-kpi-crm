package com.kpi.studentcouncil.crm.tasks.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.kpi.studentcouncil.crm.access.AccessService;
import com.kpi.studentcouncil.crm.access.Permission;
import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.shared.error.ForbiddenException;

class TaskEditPolicyTest {

	private final AccessService access = mock(AccessService.class);
	private final ActorProvider actors = mock(ActorProvider.class);
	private final TaskAssigneeRepository assignees = mock(TaskAssigneeRepository.class);
	private final TaskEditPolicy policy = new TaskEditPolicy(new AdminCheck(access), access, assignees, actors);

	private final UUID author = UUID.randomUUID();
	private final UUID stranger = UUID.randomUUID();
	private final UUID assignee = UUID.randomUUID();
	private final Task task = new Task(UUID.randomUUID(), author, java.time.Instant.now(), "T", null,
			UUID.randomUUID(), TaskPriority.NORMAL, null, Set.of(), java.util.List.of(), java.util.Map.of());

	{
		ReflectionTestUtils.setField(task, "id", UUID.randomUUID());
	}

	private void actingAs(UUID user, Set<String> permissions) {
		when(actors.currentActorId()).thenReturn(user);
		when(access.effectivePermissions(user)).thenReturn(permissions);
	}

	// ---- edit (Q7): creator or Admin ----

	@Test
	void pureEditRuleAllowsTheCreatorAndAnAdminOnly() {
		assertThat(TaskEditPolicy.canEdit(author, author, false)).isTrue();
		assertThat(TaskEditPolicy.canEdit(stranger, author, true)).isTrue();
		assertThat(TaskEditPolicy.canEdit(stranger, author, false)).isFalse();
		assertThat(TaskEditPolicy.canEdit(null, author, false)).isFalse();
		assertThat(TaskEditPolicy.canEdit(ActorProvider.SYSTEM_ACTOR, ActorProvider.SYSTEM_ACTOR, false)).isFalse();
	}

	@Test
	void theCreatorMayEditAndChangeTheStatus() {
		actingAs(author, Set.of("tasks.view", "tasks.create"));
		assertThatCode(() -> policy.assertCanEdit(task)).doesNotThrowAnyException();
		assertThatCode(() -> policy.assertCanChangeStatus(task)).doesNotThrowAnyException();
	}

	@Test
	void anAdminMayEditSomeoneElsesTask() {
		actingAs(stranger, Permission.allKeys());
		assertThatCode(() -> policy.assertCanEdit(task)).doesNotThrowAnyException();
		assertThatCode(() -> policy.assertCanChangeStatus(task)).doesNotThrowAnyException();
	}

	@Test
	void tasksEditAnyDoesNotAllowEditingFieldsButAllowsTheStatus() {
		actingAs(stranger, Set.of("tasks.view", "tasks.edit.any", "tasks.archive"));
		assertThatThrownBy(() -> policy.assertCanEdit(task)).isInstanceOfSatisfying(ForbiddenException.class, e -> {
			assertThat(e.getCode()).isEqualTo("TASK_EDIT_FORBIDDEN");
			assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
		});
		assertThatCode(() -> policy.assertCanChangeStatus(task)).doesNotThrowAnyException();
	}

	// ---- contribute (AS-5): creator, active assignee, tasks.edit.any or Admin ----

	@Test
	void pureContributeRule() {
		assertThat(TaskEditPolicy.canContribute(author, author, false, false, false)).isTrue();
		assertThat(TaskEditPolicy.canContribute(assignee, author, true, false, false)).isTrue();
		assertThat(TaskEditPolicy.canContribute(stranger, author, false, true, false)).isTrue();
		assertThat(TaskEditPolicy.canContribute(stranger, author, false, false, true)).isTrue();
		assertThat(TaskEditPolicy.canContribute(stranger, author, false, false, false)).isFalse();
		assertThat(TaskEditPolicy.canContribute(null, author, true, true, true)).isFalse();
		assertThat(TaskEditPolicy.canContribute(ActorProvider.SYSTEM_ACTOR, author, true, true, true)).isFalse();
	}

	@Test
	void anActiveAssigneeMayChangeTheStatusButNotEditTheFields() {
		actingAs(assignee, Set.of("tasks.view"));
		when(assignees.existsByTaskIdAndUserIdAndRemovedAtIsNull(task.getId(), assignee)).thenReturn(true);
		assertThatCode(() -> policy.assertCanChangeStatus(task)).doesNotThrowAnyException();
		assertThatCode(() -> policy.assertCanContribute(task)).doesNotThrowAnyException();
		assertThatThrownBy(() -> policy.assertCanEdit(task)).isInstanceOf(ForbiddenException.class);
	}

	@Test
	void aNonCreatorNonAssigneeWithoutTasksEditAnyCannotChangeTheStatus() {
		actingAs(stranger, Set.of("tasks.view", "tasks.create", "tasks.assign", "tasks.archive"));
		when(assignees.existsByTaskIdAndUserIdAndRemovedAtIsNull(task.getId(), stranger)).thenReturn(false);
		assertThatThrownBy(() -> policy.assertCanChangeStatus(task)).isInstanceOfSatisfying(ForbiddenException.class,
				e -> assertThat(e.getCode()).isEqualTo("TASK_CONTRIBUTE_FORBIDDEN"));
	}

	@Test
	void aRemovedAssigneeCannotChangeTheStatusAnymore() {
		actingAs(assignee, Set.of("tasks.view"));
		when(assignees.existsByTaskIdAndUserIdAndRemovedAtIsNull(task.getId(), assignee)).thenReturn(false);
		assertThatThrownBy(() -> policy.assertCanChangeStatus(task)).isInstanceOf(ForbiddenException.class);
	}

	// ---- assignees (AS-3) ----

	@Test
	void assigneeManagementFollowsAssigneeRules() {
		actingAs(author, Set.of("tasks.view", "tasks.create"));
		assertThatCode(() -> policy.assertCanManageAssignee(task, author)).doesNotThrowAnyException();
		assertThatThrownBy(() -> policy.assertCanManageAssignee(task, stranger))
				.isInstanceOfSatisfying(ForbiddenException.class,
						e -> assertThat(e.getCode()).isEqualTo("TASK_ASSIGN_FORBIDDEN"));

		actingAs(stranger, Set.of("tasks.view", "tasks.assign"));
		assertThatCode(() -> policy.assertCanManageAssignee(task, assignee)).doesNotThrowAnyException();
	}

}
