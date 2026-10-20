package com.kpi.studentcouncil.crm.tasks.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;
import com.kpi.studentcouncil.crm.shared.error.DomainException;
import com.kpi.studentcouncil.crm.shared.error.ForbiddenException;

class ResolutionCascadeTest {

	private static final Instant T0 = Instant.parse("2026-03-10T10:00:00Z");
	private static final Instant CANCEL_AT = Instant.parse("2026-03-11T10:00:00Z");

	private final UUID task = UUID.randomUUID();
	private final UUID head = UUID.randomUUID();
	private final UUID deputy = UUID.randomUUID();
	private final UUID chief = UUID.randomUUID();
	private final UUID member = UUID.randomUUID();

	/** parent id -> children, the lookup the service gets from the repository */
	private final Map<UUID, List<Resolution>> children = new HashMap<>();

	private Resolution make(UUID author, UUID executor, Resolution parent) {
		Resolution r = new Resolution(task, author, executor, Set.of(), "Do it", LocalDate.of(2026, 4, 1),
				parent == null ? null : parent.getId(), T0);
		ReflectionTestUtils.setField(r, "id", UUID.randomUUID());
		if (parent != null) {
			children.computeIfAbsent(parent.getId(), k -> new ArrayList<>()).add(r);
		}
		return r;
	}

	/** Moves the resolution through the legal path to the wanted state (author and executor are its own). */
	private static void drive(Resolution r, ResolutionState target) {
		UUID author = r.getAuthorId();
		UUID executor = r.getExecutorId();
		switch (target) {
			case NEW -> {
			}
			case IN_PROGRESS -> r.apply(ResolutionAction.START, executor, null, T0);
			case REDIRECTED -> {
				r.apply(ResolutionAction.START, executor, null, T0);
				r.apply(ResolutionAction.REDIRECT, executor, null, T0);
			}
			case SUBMITTED -> {
				r.apply(ResolutionAction.START, executor, null, T0);
				r.apply(ResolutionAction.SUBMIT, executor, "done", T0);
			}
			case REWORK -> {
				drive(r, ResolutionState.SUBMITTED);
				r.apply(ResolutionAction.REQUEST_REWORK, author, "again", T0);
			}
			case ACCEPTED -> {
				drive(r, ResolutionState.SUBMITTED);
				r.apply(ResolutionAction.ACCEPT, author, null, T0);
			}
			case CANCELED -> r.apply(ResolutionAction.CANCEL, author, null, T0);
		}
	}

	private List<Resolution> cancel(Resolution root, UUID actor, String comment) {
		return ResolutionCascade.cancel(root, actor, comment, CANCEL_AT, id -> children.getOrDefault(id, List.of()));
	}

	@Test
	void aFourLevelChainIsCanceledCompletelyWithHistoryReferencingTheRoot() {
		// Head -> Deputy -> Head of Department -> Member
		Resolution r1 = make(head, deputy, null);
		Resolution r2 = make(deputy, chief, r1);
		Resolution r3 = make(chief, member, r2);
		Resolution r4 = make(member, member, r3);
		drive(r1, ResolutionState.REDIRECTED);
		drive(r2, ResolutionState.REDIRECTED);
		drive(r3, ResolutionState.REDIRECTED);
		drive(r4, ResolutionState.IN_PROGRESS);

		List<Resolution> changed = cancel(r1, head, "  not needed  ");

		assertThat(changed).containsExactly(r1, r2, r3, r4);
		for (Resolution r : changed) {
			assertThat(r.getState()).isEqualTo(ResolutionState.CANCELED);
			ResolutionHistoryEntry last = r.getHistory().get(r.getHistory().size() - 1);
			assertThat(last.to()).isEqualTo(ResolutionState.CANCELED);
			assertThat(last.at()).isEqualTo(CANCEL_AT);
			assertThat(last.actorId()).isEqualTo(head);
			assertThat(last.comment()).isEqualTo("not needed");
		}
		assertThat(r1.getHistory().get(r1.getHistory().size() - 1).causedByResolutionId()).isNull();
		for (Resolution descendant : List.of(r2, r3, r4)) {
			assertThat(descendant.getHistory().get(descendant.getHistory().size() - 1).causedByResolutionId())
					.isEqualTo(r1.getId());
		}
		assertThat(r4.getHistory().get(r4.getHistory().size() - 1).from()).isEqualTo(ResolutionState.IN_PROGRESS);
	}

	@Test
	void everyNonFinalStateOfADescendantIsCanceled() {
		for (ResolutionState state : new ResolutionState[] { ResolutionState.NEW, ResolutionState.IN_PROGRESS,
				ResolutionState.SUBMITTED, ResolutionState.REWORK, ResolutionState.REDIRECTED }) {
			children.clear();
			Resolution root = make(head, deputy, null);
			drive(root, ResolutionState.REDIRECTED);
			Resolution child = make(deputy, member, root);
			drive(child, state);

			cancel(root, head, null);

			assertThat(child.getState()).as(state.name()).isEqualTo(ResolutionState.CANCELED);
		}
	}

	@Test
	void acceptedAndCanceledDescendantsAreLeftUntouchedWhileTheirSiblingsAreCanceled() {
		Resolution root = make(head, deputy, null);
		drive(root, ResolutionState.REDIRECTED);
		Resolution accepted = make(deputy, member, root);
		Resolution canceled = make(deputy, member, root);
		Resolution open = make(deputy, chief, root);
		Resolution openGrandchild = make(chief, member, open);
		drive(accepted, ResolutionState.ACCEPTED);
		drive(canceled, ResolutionState.CANCELED);
		drive(open, ResolutionState.REDIRECTED);
		drive(openGrandchild, ResolutionState.REWORK);
		int acceptedHistory = accepted.getHistory().size();
		int canceledHistory = canceled.getHistory().size();

		List<Resolution> changed = cancel(root, head, "stop");

		assertThat(changed).containsExactly(root, open, openGrandchild);
		assertThat(accepted.getState()).isEqualTo(ResolutionState.ACCEPTED);
		assertThat(accepted.getHistory()).hasSize(acceptedHistory);
		assertThat(canceled.getState()).isEqualTo(ResolutionState.CANCELED);
		assertThat(canceled.getHistory()).hasSize(canceledHistory);
		assertThat(open.getState()).isEqualTo(ResolutionState.CANCELED);
		assertThat(openGrandchild.getState()).isEqualTo(ResolutionState.CANCELED);
	}

	@Test
	void aNonFinalDescendantBehindAFinalOneIsStillReached() {
		Resolution root = make(head, deputy, null);
		drive(root, ResolutionState.REDIRECTED);
		Resolution middle = make(deputy, chief, root);
		Resolution leaf = make(chief, member, middle);
		drive(middle, ResolutionState.CANCELED);
		drive(leaf, ResolutionState.IN_PROGRESS);

		List<Resolution> changed = cancel(root, head, null);

		assertThat(changed).containsExactly(root, leaf);
		assertThat(leaf.getState()).isEqualTo(ResolutionState.CANCELED);
	}

	@Test
	void aResolutionWithoutChildrenIsJustCanceled() {
		Resolution root = make(head, deputy, null);
		assertThat(cancel(root, head, null)).containsExactly(root);
		assertThat(root.getState()).isEqualTo(ResolutionState.CANCELED);
	}

	@Test
	void aRejectedRootCancelChangesNothingAnywhereInTheTree() {
		Resolution root = make(head, deputy, null);
		drive(root, ResolutionState.REDIRECTED);
		Resolution child = make(deputy, member, root);
		drive(child, ResolutionState.IN_PROGRESS);
		int rootHistory = root.getHistory().size();
		int childHistory = child.getHistory().size();

		// not the author
		assertThatThrownBy(() -> cancel(root, deputy, null)).isInstanceOf(ForbiddenException.class)
				.extracting(e -> ((DomainException) e).getCode()).isEqualTo("RESOLUTION_NOT_AUTHOR");
		assertThat(root.getState()).isEqualTo(ResolutionState.REDIRECTED);
		assertThat(child.getState()).isEqualTo(ResolutionState.IN_PROGRESS);
		assertThat(root.getHistory()).hasSize(rootHistory);
		assertThat(child.getHistory()).hasSize(childHistory);

		// root already final
		drive(root, ResolutionState.CANCELED);
		int afterCancel = root.getHistory().size();
		assertThatThrownBy(() -> cancel(root, head, null)).isInstanceOf(BusinessRuleException.class);
		assertThat(child.getState()).isEqualTo(ResolutionState.IN_PROGRESS);
		assertThat(root.getHistory()).hasSize(afterCancel);
	}

	@Test
	void aFailureWhileLookingUpChildrenLeavesTheWholeTreeUnchanged() {
		Resolution root = make(head, deputy, null);
		drive(root, ResolutionState.REDIRECTED);
		Resolution child = make(deputy, chief, root);
		drive(child, ResolutionState.REDIRECTED);
		Resolution grandchild = make(chief, member, child);
		drive(grandchild, ResolutionState.IN_PROGRESS);

		assertThatThrownBy(() -> ResolutionCascade.cancel(root, head, null, CANCEL_AT, id -> {
			if (id.equals(child.getId())) {
				throw new IllegalStateException("lookup failed");
			}
			return children.getOrDefault(id, List.of());
		})).isInstanceOf(IllegalStateException.class);

		// planning happens before any mutation, so nothing was touched
		assertThat(root.getState()).isEqualTo(ResolutionState.REDIRECTED);
		assertThat(child.getState()).isEqualTo(ResolutionState.REDIRECTED);
		assertThat(grandchild.getState()).isEqualTo(ResolutionState.IN_PROGRESS);
	}

	@Test
	void aCyclicParentLinkDoesNotLoopForever() {
		Resolution root = make(head, deputy, null);
		Resolution child = make(deputy, member, root);
		children.computeIfAbsent(child.getId(), k -> new ArrayList<>()).add(root);

		assertThat(cancel(root, head, null)).containsExactly(root, child);
	}

}
