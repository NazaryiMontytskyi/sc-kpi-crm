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
	private final Clock clock = Clock.fixed(Instant.parse("2026-03-10T10:00:00Z"), ZoneOffset.UTC);

	private final UUID author = UUID.randomUUID();
	private final UUID executor = UUID.randomUUID();
	private final UUID taskId = UUID.randomUUID();
	private ResolutionService service;

	@BeforeEach
	void setUp() {
		when(actors.currentActorId()).thenReturn(author);
		when(users.exists(any())).thenReturn(true);
		when(resolutions.save(any(Resolution.class))).thenAnswer(inv -> {
			Resolution r = inv.getArgument(0);
			if (r.getId() == null) {
				ReflectionTestUtils.setField(r, "id", UUID.randomUUID());
			}
			return r;
		});
		service = new ResolutionService(resolutions, tasks, users, audit, actors, clock);
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
		when(users.exists(unknown)).thenReturn(false);
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

}
