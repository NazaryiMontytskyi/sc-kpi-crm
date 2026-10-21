package com.kpi.studentcouncil.crm.notifications.internal;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.kpi.studentcouncil.crm.notifications.NotificationTarget;
import com.kpi.studentcouncil.crm.notifications.NotificationType;
import com.kpi.studentcouncil.crm.tasks.ChildResolutionAccepted;
import com.kpi.studentcouncil.crm.tasks.CommentTargetType;
import com.kpi.studentcouncil.crm.tasks.ResolutionEvent;
import com.kpi.studentcouncil.crm.tasks.UserMentioned;

/**
 * Turns domain events of {@code tasks} into notifications. Runs in the publisher's transaction, so a rolled-back
 * change leaves no notification. Knows only the channel-agnostic {@link NotificationDispatcher}.
 */
@Component
class NotificationEventListener {

	private final NotificationDispatcher dispatcher;

	NotificationEventListener(NotificationDispatcher dispatcher) {
		this.dispatcher = dispatcher;
	}

	@EventListener
	void on(ResolutionEvent event) {
		NotificationTarget target = new NotificationTarget("RESOLUTION", event.id());
		Map<String, Object> payload = resolutionPayload(event);
		switch (event.kind()) {
			case ISSUED -> dispatcher.dispatch(executors(event), event.actorId(), NotificationType.RESOLUTION_ISSUED,
					target, payload);
			case REDIRECTED -> dispatcher.dispatch(Set.of(event.authorId()), event.actorId(),
					NotificationType.RESOLUTION_REDIRECTED, target, payload);
			case SUBMITTED -> dispatcher.dispatch(Set.of(event.authorId()), event.actorId(),
					NotificationType.RESOLUTION_SUBMITTED, target, payload);
			case CLOSED -> dispatcher.dispatch(executors(event), event.actorId(),
					NotificationType.RESOLUTION_ACCEPTED, target, payload);
			case REWORKED -> dispatcher.dispatch(executors(event), event.actorId(), NotificationType.RESOLUTION_REWORK,
					target, payload);
			case ACCEPTED, CANCELED -> {
				// not notified in this stage
			}
		}
	}

	@EventListener
	void on(ChildResolutionAccepted event) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("taskId", event.taskId().toString());
		payload.put("resolutionId", event.parentId().toString());
		payload.put("childResolutionId", event.childId().toString());
		payload.put("actorId", String.valueOf(event.actorId()));
		// Deliberately not actor-suppressed: only the child's author can accept it, and that is always the redirecting
		// (parent) executor, so suppression would make this notification impossible. It is the reminder to submit the
		// parent resolution (CONTEXT.md 5.3).
		dispatcher.dispatch(Set.of(event.parentExecutorId()), null,
				NotificationType.RESOLUTION_CHILD_ACCEPTED, new NotificationTarget("RESOLUTION", event.parentId()),
				payload);
	}

	@EventListener
	void on(UserMentioned event) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("commentId", event.commentId().toString());
		payload.put("targetType", event.targetType().name());
		payload.put("targetId", event.targetId().toString());
		payload.put("actorId", String.valueOf(event.authorId()));
		String type = event.targetType() == CommentTargetType.RESOLUTION ? "RESOLUTION" : "TASK";
		dispatcher.dispatch(Set.of(event.userId()), event.authorId(), NotificationType.MENTION,
				new NotificationTarget(type, event.targetId()), payload);
	}

	private static Set<UUID> executors(ResolutionEvent event) {
		Set<UUID> people = new LinkedHashSet<>();
		people.add(event.executorId());
		if (event.coExecutorIds() != null) {
			people.addAll(event.coExecutorIds());
		}
		return people;
	}

	private static Map<String, Object> resolutionPayload(ResolutionEvent event) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("taskId", event.taskId().toString());
		payload.put("resolutionId", event.id().toString());
		payload.put("actorId", String.valueOf(event.actorId()));
		return payload;
	}

}
