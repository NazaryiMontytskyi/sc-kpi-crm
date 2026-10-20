package com.kpi.studentcouncil.crm.tasks.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.kpi.studentcouncil.crm.access.AccessService;
import com.kpi.studentcouncil.crm.access.Permission;
import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.shared.error.ForbiddenException;
import com.kpi.studentcouncil.crm.tasks.CommentTargetType;

class CommentPolicyTest {

	private final AccessService access = mock(AccessService.class);
	private final ActorProvider actors = mock(ActorProvider.class);
	private final CommentPolicy policy = new CommentPolicy(new AdminCheck(access), actors);

	private final UUID author = UUID.randomUUID();
	private final UUID stranger = UUID.randomUUID();
	private final Comment comment = new Comment(CommentTargetType.TASK, UUID.randomUUID(), Map.of("type", "doc"),
			Set.of(), List.of());

	CommentPolicyTest() {
		ReflectionTestUtils.setField(comment, "createdBy", author);
	}

	@Test
	void pureRuleAllowsTheAuthorAndAnAdminOnly() {
		assertThat(CommentPolicy.canModify(author, author, false)).isTrue();
		assertThat(CommentPolicy.canModify(stranger, author, true)).isTrue();
		assertThat(CommentPolicy.canModify(stranger, author, false)).isFalse();
		assertThat(CommentPolicy.canModify(null, author, false)).isFalse();
		assertThat(CommentPolicy.canModify(ActorProvider.SYSTEM_ACTOR, ActorProvider.SYSTEM_ACTOR, false)).isFalse();
	}

	@Test
	void theAuthorMayModify() {
		when(actors.currentActorId()).thenReturn(author);
		when(access.effectivePermissions(author)).thenReturn(Set.of("tasks.view"));
		assertThatCode(() -> policy.assertCanModify(comment)).doesNotThrowAnyException();
	}

	@Test
	void anAdminMayModifySomeoneElsesComment() {
		when(actors.currentActorId()).thenReturn(stranger);
		when(access.effectivePermissions(stranger)).thenReturn(Permission.allKeys());
		assertThatCode(() -> policy.assertCanModify(comment)).doesNotThrowAnyException();
	}

	@Test
	void anotherUserGets403EvenWithTasksEditAny() {
		when(actors.currentActorId()).thenReturn(stranger);
		when(access.effectivePermissions(stranger)).thenReturn(Set.of("tasks.view", "tasks.edit.any"));
		assertThatThrownBy(() -> policy.assertCanModify(comment)).isInstanceOfSatisfying(ForbiddenException.class, e -> {
			assertThat(e.getCode()).isEqualTo("COMMENT_EDIT_FORBIDDEN");
			assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
		});
	}

}
