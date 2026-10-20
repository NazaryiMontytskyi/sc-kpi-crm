package com.kpi.studentcouncil.crm.tasks.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import org.springframework.context.ApplicationEventPublisher;

import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.audit.AuditAction;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.identity.UserDirectory;
import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;
import com.kpi.studentcouncil.crm.shared.error.ConflictException;
import com.kpi.studentcouncil.crm.shared.error.DomainException;
import com.kpi.studentcouncil.crm.shared.error.NotFoundException;

class ResolutionServiceTest {

	private final ResolutionRepository resolutions = mock(ResolutionRepository.class);
	private final TaskRepository tasks = mock(TaskRepository.class);
	private final UserDirectory users = mock(UserDirectory.class);
	private final AuditPublisher audit = mock(AuditPublisher.class);
	private final ActorProvider actors = mock(ActorProvider.class);
	private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
	private final Clock clock = Clock.fixed(Instant.parse("2026-03-10T10:00:00Z"), ZoneOffset.UTC);

	private final UUID author = UUID.randomUUID();
	private final UUID executor = UUID.randomUUID();
	private final UUID taskId = UUID.randomUUID();
	private ResolutionService service;

	@BeforeEach
	void setUp() {
		when(actors.currentActorId()).thenReturn(author);
		when(users.isActive(any())).thenReturn(true);
		when(resolutions.save(any(Resolution.class))).thenAnswer(inv -> {
			Resolution r = inv.getArgument(0);
			if (r.getId() == null) {
				ReflectionTestUtils.setField(r, "id", UUID.randomUUID());
			}
			return r;
		});
		service = new ResolutionService(resolutions, tasks, users, audit, actors, events, clock);
	}

	private Task activeTask() {
		Task task = mock(Task.class);
		when(task.isArchived()).thenReturn(false);
		when(tasks.findById(taskId)).thenReturn(Optional.of(task));
		return task;
	}

	private Resolution existing(UUID resolutionAuthor, UUID resolutionExecutor, Resolution parent) {
		Resolution r = new Resolution(taskId, resolutionAuthor, resolutionExecutor, Set.of(), "x",
				LocalDate.of(2026, 4, 1), parent == null ? null : parent.getId(), clock.instant());
		ReflectionTestUtils.setField(r, "id", UUID.randomUUID());
		when(resolutions.lockById(r.getId())).thenReturn(Optional.of(r));
		return r;
	}

	@Test
	void createStoresANewResolutionAuthoredByTheActorAndAuditsIt() {
		activeTask();
		UUID co = UUID.randomUUID();

		Resolution created = service.create(taskId, executor, List.of(co, executor), "  Prepare  ",
				LocalDate.of(2026, 4, 1));

		assertThat(created.getState()).isEqualTo(ResolutionState.NEW);
		assertThat(created.getAuthorId()).isEqualTo(author);
		assertThat(created.getCoExecutorIds()).containsExactly(co);
		assertThat(created.getInstruction()).isEqualTo("Prepare");
		assertThat(created.getParentResolutionId()).isNull();
		verify(audit).publish(eq(AuditAction.CREATE), eq("Resolution"), eq(created.getId()), eq(null), any());
	}

	@Test
	void createRejectsMissingTaskArchivedTaskAndUnknownUsers() {
		assertThatThrownBy(() -> service.create(taskId, executor, List.of(), "x", LocalDate.of(2026, 4, 1)))
				.isInstanceOf(NotFoundException.class);

		Task task = mock(Task.class);
		when(task.isArchived()).thenReturn(true);
		when(tasks.findById(taskId)).thenReturn(Optional.of(task));
		assertThatThrownBy(() -> service.create(taskId, executor, List.of(), "x", LocalDate.of(2026, 4, 1)))
				.isInstanceOf(ConflictException.class);

		activeTask();
		UUID unknown = UUID.randomUUID();
		when(users.isActive(unknown)).thenReturn(false);
		assertThatThrownBy(() -> service.create(taskId, unknown, List.of(), "x", LocalDate.of(2026, 4, 1)))
				.isInstanceOf(BusinessRuleException.class)
				.extracting(e -> ((DomainException) e).getCode()).isEqualTo("RESOLUTION_EXECUTOR_INVALID");
		assertThatThrownBy(() -> service.create(taskId, executor, List.of(unknown), "x", LocalDate.of(2026, 4, 1)))
				.isInstanceOf(BusinessRuleException.class)
				.extracting(e -> ((DomainException) e).getCode()).isEqualTo("RESOLUTION_CO_EXECUTOR_INVALID");
		verify(resolutions, never()).save(any());
	}

	@Test
	void theFullLifecycleIsDrivenByTheCurrentActorAndEveryStepIsAudited() {
		Resolution r = existing(author, executor, null);

		when(actors.currentActorId()).thenReturn(executor);
		service.start(r.getId());
		service.submit(r.getId(), "done");
		when(actors.currentActorId()).thenReturn(author);
		service.requestRework(r.getId(), "again");
		when(actors.currentActorId()).thenReturn(executor);
		service.resume(r.getId());
		service.submit(r.getId(), "done properly");
		when(actors.currentActorId()).thenReturn(author);
		service.accept(r.getId());

		assertThat(r.getState()).isEqualTo(ResolutionState.ACCEPTED);
		assertThat(r.getHistory()).hasSize(7);
		verify(audit, org.mockito.Mockito.times(6)).publish(eq(AuditAction.UPDATE), eq("Resolution"), eq(r.getId()),
				any(), any());
	}

	@Test
	void aRejectedTransitionIsNotSavedOrAudited() {
		Resolution r = existing(author, executor, null);
		when(actors.currentActorId()).thenReturn(author);

		assertThatThrownBy(() -> service.start(r.getId())).isInstanceOf(DomainException.class)
				.extracting(e -> ((DomainException) e).getCode()).isEqualTo("RESOLUTION_NOT_EXECUTOR");

		verify(resolutions, never()).save(any());
		verify(audit, never()).publish(any(AuditAction.class), any(), any(), any(), any());
	}

	@Test
	void anArchivedOrUnknownResolutionCannotBeChanged() {
		Resolution r = existing(author, executor, null);
		r.archive(author, clock.instant());
		assertThatThrownBy(() -> service.start(r.getId())).isInstanceOf(ConflictException.class);
		assertThatThrownBy(() -> service.cancel(r.getId(), null)).isInstanceOf(ConflictException.class);
		assertThatThrownBy(() -> service.accept(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
	}

	@Test
	void cancelCascadesThroughTheChainSavesAndAuditsEveryChangedResolution() {
		Resolution root = existing(author, executor, null);
		Resolution child = existing(executor, UUID.randomUUID(), root);
		Resolution grandchild = existing(child.getExecutorId(), UUID.randomUUID(), child);
		root.apply(ResolutionAction.START, executor, null, clock.instant());
		root.apply(ResolutionAction.REDIRECT, executor, null, clock.instant());
		child.apply(ResolutionAction.START, child.getExecutorId(), null, clock.instant());
		child.apply(ResolutionAction.REDIRECT, child.getExecutorId(), null, clock.instant());
		when(resolutions.lockChildren(root.getId())).thenReturn(List.of(child));
		when(resolutions.lockChildren(child.getId())).thenReturn(List.of(grandchild));
		when(resolutions.lockChildren(grandchild.getId())).thenReturn(List.of());

		List<Resolution> changed = service.cancel(root.getId(), "stop");

		assertThat(changed).containsExactly(root, child, grandchild);
		assertThat(List.of(root, child, grandchild)).allMatch(r -> r.getState() == ResolutionState.CANCELED);
		verify(resolutions, org.mockito.Mockito.times(3)).save(any(Resolution.class));
		verify(audit, org.mockito.Mockito.times(3)).publish(eq(AuditAction.UPDATE), eq("Resolution"), any(), any(),
				any());
	}

	@Test
	void cancelByANonAuthorSavesNothing() {
		Resolution root = existing(author, executor, null);
		when(actors.currentActorId()).thenReturn(executor);
		when(resolutions.lockChildren(any())).thenReturn(List.of());

		assertThatThrownBy(() -> service.cancel(root.getId(), null)).isInstanceOf(DomainException.class)
				.extracting(e -> ((DomainException) e).getCode()).isEqualTo("RESOLUTION_NOT_AUTHOR");

		verify(resolutions, never()).save(any());
		assertThat(root.getState()).isEqualTo(ResolutionState.NEW);
	}

	private Resolution inProgress(UUID resolutionAuthor, UUID resolutionExecutor, Resolution parent) {
		Resolution r = existing(resolutionAuthor, resolutionExecutor, parent);
		when(resolutions.findById(r.getId())).thenReturn(Optional.of(r));
		r.apply(ResolutionAction.START, resolutionExecutor, null, clock.instant());
		return r;
	}

	private DomainException redirectFailure(UUID id, UUID newExecutor) {
		return (DomainException) org.assertj.core.api.Assertions
				.catchThrowable(() -> service.redirect(id, newExecutor, "Pass on", LocalDate.of(2026, 3, 20)));
	}

	@Test
	void redirectCreatesOneChildAuthoredByTheExecutorAndMovesTheParentToRedirected() {
		activeTask();
		Resolution parent = inProgress(author, executor, null);
		UUID next = UUID.randomUUID();
		when(actors.currentActorId()).thenReturn(executor);

		var result = service.redirect(parent.getId(), next, "  Pass on  ", LocalDate.of(2026, 3, 20));

		assertThat(parent.getState()).isEqualTo(ResolutionState.REDIRECTED);
		assertThat(parent.getHistory()).extracting(ResolutionHistoryEntry::to).containsExactly(ResolutionState.NEW,
				ResolutionState.IN_PROGRESS, ResolutionState.REDIRECTED);
		Resolution child = result.child();
		assertThat(child.getParentResolutionId()).isEqualTo(parent.getId());
		assertThat(child.getAuthorId()).isEqualTo(executor);
		assertThat(child.getExecutorId()).isEqualTo(next);
		assertThat(child.getState()).isEqualTo(ResolutionState.NEW);
		assertThat(child.getInstruction()).isEqualTo("Pass on");
		assertThat(child.getTaskId()).isEqualTo(taskId);
		assertThat(result.dueAfterParent()).isFalse();
		verify(resolutions, org.mockito.Mockito.times(2)).save(any(Resolution.class));
		verify(audit).publish(eq(AuditAction.CREATE), eq("Resolution"), eq(child.getId()), eq(null), any());
		org.mockito.ArgumentCaptor<com.kpi.studentcouncil.crm.tasks.ResolutionEvent> captor = org.mockito.ArgumentCaptor
				.forClass(com.kpi.studentcouncil.crm.tasks.ResolutionEvent.class);
		verify(events, org.mockito.Mockito.times(2)).publishEvent(captor.capture());
		assertThat(captor.getAllValues()).extracting(com.kpi.studentcouncil.crm.tasks.ResolutionEvent::kind)
				.containsExactly(com.kpi.studentcouncil.crm.tasks.ResolutionEvent.Kind.REDIRECTED,
						com.kpi.studentcouncil.crm.tasks.ResolutionEvent.Kind.ISSUED);
	}

	@Test
	void aLaterDueDateThanTheParentsIsAcceptedWithAWarningFlag() {
		activeTask();
		Resolution parent = inProgress(author, executor, null);
		when(actors.currentActorId()).thenReturn(executor);

		var result = service.redirect(parent.getId(), UUID.randomUUID(), "x", LocalDate.of(2026, 5, 1));

		assertThat(result.dueAfterParent()).isTrue();
		assertThat(parent.getState()).isEqualTo(ResolutionState.REDIRECTED);
	}

	@Test
	void redirectIsRejectedForNonExecutorsAndWrongStatesAndSavesNothing() {
		activeTask();
		Resolution parent = inProgress(author, executor, null);
		when(actors.currentActorId()).thenReturn(author);
		assertThat(redirectFailure(parent.getId(), UUID.randomUUID()).getCode()).isEqualTo("RESOLUTION_NOT_EXECUTOR");

		Resolution fresh = existing(author, executor, null);
		when(actors.currentActorId()).thenReturn(executor);
		assertThat(redirectFailure(fresh.getId(), UUID.randomUUID()).getCode())
				.isEqualTo("RESOLUTION_TRANSITION_NOT_ALLOWED");

		verify(resolutions, never()).save(any());
		assertThat(parent.getState()).isEqualTo(ResolutionState.IN_PROGRESS);
	}

	@Test
	void redirectToSelfToAnInactiveUserAndToAnybodyInTheChainIsRejected() {
		activeTask();
		UUID deputy = UUID.randomUUID();
		UUID head = UUID.randomUUID();
		Resolution first = inProgress(head, deputy, null);
		first.apply(ResolutionAction.REDIRECT, deputy, null, clock.instant());
		Resolution second = inProgress(deputy, executor, first);
		when(actors.currentActorId()).thenReturn(executor);

		assertThat(redirectFailure(second.getId(), executor).getCode()).isEqualTo("RESOLUTION_REDIRECT_TO_SELF");
		assertThat(redirectFailure(second.getId(), deputy).getCode()).isEqualTo("RESOLUTION_REDIRECT_CYCLE");
		assertThat(redirectFailure(second.getId(), head).getCode()).isEqualTo("RESOLUTION_REDIRECT_CYCLE");
		UUID blocked = UUID.randomUUID();
		when(users.isActive(blocked)).thenReturn(false);
		assertThat(redirectFailure(second.getId(), blocked).getCode()).isEqualTo("RESOLUTION_EXECUTOR_INVALID");

		verify(resolutions, never()).save(any());
		assertThat(second.getState()).isEqualTo(ResolutionState.IN_PROGRESS);
	}

	@Test
	void redirectRejectsADueDateInThePast() {
		activeTask();
		Resolution parent = inProgress(author, executor, null);
		when(actors.currentActorId()).thenReturn(executor);
		assertThatThrownBy(() -> service.redirect(parent.getId(), UUID.randomUUID(), "x", LocalDate.of(2026, 3, 9)))
				.isInstanceOf(BusinessRuleException.class)
				.extracting(e -> ((DomainException) e).getCode()).isEqualTo("RESOLUTION_DUE_DATE_IN_PAST");
		assertThat(parent.getState()).isEqualTo(ResolutionState.IN_PROGRESS);
	}

	@Test
	void acceptingAChildNotifiesTheParentExecutorAndLeavesTheParentUntouched() {
		Resolution parent = inProgress(author, executor, null);
		parent.apply(ResolutionAction.REDIRECT, executor, null, clock.instant());
		UUID next = UUID.randomUUID();
		Resolution child = existing(executor, next, parent);
		child.apply(ResolutionAction.START, next, null, clock.instant());
		child.apply(ResolutionAction.SUBMIT, next, "done", clock.instant());
		when(actors.currentActorId()).thenReturn(executor);

		service.accept(child.getId());

		assertThat(child.getState()).isEqualTo(ResolutionState.ACCEPTED);
		assertThat(parent.getState()).isEqualTo(ResolutionState.REDIRECTED);
		org.mockito.ArgumentCaptor<Object> captor = org.mockito.ArgumentCaptor.forClass(Object.class);
		verify(events, org.mockito.Mockito.times(2)).publishEvent(captor.capture());
		assertThat(captor.getAllValues().get(1)).isEqualTo(new com.kpi.studentcouncil.crm.tasks.ChildResolutionAccepted(
				child.getId(), parent.getId(), taskId, executor, next, executor));
		verify(resolutions, never()).save(parent);
	}

	@Test
	void chainBuildsTheTreeFromTheRootStartingAnywhere() {
		Resolution r1 = inProgress(author, executor, null);
		Resolution r2 = inProgress(executor, UUID.randomUUID(), r1);
		Resolution r3 = existing(r2.getExecutorId(), UUID.randomUUID(), r2);
		when(resolutions.findById(r3.getId())).thenReturn(Optional.of(r3));
		when(resolutions.findByParentResolutionIdOrderByCreatedAtAscIdAsc(r1.getId())).thenReturn(List.of(r2));
		when(resolutions.findByParentResolutionIdOrderByCreatedAtAscIdAsc(r2.getId())).thenReturn(List.of(r3));
		when(resolutions.findByParentResolutionIdOrderByCreatedAtAscIdAsc(r3.getId())).thenReturn(List.of());

		var tree = service.chain(r3.getId());

		assertThat(tree.resolution()).isSameAs(r1);
		assertThat(tree.children()).hasSize(1);
		assertThat(tree.children().get(0).resolution()).isSameAs(r2);
		assertThat(tree.children().get(0).children().get(0).resolution()).isSameAs(r3);
	}

	@Test
	void dueDateInThePastIsRejectedButTodayIsAccepted() {
		activeTask();
		assertThatThrownBy(() -> service.create(taskId, executor, List.of(), "x", LocalDate.of(2026, 3, 9)))
				.isInstanceOf(BusinessRuleException.class)
				.extracting(e -> ((DomainException) e).getCode()).isEqualTo("RESOLUTION_DUE_DATE_IN_PAST");
		verify(resolutions, never()).save(any());
		Resolution created = service.create(taskId, executor, List.of(), "x", LocalDate.of(2026, 3, 10));
		assertThat(created.getState()).isEqualTo(ResolutionState.NEW);
	}

	@Test
	void inactiveExecutorIsRejected() {
		activeTask();
		UUID blocked = UUID.randomUUID();
		when(users.isActive(blocked)).thenReturn(false);
		when(users.exists(blocked)).thenReturn(true);
		assertThatThrownBy(() -> service.create(taskId, blocked, List.of(), "x", LocalDate.of(2026, 4, 1)))
				.isInstanceOf(BusinessRuleException.class)
				.extracting(e -> ((DomainException) e).getCode()).isEqualTo("RESOLUTION_EXECUTOR_INVALID");
	}

	@Test
	void everyTransitionPublishesAnEventWithAllParticipants() {
		activeTask();
		UUID co = UUID.randomUUID();
		Resolution r = service.create(taskId, executor, List.of(co), "x", LocalDate.of(2026, 4, 1));
		org.mockito.ArgumentCaptor<com.kpi.studentcouncil.crm.tasks.ResolutionEvent> captor = org.mockito.ArgumentCaptor
				.forClass(com.kpi.studentcouncil.crm.tasks.ResolutionEvent.class);
		verify(events).publishEvent(captor.capture());
		var issued = captor.getValue();
		assertThat(issued.kind()).isEqualTo(com.kpi.studentcouncil.crm.tasks.ResolutionEvent.Kind.ISSUED);
		assertThat(issued.authorId()).isEqualTo(author);
		assertThat(issued.executorId()).isEqualTo(executor);
		assertThat(issued.coExecutorIds()).containsExactly(co);

		when(resolutions.lockById(r.getId())).thenReturn(Optional.of(r));
		when(actors.currentActorId()).thenReturn(executor);
		service.start(r.getId());
		service.submit(r.getId(), "done");
		when(actors.currentActorId()).thenReturn(author);
		service.accept(r.getId());
		org.mockito.ArgumentCaptor<com.kpi.studentcouncil.crm.tasks.ResolutionEvent> all = org.mockito.ArgumentCaptor
				.forClass(com.kpi.studentcouncil.crm.tasks.ResolutionEvent.class);
		verify(events, org.mockito.Mockito.times(4)).publishEvent(all.capture());
		assertThat(all.getAllValues()).extracting(com.kpi.studentcouncil.crm.tasks.ResolutionEvent::kind)
				.containsExactly(com.kpi.studentcouncil.crm.tasks.ResolutionEvent.Kind.ISSUED,
						com.kpi.studentcouncil.crm.tasks.ResolutionEvent.Kind.ACCEPTED,
						com.kpi.studentcouncil.crm.tasks.ResolutionEvent.Kind.SUBMITTED,
						com.kpi.studentcouncil.crm.tasks.ResolutionEvent.Kind.CLOSED);
	}

}
