package com.kpi.studentcouncil.crm.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.notifications.NotificationChannel;
import com.kpi.studentcouncil.crm.notifications.NotificationMessage;
import com.kpi.studentcouncil.crm.notifications.NotificationTarget;
import com.kpi.studentcouncil.crm.notifications.NotificationType;
import com.kpi.studentcouncil.crm.tasks.ActiveTaskQuery;
import com.kpi.studentcouncil.crm.tasks.CommentTargetType;
import com.kpi.studentcouncil.crm.tasks.TaskEvent;
import com.kpi.studentcouncil.crm.tasks.TaskEvent.Kind;
import com.kpi.studentcouncil.crm.tasks.UserMentioned;

/**
 * Recipient rules of the listener ({@link TaskNotificationRules}) and the dispatcher, with a recording channel and a
 * stubbed {@link ActiveTaskQuery} instead of the database (NT-6, NT-7, spec §8 Q5).
 */
class NotificationDispatchTest {

	private final List<NotificationMessage> delivered = new ArrayList<>();

	private final NotificationChannel recording = delivered::add;

	private final UUID creator = UUID.randomUUID();
	private final UUID assignee = UUID.randomUUID();
	private final UUID coAssignee = UUID.randomUUID();
	private final UUID outsider = UUID.randomUUID();
	private final UUID task = UUID.randomUUID();
	private final UUID space = UUID.randomUUID();
	private final UUID status = UUID.randomUUID();

	/** Active assignees the stubbed query returns for {@link #task}. */
	private final Set<UUID> activeAssignees = new LinkedHashSet<>(List.of(assignee, coAssignee));

	private final ActiveTaskQuery query = new ActiveTaskQuery() {
		@Override
		public List<ActiveTask> findOpenDueOnOrBefore(LocalDate latestDeadline) {
			return List.of();
		}

		@Override
		public Set<UUID> activeAssigneeIds(UUID taskId) {
			return taskId.equals(task) ? activeAssignees : Set.of();
		}
	};

	private final NotificationEventListener listener = new NotificationEventListener(
			new NotificationDispatcher(List.of(recording)), query);

	private TaskEvent event(Kind kind, UUID actor, UUID assigneeId) {
		return new TaskEvent(kind, task, space, status, creator, actor, assigneeId, null, null, null);
	}

	private Set<UUID> recipients() {
		return Set.copyOf(delivered.stream().map(NotificationMessage::recipientId).toList());
	}

	@Test
	void assignedAndUnassignedGoToThatAssigneeOnly() {
		listener.on(event(Kind.ASSIGNED, creator, outsider));
		listener.on(event(Kind.UNASSIGNED, creator, outsider));
		assertThat(recipients()).containsExactly(outsider);
		assertThat(delivered).extracting(NotificationMessage::type).containsExactly(NotificationType.TASK_ASSIGNED,
				NotificationType.TASK_UNASSIGNED);
		assertThat(delivered).allSatisfy(m -> {
			assertThat(m.target()).isEqualTo(new NotificationTarget("TASK", task));
			assertThat(m.payload()).containsEntry("taskId", task.toString())
					.containsEntry("assigneeId", outsider.toString()).containsEntry("actorId", creator.toString());
		});
	}

	@Test
	void selfAssignmentNotifiesNobody() {
		listener.on(event(Kind.ASSIGNED, outsider, outsider));
		listener.on(event(Kind.UNASSIGNED, outsider, outsider));
		assertThat(delivered).isEmpty();
	}

	@Test
	void statusChangeByAnAssigneeGoesToTheCreatorAndTheOtherAssignees() {
		UUID previous = UUID.randomUUID();
		listener.on(new TaskEvent(Kind.STATUS_CHANGED, task, space, status, creator, assignee, null, previous, null,
				null));
		assertThat(recipients()).containsExactlyInAnyOrder(creator, coAssignee);
		assertThat(delivered).allSatisfy(m -> {
			assertThat(m.type()).isEqualTo(NotificationType.TASK_STATUS_CHANGED);
			assertThat(m.payload()).containsEntry("previousStatusId", previous.toString())
					.containsEntry("statusId", status.toString());
		});
	}

	@Test
	void deadlineChangeByTheCreatorGoesToTheAssignees() {
		listener.on(new TaskEvent(Kind.DEADLINE_CHANGED, task, space, status, creator, creator, null, null, null,
				LocalDate.of(2030, 6, 10)));
		assertThat(recipients()).containsExactlyInAnyOrder(assignee, coAssignee);
		assertThat(delivered).allSatisfy(m -> {
			assertThat(m.type()).isEqualTo(NotificationType.TASK_DEADLINE_CHANGED);
			// a removed previous deadline is an absent key, never null
			assertThat(m.payload()).containsEntry("deadline", "2030-06-10").doesNotContainKey("previousDeadline");
		});
	}

	@Test
	void subtaskAddedGoesToTheParentsCreatorAndAssigneesAndLinksToTheSubtask() {
		UUID subtask = UUID.randomUUID();
		listener.on(new TaskEvent(Kind.SUBTASK_ADDED, task, space, status, creator, coAssignee, null, null, null, null,
				subtask));
		assertThat(recipients()).containsExactlyInAnyOrder(creator, assignee);
		assertThat(delivered).allSatisfy(m -> {
			assertThat(m.type()).isEqualTo(NotificationType.SUBTASK_ADDED);
			assertThat(m.target()).isEqualTo(new NotificationTarget("TASK", subtask));
			assertThat(m.payload()).containsEntry("taskId", task.toString())
					.containsEntry("subtaskId", subtask.toString());
		});
	}

	@Test
	void taskWithoutAssigneesNotifiesOnlyTheCreatorOfAStatusChange() {
		activeAssignees.clear();
		listener.on(new TaskEvent(Kind.STATUS_CHANGED, task, space, status, creator, outsider, null, null, null, null));
		assertThat(recipients()).containsExactly(creator);
	}

	@Test
	void creatorChangingStatusOfAnUnassignedTaskNotifiesNobody() {
		activeAssignees.clear();
		listener.on(new TaskEvent(Kind.STATUS_CHANGED, task, space, status, creator, creator, null, null, null, null));
		assertThat(delivered).isEmpty();
	}

	@Test
	void plainLifecycleEventsNotifyNobody() {
		for (Kind kind : List.of(Kind.CREATED, Kind.UPDATED, Kind.ARCHIVED, Kind.RESTORED)) {
			listener.on(event(kind, outsider, null));
		}
		assertThat(delivered).isEmpty();
	}

	@Test
	void theSystemActorCreatorIsNeverARecipient() {
		listener.on(new TaskEvent(Kind.STATUS_CHANGED, task, space, status, ActorProvider.SYSTEM_ACTOR, assignee, null,
				null, null, null));
		assertThat(recipients()).containsExactly(coAssignee);
	}

	@Test
	void rulesMapEveryNotifiedKindToItsType() {
		assertThat(TaskNotificationRules.type(Kind.ASSIGNED)).contains(NotificationType.TASK_ASSIGNED);
		assertThat(TaskNotificationRules.type(Kind.UNASSIGNED)).contains(NotificationType.TASK_UNASSIGNED);
		assertThat(TaskNotificationRules.type(Kind.DEADLINE_CHANGED))
				.contains(NotificationType.TASK_DEADLINE_CHANGED);
		assertThat(TaskNotificationRules.type(Kind.STATUS_CHANGED)).contains(NotificationType.TASK_STATUS_CHANGED);
		assertThat(TaskNotificationRules.type(Kind.SUBTASK_ADDED)).contains(NotificationType.SUBTASK_ADDED);
		assertThat(TaskNotificationRules.type(Kind.CREATED)).isEmpty();
		assertThat(TaskNotificationRules.needsAssignees(Kind.ASSIGNED)).isFalse();
		assertThat(TaskNotificationRules.needsAssignees(Kind.STATUS_CHANGED)).isTrue();
	}

	@Test
	void mentionGoesToTheMentionedUserUnlessSelf() {
		UUID comment = UUID.randomUUID();
		listener.on(new UserMentioned(outsider, comment, CommentTargetType.TASK, task, creator));
		listener.on(new UserMentioned(creator, comment, CommentTargetType.TASK, task, creator));
		assertThat(delivered).singleElement().satisfies(m -> {
			assertThat(m.recipientId()).isEqualTo(outsider);
			assertThat(m.type()).isEqualTo(NotificationType.MENTION);
			assertThat(m.target().type()).isEqualTo("TASK");
			assertThat(m.target().id()).isEqualTo(task);
		});
	}

	@Test
	void everyRegisteredChannelReceivesEachMessageOnceAndRecipientsAreDeduplicated() {
		List<NotificationMessage> second = new ArrayList<>();
		NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(recording, second::add));
		UUID user = UUID.randomUUID();
		dispatcher.dispatch(Set.of(user), null, NotificationType.MENTION, new NotificationTarget("TASK", task),
				Map.of());
		assertThat(delivered).hasSize(1);
		assertThat(second).hasSize(1);
	}

}
