package com.kpi.studentcouncil.crm.tasks.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.kpi.studentcouncil.crm.access.AccessService;
import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.audit.AuditAction;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.files.FileService;
import com.kpi.studentcouncil.crm.identity.UserDirectory;
import com.kpi.studentcouncil.crm.shared.error.DomainException;
import com.kpi.studentcouncil.crm.shared.error.ForbiddenException;
import com.kpi.studentcouncil.crm.shared.error.NotFoundException;
import com.kpi.studentcouncil.crm.tasks.CommentTarget;
import com.kpi.studentcouncil.crm.tasks.CommentTargetType;
import com.kpi.studentcouncil.crm.tasks.UserMentioned;

class CommentServiceTest {

	private final CommentRepository comments = mock(CommentRepository.class);
	private final CommentTarget taskTarget = mock(CommentTarget.class);
	private final FileService files = mock(FileService.class);
	private final UserDirectory users = mock(UserDirectory.class);
	private final AccessService access = mock(AccessService.class);
	private final CommentPolicy policy = mock(CommentPolicy.class);
	private final AuditPublisher audit = mock(AuditPublisher.class);
	private final ActorProvider actors = mock(ActorProvider.class);
	private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

	private final UUID author = UUID.randomUUID();
	private final UUID taskId = UUID.randomUUID();
	private CommentService service;

	@BeforeEach
	void setUp() {
		when(taskTarget.type()).thenReturn(CommentTargetType.TASK);
		when(taskTarget.viewPermission()).thenReturn("tasks.view");
		when(actors.currentActorId()).thenReturn(author);
		when(comments.save(any(Comment.class))).thenAnswer(inv -> {
			Comment c = inv.getArgument(0);
			if (c.getId() == null) {
				ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
				ReflectionTestUtils.setField(c, "createdBy", author);
			}
			return c;
		});
		service = new CommentService(comments, List.of(taskTarget), files, users, access, policy, audit, actors,
				events, Clock.systemUTC());
	}

	private static Map<String, Object> body(UUID... mentions) {
		java.util.List<Object> inline = new java.util.ArrayList<>();
		inline.add(Map.of("type", "text", "text", "hi "));
		for (UUID m : mentions) {
			inline.add(Map.of("type", "mention", "attrs", Map.of("id", m.toString())));
		}
		return Map.of("type", "doc", "content", List.of(Map.of("type", "paragraph", "content", inline)));
	}

	private Comment existing(Set<UUID> mentions) {
		Comment c = new Comment(CommentTargetType.TASK, taskId, body(mentions.toArray(UUID[]::new)), mentions,
				List.of());
		ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
		ReflectionTestUtils.setField(c, "createdBy", author);
		when(comments.lockById(c.getId())).thenReturn(Optional.of(c));
		return c;
	}

	@Test
	void createStoresMentionsAuditsAndPublishesOneEventPerMention() {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		when(users.isActive(a)).thenReturn(true);
		when(users.isActive(b)).thenReturn(true);

		CommentService.CommentView view = service.create(CommentTargetType.TASK, taskId, body(a, b), List.of(a, b),
				null);

		assertThat(view.comment().getMentionUserIds()).containsExactlyInAnyOrder(a, b);
		verify(taskTarget).assertAcceptsComments(taskId);
		verify(audit).publish(eq(AuditAction.CREATE), eq("Comment"), eq(view.comment().getId()), eq(null), any());
		ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
		verify(events, org.mockito.Mockito.times(2)).publishEvent(published.capture());
		assertThat(published.getAllValues()).allSatisfy(e -> {
			UserMentioned m = (UserMentioned) e;
			assertThat(m.authorId()).isEqualTo(author);
			assertThat(m.targetId()).isEqualTo(taskId);
			assertThat(m.targetType()).isEqualTo(CommentTargetType.TASK);
			assertThat(m.commentId()).isEqualTo(view.comment().getId());
		});
	}

	@Test
	void mentioningAnInactiveOrUnknownUserIsRejectedAndNothingIsSaved() {
		UUID gone = UUID.randomUUID();
		when(users.isActive(gone)).thenReturn(false);
		assertThatThrownBy(() -> service.create(CommentTargetType.TASK, taskId, body(gone), List.of(gone), null))
				.isInstanceOfSatisfying(DomainException.class,
						e -> assertThat(e.getCode()).isEqualTo("COMMENT_MENTION_INVALID"));
		verify(comments, never()).save(any());
		verify(events, never()).publishEvent(any(Object.class));
	}

	@Test
	void aTargetTypeWithoutAnImplementationIsRejected() {
		assertThatThrownBy(() -> service.create(CommentTargetType.RESOLUTION, UUID.randomUUID(), body(), null, null))
				.isInstanceOfSatisfying(DomainException.class,
						e -> assertThat(e.getCode()).isEqualTo("COMMENT_TARGET_UNSUPPORTED"));
	}

	@Test
	void aMissingTargetIsRejectedByTheTargetExtensionPoint() {
		doThrow(new NotFoundException("Task", taskId)).when(taskTarget).assertAcceptsComments(taskId);
		assertThatThrownBy(() -> service.create(CommentTargetType.TASK, taskId, body(), null, null))
				.isInstanceOf(NotFoundException.class);
		verify(comments, never()).save(any());
	}

	@Test
	void anAttachmentThatDoesNotExistIsRejected() {
		UUID file = UUID.randomUUID();
		when(files.require(file)).thenThrow(new NotFoundException("File", file));
		assertThatThrownBy(() -> service.create(CommentTargetType.TASK, taskId, body(), null, List.of(file)))
				.isInstanceOfSatisfying(DomainException.class,
						e -> assertThat(e.getCode()).isEqualTo("COMMENT_ATTACHMENT_INVALID"));
	}

	@Test
	void editPublishesEventsOnlyForNewMentionsAndKeepsTheBeforeStateInAudit() {
		UUID kept = UUID.randomUUID();
		UUID added = UUID.randomUUID();
		Comment c = existing(Set.of(kept));
		when(users.isActive(added)).thenReturn(true);
		// the kept user was archived since: it must not be re-validated

		service.edit(c.getId(), body(kept, added), List.of(kept, added), null);

		ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
		verify(events).publishEvent(published.capture());
		assertThat(((UserMentioned) published.getValue()).userId()).isEqualTo(added);
		verify(users, never()).isActive(kept);
		ArgumentCaptor<Object> before = ArgumentCaptor.forClass(Object.class);
		ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
		verify(audit).publish(eq(AuditAction.UPDATE), eq("Comment"), eq(c.getId()), before.capture(), after.capture());
		assertThat(((Map<?, ?>) before.getValue()).get("mentionUserIds")).isEqualTo(List.of(kept.toString()));
		assertThat((List<?>) ((Map<?, ?>) after.getValue()).get("mentionUserIds")).hasSize(2);
		assertThat(c.getEditedAt()).isNotNull();
	}

	@Test
	void editByANonAuthorIsForbiddenAndChangesNothing() {
		Comment c = existing(Set.of());
		doThrow(new ForbiddenException("COMMENT_EDIT_FORBIDDEN")).when(policy).assertCanModify(c);
		assertThatThrownBy(() -> service.edit(c.getId(), body(), null, null)).isInstanceOf(ForbiddenException.class);
		verify(comments, never()).save(any());
		verify(audit, never()).publish(any(AuditAction.class), any(), any(), any(), any());
	}

	@Test
	void anArchivedCommentCannotBeEdited() {
		Comment c = existing(Set.of());
		c.archive(author, java.time.Instant.now());
		assertThatThrownBy(() -> service.edit(c.getId(), body(), null, null)).isInstanceOfSatisfying(
				DomainException.class, e -> assertThat(e.getCode()).isEqualTo("COMMENT_ARCHIVED"));
	}

	@Test
	void archiveSoftDeletesAndAudits() {
		Comment c = existing(Set.of());
		CommentService.CommentView view = service.archive(c.getId());
		assertThat(view.comment().isArchived()).isTrue();
		assertThat(view.comment().getArchivedBy()).isEqualTo(author);
		verify(audit).publish(eq(AuditAction.ARCHIVE), eq("Comment"), eq(c.getId()), any(), any());
	}

	@Test
	void listingNeedsTheViewPermissionOfTheTarget() {
		when(access.hasPermission(author, "tasks.view")).thenReturn(false);
		assertThatThrownBy(() -> service.list(CommentTargetType.TASK, taskId,
				org.springframework.data.domain.PageRequest.of(0, 20))).isInstanceOf(ForbiddenException.class);
	}

}
