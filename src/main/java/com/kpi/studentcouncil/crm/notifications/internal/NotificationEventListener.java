package com.kpi.studentcouncil.crm.notifications.internal;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.kpi.studentcouncil.crm.notifications.NotificationTarget;
import com.kpi.studentcouncil.crm.notifications.NotificationType;
import com.kpi.studentcouncil.crm.tasks.ActiveTaskQuery;
import com.kpi.studentcouncil.crm.tasks.TaskEvent;
import com.kpi.studentcouncil.crm.tasks.UserMentioned;

/**
 * Turns domain events of {@code tasks} into notifications. Runs in the publisher's transaction, so a rolled-back
 * change leaves no notification. Knows only the channel-agnostic {@link NotificationDispatcher} and reads assignees
 * through the public {@link ActiveTaskQuery}.
 * The deprecated {@code RESOLUTION_*} notification types are never emitted (NT-6).
 */
@Component
class NotificationEventListener {

	static final String TASK_TARGET = "TASK";

	private final NotificationDispatcher dispatcher;

	private final ActiveTaskQuery tasks;

	NotificationEventListener(NotificationDispatcher dispatcher, ActiveTaskQuery tasks) {
		this.dispatcher = dispatcher;
		this.tasks = tasks;
	}

	@EventListener
	void on(TaskEvent event) {
		Optional<NotificationType> type = TaskNotificationRules.type(event.kind());
		if (type.isEmpty()) {
			return;
		}
		Set<UUID> assignees = TaskNotificationRules.needsAssignees(event.kind()) ? tasks.activeAssigneeIds(event.id())
				: Set.of();
		Set<UUID> recipients = TaskNotificationRules.recipients(event, assignees);
		if (recipients.isEmpty()) {
			return;
		}
		dispatcher.dispatch(recipients, event.actorId(), type.get(), target(event), payload(event));
	}

	@EventListener
	void on(UserMentioned event) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("commentId", event.commentId().toString());
		payload.put("targetType", event.targetType().name());
		payload.put("targetId", event.targetId().toString());
		payload.put("actorId", String.valueOf(event.authorId()));
		dispatcher.dispatch(Set.of(event.userId()), event.authorId(), NotificationType.MENTION,
				new NotificationTarget(event.targetType().name(), event.targetId()), payload);
	}

	/** The task the notification links to: the new subtask for {@code SUBTASK_ADDED}, else the task itself. */
	private static NotificationTarget target(TaskEvent event) {
		UUID id = event.kind() == TaskEvent.Kind.SUBTASK_ADDED && event.subtaskId() != null ? event.subtaskId()
				: event.id();
		return new NotificationTarget(TASK_TARGET, id);
	}

	/** Locale-independent details: ids and ISO dates only. */
	private static Map<String, Object> payload(TaskEvent event) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("taskId", event.id().toString());
		payload.put("actorId", String.valueOf(event.actorId()));
		switch (event.kind()) {
			case ASSIGNED, UNASSIGNED -> payload.put("assigneeId", String.valueOf(event.assigneeId()));
			case DEADLINE_CHANGED -> {
				putIfPresent(payload, "previousDeadline", event.previousDeadline());
				putIfPresent(payload, "deadline", event.deadline());
			}
			case STATUS_CHANGED -> {
				putIfPresent(payload, "previousStatusId", event.previousStatusId());
				putIfPresent(payload, "statusId", event.statusId());
			}
			case SUBTASK_ADDED -> payload.put("subtaskId", String.valueOf(event.subtaskId()));
			default -> {
				// no extra details
			}
		}
		return payload;
	}

	/** Payload values are never null (an absent deadline is an absent key). */
	private static void putIfPresent(Map<String, Object> payload, String key, Object value) {
		if (value != null) {
			payload.put(key, value.toString());
		}
	}

}
