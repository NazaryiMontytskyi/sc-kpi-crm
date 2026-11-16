package com.kpi.studentcouncil.crm.notifications.internal;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.notifications.NotificationType;
import com.kpi.studentcouncil.crm.tasks.TaskEvent;

/**
 * Pure rules turning a {@link TaskEvent} into a notification (NT-6, NT-7, spec §8 Q5):
 * <ul>
 * <li>{@code ASSIGNED} / {@code UNASSIGNED} -> {@code TASK_ASSIGNED} / {@code TASK_UNASSIGNED} to the added / removed
 * assignee;</li>
 * <li>{@code DEADLINE_CHANGED} -> {@code TASK_DEADLINE_CHANGED}, {@code STATUS_CHANGED} -> {@code TASK_STATUS_CHANGED}
 * and {@code SUBTASK_ADDED} -> {@code SUBTASK_ADDED}, each to the people following the task: its creator and its
 * active assignees (of the parent for a subtask added);</li>
 * <li>every other kind -> nothing.</li>
 * </ul>
 * The actor is never notified of their own action, and the nil system actor is never a recipient.
 */
final class TaskNotificationRules {

	private TaskNotificationRules() {
	}

	/** The notification type of an event kind, or empty when the kind is not notified. */
	static Optional<NotificationType> type(TaskEvent.Kind kind) {
		return Optional.ofNullable(switch (kind) {
			case ASSIGNED -> NotificationType.TASK_ASSIGNED;
			case UNASSIGNED -> NotificationType.TASK_UNASSIGNED;
			case DEADLINE_CHANGED -> NotificationType.TASK_DEADLINE_CHANGED;
			case STATUS_CHANGED -> NotificationType.TASK_STATUS_CHANGED;
			case SUBTASK_ADDED -> NotificationType.SUBTASK_ADDED;
			case CREATED, UPDATED, ARCHIVED, RESTORED -> null;
		});
	}

	/** Whether recipients depend on the task's current assignees (so the caller must look them up). */
	static boolean needsAssignees(TaskEvent.Kind kind) {
		return kind == TaskEvent.Kind.DEADLINE_CHANGED || kind == TaskEvent.Kind.STATUS_CHANGED
				|| kind == TaskEvent.Kind.SUBTASK_ADDED;
	}

	/**
	 * Recipients of the event, the actor and the system actor excluded.
	 *
	 * @param activeAssignees active assignees of {@code event.id()}; ignored unless {@link #needsAssignees}
	 */
	static Set<UUID> recipients(TaskEvent event, Set<UUID> activeAssignees) {
		Set<UUID> people = new LinkedHashSet<>();
		switch (event.kind()) {
			case ASSIGNED, UNASSIGNED -> people.add(event.assigneeId());
			case DEADLINE_CHANGED, STATUS_CHANGED, SUBTASK_ADDED -> {
				people.add(event.authorId());
				if (activeAssignees != null) {
					people.addAll(activeAssignees);
				}
			}
			case CREATED, UPDATED, ARCHIVED, RESTORED -> {
				// not notified
			}
		}
		people.remove(null);
		people.remove(ActorProvider.SYSTEM_ACTOR);
		if (event.actorId() != null) {
			people.remove(event.actorId());
		}
		return people;
	}

}
