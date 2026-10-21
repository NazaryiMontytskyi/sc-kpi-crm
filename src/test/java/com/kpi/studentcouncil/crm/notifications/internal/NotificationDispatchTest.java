package com.kpi.studentcouncil.crm.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.kpi.studentcouncil.crm.notifications.NotificationChannel;
import com.kpi.studentcouncil.crm.notifications.NotificationMessage;
import com.kpi.studentcouncil.crm.notifications.NotificationType;
import com.kpi.studentcouncil.crm.tasks.ChildResolutionAccepted;
import com.kpi.studentcouncil.crm.tasks.CommentTargetType;
import com.kpi.studentcouncil.crm.tasks.ResolutionEvent;
import com.kpi.studentcouncil.crm.tasks.ResolutionEvent.Kind;
import com.kpi.studentcouncil.crm.tasks.UserMentioned;

/** Recipient rules of the listener and the dispatcher, with a recording channel instead of the database. */
class NotificationDispatchTest {

	private final List<NotificationMessage> delivered = new ArrayList<>();

	private final NotificationChannel recording = delivered::add;

	private final NotificationEventListener listener = new NotificationEventListener(
			new NotificationDispatcher(List.of(recording)));

	private final UUID author = UUID.randomUUID();
	private final UUID executor = UUID.randomUUID();
	private final UUID coExecutor = UUID.randomUUID();
	private final UUID resolution = UUID.randomUUID();
	private final UUID task = UUID.randomUUID();

	private ResolutionEvent event(Kind kind, UUID actor) {
		return new ResolutionEvent(kind, resolution, task, author, executor, Set.of(coExecutor), actor);
	}

	private Set<UUID> recipients() {
		return Set.copyOf(delivered.stream().map(NotificationMessage::recipientId).toList());
	}

	@Test
	void issuedGoesToExecutorAndCoExecutors() {
		listener.on(event(Kind.ISSUED, author));
		assertThat(recipients()).containsExactlyInAnyOrder(executor, coExecutor);
		assertThat(delivered).allSatisfy(m -> {
			assertThat(m.type()).isEqualTo(NotificationType.RESOLUTION_ISSUED);
			assertThat(m.target().type()).isEqualTo("RESOLUTION");
			assertThat(m.target().id()).isEqualTo(resolution);
			assertThat(m.payload()).containsEntry("taskId", task.toString());
		});
	}

	@Test
	void submittedAndRedirectedGoToTheAuthor() {
		listener.on(event(Kind.SUBMITTED, executor));
		listener.on(event(Kind.REDIRECTED, executor));
		assertThat(recipients()).containsExactly(author);
		assertThat(delivered).extracting(NotificationMessage::type)
				.containsExactly(NotificationType.RESOLUTION_SUBMITTED, NotificationType.RESOLUTION_REDIRECTED);
	}

	@Test
	void acceptedAndReworkGoToExecutors() {
		listener.on(event(Kind.CLOSED, author));
		listener.on(event(Kind.REWORKED, author));
		assertThat(recipients()).containsExactlyInAnyOrder(executor, coExecutor);
		assertThat(delivered).extracting(NotificationMessage::type).contains(NotificationType.RESOLUTION_ACCEPTED,
				NotificationType.RESOLUTION_REWORK);
		assertThat(delivered).hasSize(4);
	}

	@Test
	void takingIntoWorkAndCancelNotifyNobody() {
		listener.on(event(Kind.ACCEPTED, executor));
		listener.on(event(Kind.CANCELED, author));
		assertThat(delivered).isEmpty();
	}

	@Test
	void theActorIsNeverNotifiedAboutOwnAction() {
		// the executor issues to themself and to a co-executor
		listener.on(event(Kind.ISSUED, executor));
		assertThat(recipients()).containsExactly(coExecutor);
		delivered.clear();
		// the author submits (unusual, but the author is the actor)
		listener.on(event(Kind.SUBMITTED, author));
		assertThat(delivered).isEmpty();
	}

	@Test
	void childAcceptedGoesToTheParentExecutorEvenWhenTheyAreTheActor() {
		UUID parent = UUID.randomUUID();
		UUID child = UUID.randomUUID();
		listener.on(new ChildResolutionAccepted(child, parent, task, executor, UUID.randomUUID(), executor));
		assertThat(delivered).singleElement().satisfies(m -> {
			assertThat(m.recipientId()).isEqualTo(executor);
			assertThat(m.type()).isEqualTo(NotificationType.RESOLUTION_CHILD_ACCEPTED);
			assertThat(m.target().id()).isEqualTo(parent);
			assertThat(m.payload()).containsEntry("childResolutionId", child.toString());
		});
	}

	@Test
	void mentionGoesToTheMentionedUserUnlessSelf() {
		UUID mentioned = UUID.randomUUID();
		UUID comment = UUID.randomUUID();
		listener.on(new UserMentioned(mentioned, comment, CommentTargetType.TASK, task, author));
		listener.on(new UserMentioned(author, comment, CommentTargetType.TASK, task, author));
		assertThat(delivered).singleElement().satisfies(m -> {
			assertThat(m.recipientId()).isEqualTo(mentioned);
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
		dispatcher.dispatch(Set.of(user), null, NotificationType.MENTION,
				new com.kpi.studentcouncil.crm.notifications.NotificationTarget("TASK", task), Map.of());
		assertThat(delivered).hasSize(1);
		assertThat(second).hasSize(1);
	}

}
