package com.kpi.studentcouncil.crm.tasks.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
	private final TaskAssigneeRepository assignees = mock(TaskAssigneeRepository.class);
	private final TaskLinkRepository links = mock(TaskLinkRepository.class);
	private final SpaceRepository spaces = mock(SpaceRepository.class);
	private final SpaceStatusRepository statuses = mock(SpaceStatusRepository.class);
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
		when(actors.currentActorId()).thenReturn(author);
		when(tasks.save(any(Task.class))).thenAnswer(inv -> {
			Task t = inv.getArgument(0);
			if (t.getId() == null) {
				ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
				ReflectionTestUtils.setField(t, "createdBy", author);
			}
			return t;
		});
		when(assignees.saveAndFlush(any(TaskAssignee.class))).thenAnswer(inv -> {
			TaskAssignee a = inv.getArgument(0);
			if (a.getId() == null) {
				ReflectionTestUtils.setField(a, "id", UUID.randomUUID());
			}
			return a;
		});
		service = new TaskService(tasks, assignees, links, spaces, statuses, fields, files, users, editPolicy,
				mock(TaskArtifactService.class), audit, actors,
				events, Clock.systemUTC());
	}

	private SpaceStatus status(String name, StatusCategory category, int position) {
		SpaceStatus s = new SpaceStatus(spaceId, name, category, position);
		ReflectionTestUtils.setField(s, "id", UUID.randomUUID());
		return s;
	}

	private Task existingTask() {
		Task task = new Task(spaceId, author, java.time.Instant.parse("2026-01-01T10:00:00Z"), "Title", null,
				todo.getId(), TaskPriority.NORMAL, null, Set.of(), List.of(), Map.of());
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
		assertThatThrownBy(() -> service.update(task.getId(), null, "New", null, null, null, null, null, null, null))
				.isInstanceOf(ForbiddenException.class);
		assertThat(task.getTitle()).isEqualTo("Title");
		verify(audit, never()).publish(any(AuditAction.class), any(), any(), any(), any());
	}

	@Test
	void updateAuditsBeforeAndAfterAndPublishesTaskUpdated() {
		Task task = existingTask();
		service.update(task.getId(), 0L, "New title", null, TaskPriority.HIGH, java.time.LocalDate.of(2031, 3, 5),
				List.of("x"), null, null, null);
		ArgumentCaptor<Object> before = ArgumentCaptor.forClass(Object.class);
		ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
		verify(audit).publish(eq(AuditAction.UPDATE), eq("Task"), eq(task.getId()), before.capture(), after.capture());
		assertThat(((Map<?, ?>) before.getValue()).get("title")).isEqualTo("Title");
		assertThat(((Map<?, ?>) after.getValue()).get("title")).isEqualTo("New title");
		assertThat(((Map<?, ?>) after.getValue()).get("priority")).isEqualTo("HIGH");
		assertThat(((Map<?, ?>) before.getValue()).get("deadline")).isNull();
		assertThat(((Map<?, ?>) after.getValue()).get("deadline")).isEqualTo("2031-03-05");
		for (Object snapshot : List.of(before.getValue(), after.getValue())) {
			assertThat(snapshotOf(snapshot)).containsEntry("creatorId", author.toString())
					.containsEntry("issuedAt", "2026-01-01T10:00:00Z").containsEntry("parentId", null)
					.containsKey("version").doesNotContainKey("dueDate");
		}
		verify(tasks).flush();
		ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
		verify(events, times(2)).publishEvent(event.capture());
		assertThat(((TaskEvent) event.getAllValues().get(0)).kind()).isEqualTo(TaskEvent.Kind.UPDATED);
		assertThat(event.getAllValues().get(1)).isInstanceOfSatisfying(TaskEvent.class, e -> {
			assertThat(e.kind()).isEqualTo(TaskEvent.Kind.DEADLINE_CHANGED);
			assertThat(e.previousDeadline()).isNull();
			assertThat(e.deadline()).isEqualTo(java.time.LocalDate.of(2031, 3, 5));
			assertThat(e.actorId()).isEqualTo(author);
		});
	}

	@Test
	void anEditThatKeepsTheDeadlinePublishesOnlyUpdated() {
		Task task = existingTask();
		service.update(task.getId(), null, "Renamed", null, null, null, null, null, null, null);
		ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
		verify(events).publishEvent(event.capture());
		assertThat(((TaskEvent) event.getValue()).kind()).isEqualTo(TaskEvent.Kind.UPDATED);
	}

	@Test
	void aNoOpEditIsNeitherSavedNorAuditedNorPublished() {
		Task task = existingTask();
		service.update(task.getId(), 0L, " Title ", null, TaskPriority.NORMAL, null, List.of(), null, null, null);
		verify(tasks, never()).save(any());
		verify(audit, never()).publish(any(AuditAction.class), any(), any(), any(), any());
		verify(events, never()).publishEvent(any());
	}

	@Test
	void changeStatusPublishesStatusChangedWithThePreviousStatus() {
		Task task = existingTask();
		service.changeStatus(task.getId(), null, done.getId());
		ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
		verify(events).publishEvent(event.capture());
		assertThat(event.getValue()).isInstanceOfSatisfying(TaskEvent.class, e -> {
			assertThat(e.kind()).isEqualTo(TaskEvent.Kind.STATUS_CHANGED);
			assertThat(e.previousStatusId()).isEqualTo(todo.getId());
			assertThat(e.statusId()).isEqualTo(done.getId());
			assertThat(e.actorId()).isEqualTo(author);
		});
	}

	// ---- assignees ----

	@Test
	void assignAddsAnActiveAssigneeAuditsAndPublishesAssigned() {
		Task task = existingTask();
		UUID user = UUID.randomUUID();
		when(users.isActive(user)).thenReturn(true);
		TaskAssignee saved = service.assign(task.getId(), user);
		assertThat(saved.getUserId()).isEqualTo(user);
		assertThat(saved.getAssignedBy()).isEqualTo(author);
		assertThat(saved.isActive()).isTrue();
		verify(editPolicy).assertCanManageAssignee(task, user);
		ArgumentCaptor<Object> before = ArgumentCaptor.forClass(Object.class);
		ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
		verify(audit).publish(eq(AuditAction.UPDATE), eq("Task"), eq(task.getId()), before.capture(), after.capture());
		assertThat(snapshotOf(before.getValue())).containsEntry("assignee", null);
		assertThat(snapshotOf(after.getValue()).get("assignee")).isNotNull();
		ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
		verify(events).publishEvent(event.capture());
		assertThat(event.getValue()).isInstanceOfSatisfying(TaskEvent.class, e -> {
			assertThat(e.kind()).isEqualTo(TaskEvent.Kind.ASSIGNED);
			assertThat(e.assigneeId()).isEqualTo(user);
			assertThat(e.actorId()).isEqualTo(author);
		});
	}

	@Test
	void assignRejectsInactiveUsersDuplicatesAndForbiddenCallersWithoutChanges() {
		Task task = existingTask();
		UUID blocked = UUID.randomUUID();
		when(users.isActive(blocked)).thenReturn(false);
		assertThatThrownBy(() -> service.assign(task.getId(), blocked)).isInstanceOfSatisfying(DomainException.class,
				e -> {
					assertThat(e.getCode()).isEqualTo("TASK_ASSIGNEE_INACTIVE");
					assertThat(e.getStatus().value()).isEqualTo(422);
				});

		UUID twice = UUID.randomUUID();
		when(users.isActive(twice)).thenReturn(true);
		when(assignees.existsByTaskIdAndUserIdAndRemovedAtIsNull(task.getId(), twice)).thenReturn(true);
		assertThatThrownBy(() -> service.assign(task.getId(), twice)).isInstanceOfSatisfying(DomainException.class,
				e -> {
					assertThat(e.getCode()).isEqualTo("TASK_ASSIGNEE_DUPLICATE");
					assertThat(e.getStatus().value()).isEqualTo(409);
				});

		UUID other = UUID.randomUUID();
		when(users.isActive(other)).thenReturn(true);
		doThrow(new ForbiddenException("TASK_ASSIGN_FORBIDDEN")).when(editPolicy).assertCanManageAssignee(task, other);
		assertThatThrownBy(() -> service.assign(task.getId(), other)).isInstanceOf(ForbiddenException.class);

		verify(assignees, never()).saveAndFlush(any());
		verify(audit, never()).publish(any(AuditAction.class), any(), any(), any(), any());
		verify(events, never()).publishEvent(any());
	}

	@Test
	void assignToAnArchivedTaskIsAConflict() {
		Task task = existingTask();
		task.archive(author, java.time.Instant.now());
		assertThatThrownBy(() -> service.assign(task.getId(), author)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.getCode()).isEqualTo("TASK_ARCHIVED"));
	}

	@Test
	void unassignKeepsTheRowWithRemovedAtAuditsBeforeAfterAndPublishesUnassigned() {
		Task task = existingTask();
		UUID user = UUID.randomUUID();
		TaskAssignee row = new TaskAssignee(task.getId(), user, author, java.time.Instant.parse("2026-02-01T08:00:00Z"));
		ReflectionTestUtils.setField(row, "id", UUID.randomUUID());
		when(assignees.findByTaskIdAndUserIdAndRemovedAtIsNull(task.getId(), user)).thenReturn(Optional.of(row));

		TaskAssignee removed = service.unassign(task.getId(), user);
		assertThat(removed).isSameAs(row);
		assertThat(row.isActive()).isFalse();
		assertThat(row.getRemovedAt()).isNotNull();
		assertThat(row.getRemovedBy()).isEqualTo(author);
		verify(assignees, never()).delete(any());
		ArgumentCaptor<Object> before = ArgumentCaptor.forClass(Object.class);
		ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
		verify(audit).publish(eq(AuditAction.UPDATE), eq("Task"), eq(task.getId()), before.capture(), after.capture());
		assertThat(snapshotOf(snapshotOf(before.getValue()).get("assignee"))).containsEntry("removedAt", null);
		assertThat(snapshotOf(snapshotOf(after.getValue()).get("assignee")).get("removedAt")).isNotNull();
		ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
		verify(events).publishEvent(event.capture());
		assertThat(((TaskEvent) event.getValue()).kind()).isEqualTo(TaskEvent.Kind.UNASSIGNED);
		assertThat(((TaskEvent) event.getValue()).assigneeId()).isEqualTo(user);
	}

	@Test
	void unassignOfAUserWhoIsNotAnActiveAssigneeIsNotFoundAndEmitsNothing() {
		Task task = existingTask();
		assertThatThrownBy(() -> service.unassign(task.getId(), UUID.randomUUID())).isInstanceOf(NotFoundException.class);
		verify(audit, never()).publish(any(AuditAction.class), any(), any(), any(), any());
		verify(events, never()).publishEvent(any());
	}

	@Test
	void createSetsTheCreatorAndTheIssueDateAndAuditsTheNewFields() {
		TaskService.TaskView view = create(null);
		assertThat(view.task().getCreatorId()).isEqualTo(author);
		assertThat(view.task().getAuthorId()).isEqualTo(author);
		assertThat(view.task().getIssuedAt()).isNotNull();
		assertThat(view.task().getParentTaskId()).isNull();
		ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
		verify(audit).publish(eq(AuditAction.CREATE), eq("Task"), eq(view.task().getId()), eq(null), after.capture());
		assertThat(snapshotOf(after.getValue())).containsEntry("creatorId", author.toString())
				.containsEntry("issuedAt", view.task().getIssuedAt().toString()).containsEntry("deadline", null)
				.containsEntry("parentId", null).containsEntry("version", 0L);
	}

	@Test
	void updateWithAStaleVersionIsAConflictAndChangesNothing() {
		Task task = existingTask();
		ReflectionTestUtils.setField(task, "version", 3L);
		assertThatThrownBy(() -> service.update(task.getId(), 2L, "New", null, null, null, null, null, null, null))
				.isInstanceOfSatisfying(DomainException.class, e -> {
					assertThat(e.getCode()).isEqualTo("TASK_VERSION_CONFLICT");
					assertThat(e.getStatus().value()).isEqualTo(409);
					assertThat(e.getArgs()).containsExactly(3L);
				});
		assertThat(task.getTitle()).isEqualTo("Title");
		verify(audit, never()).publish(any(AuditAction.class), any(), any(), any(), any());
		service.update(task.getId(), 3L, "New", null, null, null, null, null, null, null);
		assertThat(task.getTitle()).isEqualTo("New");
	}

	@Test
	void changeStatusWithAStaleVersionIsAConflict() {
		Task task = existingTask();
		ReflectionTestUtils.setField(task, "version", 1L);
		assertThatThrownBy(() -> service.changeStatus(task.getId(), 0L, done.getId()))
				.isInstanceOfSatisfying(DomainException.class,
						e -> assertThat(e.getCode()).isEqualTo("TASK_VERSION_CONFLICT"));
		assertThat(task.getStatusId()).isEqualTo(todo.getId());
	}

	@Test
	void updateOfAnArchivedTaskIsAConflict() {
		Task task = existingTask();
		task.archive(author, java.time.Instant.now());
		assertThatThrownBy(() -> service.update(task.getId(), null, "New", null, null, null, null, null, null, null))
				.isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.getCode()).isEqualTo("TASK_ARCHIVED"));
	}

	@Test
	void changeStatusRequiresAStatusOfTheSameSpace() {
		Task task = existingTask();
		assertThatThrownBy(() -> service.changeStatus(task.getId(), null, UUID.randomUUID()))
				.isInstanceOfSatisfying(DomainException.class,
						e -> assertThat(e.getCode()).isEqualTo("TASK_STATUS_NOT_IN_SPACE"));
		service.changeStatus(task.getId(), null, done.getId());
		assertThat(task.getStatusId()).isEqualTo(done.getId());
		verify(audit).publish(eq(AuditAction.UPDATE), eq("Task"), eq(task.getId()), any(), any());
	}

	@Test
	void changeToTheSameStatusIsANoOp() {
		Task task = existingTask();
		service.changeStatus(task.getId(), null, todo.getId());
		verify(audit, never()).publish(any(AuditAction.class), any(), any(), any(), any());
		verify(events, never()).publishEvent(any());
	}

	@Test
	void changeStatusByANonAuthorIsForbidden() {
		Task task = existingTask();
		doThrow(new ForbiddenException("TASK_EDIT_FORBIDDEN")).when(editPolicy).assertCanChangeStatus(task);
		assertThatThrownBy(() -> service.changeStatus(task.getId(), null, done.getId())).isInstanceOf(ForbiddenException.class);
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

	@SuppressWarnings("unchecked")
	private static Map<String, Object> snapshotOf(Object value) {
		return (Map<String, Object>) value;
	}

}
