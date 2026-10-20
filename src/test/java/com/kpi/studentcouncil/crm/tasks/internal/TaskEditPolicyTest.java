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
	private final TaskEditPolicy policy = new TaskEditPolicy(access, actors);

	private final UUID author = UUID.randomUUID();
	private final UUID stranger = UUID.randomUUID();
	private final Task task = new Task(UUID.randomUUID(), "T", null, UUID.randomUUID(), TaskPriority.NORMAL, null,
			Set.of(), java.util.List.of(), java.util.Map.of());

	TaskEditPolicyTest() {
		ReflectionTestUtils.setField(task, "createdBy", author);
	}

	@Test
	void pureRuleAllowsTheAuthorAndAnAdminOnly() {
		assertThat(TaskEditPolicy.canEdit(author, author, false)).isTrue();
		assertThat(TaskEditPolicy.canEdit(stranger, author, true)).isTrue();
		assertThat(TaskEditPolicy.canEdit(stranger, author, false)).isFalse();
		assertThat(TaskEditPolicy.canEdit(null, author, false)).isFalse();
		assertThat(TaskEditPolicy.canEdit(ActorProvider.SYSTEM_ACTOR, ActorProvider.SYSTEM_ACTOR, false)).isFalse();
	}

	@Test
	void theAuthorMayEdit() {
		when(actors.currentActorId()).thenReturn(author);
		when(access.effectivePermissions(author)).thenReturn(Set.of("tasks.view", "tasks.create"));
		assertThatCode(() -> policy.assertCanEdit(task)).doesNotThrowAnyException();
		assertThatCode(() -> policy.assertCanChangeStatus(task)).doesNotThrowAnyException();
	}

	@Test
	void anAdminMayEditSomeoneElsesTask() {
		when(actors.currentActorId()).thenReturn(stranger);
		when(access.effectivePermissions(stranger)).thenReturn(Permission.allKeys());
		assertThatCode(() -> policy.assertCanEdit(task)).doesNotThrowAnyException();
	}

	@Test
	void anotherUserGets403EvenWithTasksEditAny() {
		when(actors.currentActorId()).thenReturn(stranger);
		when(access.effectivePermissions(stranger)).thenReturn(Set.of("tasks.view", "tasks.edit.any", "tasks.archive"));
		assertThatThrownBy(() -> policy.assertCanEdit(task)).isInstanceOfSatisfying(ForbiddenException.class, e -> {
			assertThat(e.getCode()).isEqualTo("TASK_EDIT_FORBIDDEN");
			assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
		});
		assertThatThrownBy(() -> policy.assertCanChangeStatus(task)).isInstanceOf(ForbiddenException.class);
	}

}
