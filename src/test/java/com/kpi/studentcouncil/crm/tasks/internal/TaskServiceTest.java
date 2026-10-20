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

import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.audit.AuditAction;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.files.FileService;
import com.kpi.studentcouncil.crm.identity.UserDirectory;
import com.kpi.studentcouncil.crm.shared.error.DomainException;
import com.kpi.studentcouncil.crm.shared.error.ForbiddenException;
import com.kpi.studentcouncil.crm.shared.error.NotFoundException;
import com.kpi.studentcouncil.crm.tasks.StatusCategory;
import com.kpi.studentcouncil.crm.tasks.TaskEvent;

class TaskServiceTest {

	private final TaskRepository tasks = mock(TaskRepository.class);
	private final TaskLinkRepository links = mock(TaskLinkRepository.class);
	private final SpaceRepository spaces = mock(SpaceRepository.class);
	private final SpaceStatusRepository statuses = mock(SpaceStatusRepository.class);
	private final ResolutionRepository resolutions = mock(ResolutionRepository.class);
	private final CustomFieldDefinitionRepository fields = mock(CustomFieldDefinitionRepository.class);
	private final FileService files = mock(FileService.class);
	private final UserDirectory users = mock(UserDirectory.class);
	private final TaskEditPolicy editPolicy = mock(TaskEditPolicy.class);
	private final AuditPublisher audit = mock(AuditPublisher.class);
	private final ActorProvider actors = mock(ActorProvider.class);
	private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

	private final UUID spaceId = UUID.randomUUID();
	private final UUID author = UUID.randomUUID();
	private final Space space = new Space("Space", null, true);
	private SpaceStatus todo;
	private SpaceStatus done;
	private TaskService service;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(space, "id", spaceId);
		todo = status("To do", StatusCategory.TODO, 0);
		done = status("Done", StatusCategory.DONE, 1);
		when(spaces.lockById(spaceId)).thenReturn(Optional.of(space));
		when(statuses.findBySpaceIdAndArchivedAtIsNullOrderByPositionAscIdAsc(spaceId)).thenReturn(List.of(todo, done));
		when(fields.findBySpaceIdAndArchivedAtIsNullOrderByCreatedAtAscIdAsc(spaceId)).thenReturn(List.of());
		when(tasks.save(any(Task.class))).thenAnswer(inv -> {
			Task t = inv.getArgument(0);
			if (t.getId() == null) {
				ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
				ReflectionTestUtils.setField(t, "createdBy", author);
			}
			return t;
		});
		service = new TaskService(tasks, links, spaces, statuses, resolutions, fields, files, users, editPolicy, audit, actors,
				events, Clock.systemUTC());
	}

	private SpaceStatus status(String name, StatusCategory category, int position) {
		SpaceStatus s = new SpaceStatus(spaceId, name, category, position);
		ReflectionTestUtils.setField(s, "id", UUID.randomUUID());
		return s;
	}

	private Task existingTask() {
		Task task = new Task(spaceId, "Title", null, todo.getId(), TaskPriority.NORMAL, null, Set.of(), List.of(),
				Map.of());
		ReflectionTestUtils.setField(task, "id", UUID.randomUUID());
		ReflectionTestUtils.setField(task, "createdBy", author);
		when(tasks.lockById(task.getId())).thenReturn(Optional.of(task));
		when(tasks.findById(task.getId())).thenReturn(Optional.of(task));
		return task;
	}

	private TaskService.TaskView create(UUID statusId) {
		return service.create(spaceId, " Plan the event ", null, statusId, null, null, List.of("A"), null, null, null);
	}

	@Test
	void createUsesTheFirstTodoStatusAndNormalPriorityByDefault() {
		TaskService.TaskView view = create(null);
		assertThat(view.task().getStatusId()).isEqualTo(todo.getId());
		assertThat(view.task().getPriority()).isEqualTo(TaskPriority.NORMAL);
		assertThat(view.task().getTitle()).isEqualTo("Plan the event");
		assertThat(view.task().getLabels()).containsExactly("a");
	}

	@Test
	void createPublishesAnAuditEventWithAfterStateAndATaskCreatedEvent() {
		TaskService.TaskView view = create(done.getId());
		verify(audit).publish(eq(AuditAction.CREATE), eq("Task"), eq(view.task().getId()), eq(null), any());
		ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
		verify(events).publishEvent(event.capture());
		assertThat(event.getValue()).isInstanceOfSatisfying(TaskEvent.class, e -> {
			assertThat(e.kind()).isEqualTo(TaskEvent.Kind.CREATED);
			assertThat(e.statusId()).isEqualTo(done.getId());
			assertThat(e.authorId()).isEqualTo(author);
		});
	}

	@Test
	void createRejectsAStatusOfAnotherSpace() {
		assertThatThrownBy(() -> create(UUID.randomUUID())).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.getCode()).isEqualTo("TASK_STATUS_NOT_IN_SPACE"));
		verify(tasks, never()).save(any());
	}

	@Test
	void createRejectsAnArchivedOrUnknownSpace() {
		space.archive(author, java.time.Instant.now());
		assertThatThrownBy(() -> create(null)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.getCode()).isEqualTo("SPACE_ARCHIVED"));
		assertThatThrownBy(() -> service.create(UUID.randomUUID(), "T", null, null, null, null, null, null, null, null))
				.isInstanceOf(NotFoundException.class);
	}

	@Test
	void createRejectsMissingAttachmentsAndArchivedRelatedTasks() {
		UUID file = UUID.randomUUID();
		when(files.require(file)).thenThrow(new NotFoundException("File", file));
		assertThatThrownBy(() -> service.create(spaceId, "T", null, null, null, null, null, null, List.of(file), null))
				.isInstanceOfSatisfying(DomainException.class,
						e -> assertThat(e.getCode()).isEqualTo("TASK_ATTACHMENT_INVALID"));

		Task archived = existingTask();
		archived.archive(author, java.time.Instant.now());
		when(tasks.findByIdIn(any())).thenReturn(List.of(archived));
		assertThatThrownBy(() -> service.create(spaceId, "T", null, null, null, null, null, null, null,
				List.of(archived.getId()))).isInstanceOfSatisfying(DomainException.class,
						e -> assertThat(e.getCode()).isEqualTo("TASK_RELATED_INVALID"));
	}

	@Test
	void updateByANonAuthorIsForbiddenAndChangesNothing() {
		Task task = existingTask();
		doThrow(new ForbiddenException("TASK_EDIT_FORBIDDEN")).when(editPolicy).assertCanEdit(task);
		assertThatThrownBy(() -> service.update(task.getId(), "New", null, null, null, null, null, null, null))
				.isInstanceOf(ForbiddenException.class);
		assertThat(task.getTitle()).isEqualTo("Title");
		verify(audit, never()).publish(any(AuditAction.class), any(), any(), any(), any());
	}

	@Test
	void updateAuditsBeforeAndAfterAndPublishesTaskUpdated() {
		Task task = existingTask();
		service.update(task.getId(), "New title", null, TaskPriority.HIGH, null, List.of("x"), null, null, null);
		ArgumentCaptor<Object> before = ArgumentCaptor.forClass(Object.class);
		ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
		verify(audit).publish(eq(AuditAction.UPDATE), eq("Task"), eq(task.getId()), before.capture(), after.capture());
		assertThat(((Map<?, ?>) before.getValue()).get("title")).isEqualTo("Title");
		assertThat(((Map<?, ?>) after.getValue()).get("title")).isEqualTo("New title");
		assertThat(((Map<?, ?>) after.getValue()).get("priority")).isEqualTo("HIGH");
		ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
		verify(events).publishEvent(event.capture());
		assertThat(((TaskEvent) event.getValue()).kind()).isEqualTo(TaskEvent.Kind.UPDATED);
	}

	@Test
	void updateOfAnArchivedTaskIsAConflict() {
		Task task = existingTask();
		task.archive(author, java.time.Instant.now());
		assertThatThrownBy(() -> service.update(task.getId(), "New", null, null, null, null, null, null, null))
				.isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.getCode()).isEqualTo("TASK_ARCHIVED"));
	}

	@Test
	void changeStatusRequiresAStatusOfTheSameSpace() {
		Task task = existingTask();
		assertThatThrownBy(() -> service.changeStatus(task.getId(), UUID.randomUUID()))
				.isInstanceOfSatisfying(DomainException.class,
						e -> assertThat(e.getCode()).isEqualTo("TASK_STATUS_NOT_IN_SPACE"));
		service.changeStatus(task.getId(), done.getId());
		assertThat(task.getStatusId()).isEqualTo(done.getId());
		verify(audit).publish(eq(AuditAction.UPDATE), eq("Task"), eq(task.getId()), any(), any());
	}

	@Test
	void changeToTheSameStatusIsANoOp() {
		Task task = existingTask();
		service.changeStatus(task.getId(), todo.getId());
		verify(audit, never()).publish(any(AuditAction.class), any(), any(), any(), any());
		verify(events, never()).publishEvent(any());
	}

	@Test
	void changeStatusByANonAuthorIsForbidden() {
		Task task = existingTask();
		doThrow(new ForbiddenException("TASK_EDIT_FORBIDDEN")).when(editPolicy).assertCanChangeStatus(task);
		assertThatThrownBy(() -> service.changeStatus(task.getId(), done.getId())).isInstanceOf(ForbiddenException.class);
		assertThat(task.getStatusId()).isEqualTo(todo.getId());
	}

	@Test
	void archiveAndRestoreAuditAndPublish() {
		Task task = existingTask();
		service.archive(task.getId());
		assertThat(task.isArchived()).isTrue();
		verify(audit).publish(eq(AuditAction.ARCHIVE), eq("Task"), eq(task.getId()), any(), any());
		service.restore(task.getId());
		assertThat(task.isArchived()).isFalse();
		verify(audit).publish(eq(AuditAction.RESTORE), eq("Task"), eq(task.getId()), any(), any());
		assertThatThrownBy(() -> service.restore(task.getId())).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.getCode()).isEqualTo("NOT_ARCHIVED"));
	}

	@Test
	void restoreIsRefusedWhileTheSpaceIsArchived() {
		Task task = existingTask();
		task.archive(author, java.time.Instant.now());
		space.archive(author, java.time.Instant.now());
		assertThatThrownBy(() -> service.restore(task.getId())).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.getCode()).isEqualTo("SPACE_ARCHIVED"));
	}

	@Test
	void usageGuardCountsActiveTasksAndReassignsAllOfThem() {
		Task one = existingTask();
		Task two = existingTask();
		when(tasks.countBySpaceIdAndStatusIdAndArchivedAtIsNull(spaceId, todo.getId())).thenReturn(2L);
		when(tasks.findBySpaceIdAndStatusId(spaceId, todo.getId())).thenReturn(List.of(one, two));
		TaskStatusUsageGuard guard = new TaskStatusUsageGuard(service);

		assertThat(guard.countUsages(spaceId, todo.getId())).isEqualTo(2L);
		guard.reassign(spaceId, todo.getId(), done.getId());

		assertThat(one.getStatusId()).isEqualTo(done.getId());
		assertThat(two.getStatusId()).isEqualTo(done.getId());
		verify(audit).publish(eq(AuditAction.UPDATE), eq("Task"), eq(one.getId()), any(), any());
		verify(audit).publish(eq(AuditAction.UPDATE), eq("Task"), eq(two.getId()), any(), any());
	}

}
