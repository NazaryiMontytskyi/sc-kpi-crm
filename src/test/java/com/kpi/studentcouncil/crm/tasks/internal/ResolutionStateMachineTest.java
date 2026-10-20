package com.kpi.studentcouncil.crm.tasks.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;
import com.kpi.studentcouncil.crm.shared.error.DomainException;
import com.kpi.studentcouncil.crm.shared.error.ForbiddenException;

import static com.kpi.studentcouncil.crm.tasks.internal.ResolutionAction.*;
import static com.kpi.studentcouncil.crm.tasks.internal.ResolutionState.*;

class ResolutionStateMachineTest {

	private static final Instant NOW = Instant.parse("2026-03-10T10:00:00Z");

	private final UUID author = UUID.randomUUID();
	private final UUID executor = UUID.randomUUID();
	private final UUID stranger = UUID.randomUUID();

	/** The complete allowed set of CONTEXT.md 5.3; everything else must be rejected. */
	private static Map<ResolutionAction, Map<ResolutionState, ResolutionState>> allowed() {
		Map<ResolutionAction, Map<ResolutionState, ResolutionState>> map = new EnumMap<>(ResolutionAction.class);
		map.put(START, Map.of(NEW, IN_PROGRESS));
		map.put(SUBMIT, Map.of(IN_PROGRESS, SUBMITTED));
		map.put(ACCEPT, Map.of(SUBMITTED, ACCEPTED));
		map.put(REQUEST_REWORK, Map.of(SUBMITTED, REWORK));
		map.put(RESUME, Map.of(REWORK, IN_PROGRESS));
		map.put(REDIRECT, Map.of(IN_PROGRESS, REDIRECTED));
		map.put(CANCEL, Map.of(NEW, CANCELED, IN_PROGRESS, CANCELED, SUBMITTED, CANCELED, REWORK, CANCELED,
				REDIRECTED, CANCELED));
		return map;
	}

	private UUID actorFor(ResolutionAction action) {
		return ResolutionStateMachine.requiredRole(action) == ResolutionStateMachine.Role.EXECUTOR ? executor : author;
	}

	private static String commentFor(ResolutionAction action) {
		return action == SUBMIT || action == REQUEST_REWORK ? "text" : null;
	}

	@Test
	void everyStateActionPairFollowsTheSpecificationExactly() {
		int allowedCount = 0;
		int forbiddenCount = 0;
		for (ResolutionAction action : ResolutionAction.values()) {
			for (ResolutionState from : ResolutionState.values()) {
				ResolutionState expected = allowed().get(action).get(from);
				if (expected != null) {
					assertThat(ResolutionStateMachine.check(from, action, actorFor(action), author, executor,
							commentFor(action))).as("%s from %s", action, from).isEqualTo(expected);
					allowedCount++;
				}
				else {
					assertThatThrownBy(() -> ResolutionStateMachine.check(from, action, actorFor(action), author,
							executor, commentFor(action))).as("%s from %s", action, from)
							.isInstanceOf(BusinessRuleException.class)
							.extracting(e -> ((DomainException) e).getCode())
							.isEqualTo("RESOLUTION_TRANSITION_NOT_ALLOWED");
					forbiddenCount++;
				}
			}
		}
		assertThat(allowedCount).isEqualTo(11);
		assertThat(forbiddenCount).isEqualTo(7 * 7 - 11);
	}

	@Test
	void finalStatesAreLeftByNoAction() {
		for (ResolutionState finalState : new ResolutionState[] { ACCEPTED, CANCELED }) {
			assertThat(finalState.isFinal()).isTrue();
			for (ResolutionAction action : ResolutionAction.values()) {
				assertThatThrownBy(() -> ResolutionStateMachine.target(finalState, action))
						.isInstanceOf(BusinessRuleException.class);
			}
		}
		for (ResolutionState open : new ResolutionState[] { NEW, IN_PROGRESS, SUBMITTED, REWORK, REDIRECTED }) {
			assertThat(open.isFinal()).isFalse();
		}
	}

	@Test
	void onlyTheExecutorMayStartSubmitResumeAndRedirect() {
		for (ResolutionAction action : new ResolutionAction[] { START, SUBMIT, RESUME, REDIRECT }) {
			ResolutionState from = allowed().get(action).keySet().iterator().next();
			for (UUID wrong : new UUID[] { author, stranger }) {
				assertThatThrownBy(() -> ResolutionStateMachine.check(from, action, wrong, author, executor, "text"))
						.isInstanceOf(ForbiddenException.class)
						.extracting(e -> ((DomainException) e).getCode()).isEqualTo("RESOLUTION_NOT_EXECUTOR");
			}
			assertThat(ResolutionStateMachine.check(from, action, executor, author, executor, "text")).isNotNull();
		}
	}

	@Test
	void onlyTheAuthorMayAcceptRequestReworkAndCancel() {
		for (ResolutionAction action : new ResolutionAction[] { ACCEPT, REQUEST_REWORK, CANCEL }) {
			ResolutionState from = SUBMITTED;
			for (UUID wrong : new UUID[] { executor, stranger }) {
				assertThatThrownBy(() -> ResolutionStateMachine.check(from, action, wrong, author, executor, "text"))
						.isInstanceOf(ForbiddenException.class)
						.extracting(e -> ((DomainException) e).getCode()).isEqualTo("RESOLUTION_NOT_AUTHOR");
			}
			assertThat(ResolutionStateMachine.check(from, action, author, author, executor, "text")).isNotNull();
		}
	}

	@Test
	void aMissingActorIsRejected() {
		assertThatThrownBy(() -> ResolutionStateMachine.check(NEW, START, null, author, executor, null))
				.isInstanceOf(ForbiddenException.class);
		assertThatThrownBy(() -> ResolutionStateMachine.check(NEW, CANCEL, null, author, executor, null))
				.isInstanceOf(ForbiddenException.class);
	}

	@Test
	void theTransitionIsCheckedBeforeTheActor() {
		assertThatThrownBy(() -> ResolutionStateMachine.check(ACCEPTED, CANCEL, stranger, author, executor, null))
				.isInstanceOf(BusinessRuleException.class);
	}

	@Test
	void aBlankCompletionNoteIsRejected() {
		for (String blank : new String[] { null, "", "   ", "\n\t" }) {
			assertThatThrownBy(() -> ResolutionStateMachine.check(IN_PROGRESS, SUBMIT, executor, author, executor, blank))
					.isInstanceOf(BusinessRuleException.class)
					.extracting(e -> ((DomainException) e).getCode())
					.isEqualTo("RESOLUTION_COMPLETION_NOTE_REQUIRED");
		}
	}

	@Test
	void aBlankReworkCommentIsRejected() {
		for (String blank : new String[] { null, "", "   ", "\n\t" }) {
			assertThatThrownBy(() -> ResolutionStateMachine.check(SUBMITTED, REQUEST_REWORK, author, author, executor,
					blank)).isInstanceOf(BusinessRuleException.class)
					.extracting(e -> ((DomainException) e).getCode())
					.isEqualTo("RESOLUTION_REWORK_COMMENT_REQUIRED");
		}
	}

	@Test
	void cancelAcceptAndStartNeedNoComment() {
		assertThat(ResolutionStateMachine.check(NEW, CANCEL, author, author, executor, null)).isEqualTo(CANCELED);
		assertThat(ResolutionStateMachine.check(SUBMITTED, ACCEPT, author, author, executor, "")).isEqualTo(ACCEPTED);
		assertThat(ResolutionStateMachine.check(NEW, START, executor, author, executor, null)).isEqualTo(IN_PROGRESS);
	}

	// ---- overdue ----

	@Test
	void overdueMeansDueDateInThePastAndNotFinal() {
		Clock clock = Clock.fixed(Instant.parse("2026-03-10T12:00:00Z"), ZoneOffset.UTC);
		LocalDate yesterday = LocalDate.of(2026, 3, 9);
		LocalDate today = LocalDate.of(2026, 3, 10);
		LocalDate tomorrow = LocalDate.of(2026, 3, 11);
		for (ResolutionState state : ResolutionState.values()) {
			assertThat(ResolutionStateMachine.isOverdue(state, yesterday, clock)).as("%s yesterday", state)
					.isEqualTo(!state.isFinal());
			assertThat(ResolutionStateMachine.isOverdue(state, today, clock)).as("%s today", state).isFalse();
			assertThat(ResolutionStateMachine.isOverdue(state, tomorrow, clock)).as("%s tomorrow", state).isFalse();
		}
		assertThat(ResolutionStateMachine.isOverdue(NEW, null, clock)).isFalse();
	}

	@Test
	void overdueIsDecidedByTheKyivCalendarDay() {
		// 22:30 UTC on 9 March is already 10 March in Kyiv (UTC+2): a resolution due on 9 March is overdue
		Clock clock = Clock.fixed(Instant.parse("2026-03-09T22:30:00Z"), ZoneOffset.UTC);
		assertThat(ResolutionStateMachine.isOverdue(IN_PROGRESS, LocalDate.of(2026, 3, 9), clock)).isTrue();
		assertThat(ResolutionStateMachine.isOverdue(IN_PROGRESS, LocalDate.of(2026, 3, 10), clock)).isFalse();
	}

	// ---- entity: history and effects ----

	private Resolution resolution() {
		Resolution r = new Resolution(UUID.randomUUID(), author, executor, Set.of(UUID.randomUUID()), "  Do it  ",
				LocalDate.of(2026, 4, 1), null, NOW);
		ReflectionTestUtils.setField(r, "id", UUID.randomUUID());
		return r;
	}

	@Test
	void creationStartsInNewWithAHistoryEntryAndNormalisedFields() {
		Resolution r = resolution();
		assertThat(r.getState()).isEqualTo(NEW);
		assertThat(r.getInstruction()).isEqualTo("Do it");
		assertThat(r.getHistory()).containsExactly(new ResolutionHistoryEntry(author, null, NEW, null, NOW, null));
	}

	@Test
	void theExecutorIsNeverAlsoACoExecutor() {
		Resolution r = new Resolution(UUID.randomUUID(), author, executor, Set.of(executor), "x",
				LocalDate.of(2026, 4, 1), null, NOW);
		assertThat(r.getCoExecutorIds()).isEmpty();
	}

	@Test
	void creationRejectsBlankInstructionAndMissingDueDate() {
		assertThatThrownBy(() -> new Resolution(UUID.randomUUID(), author, executor, Set.of(), " ",
				LocalDate.of(2026, 4, 1), null, NOW)).isInstanceOf(BusinessRuleException.class);
		assertThatThrownBy(() -> new Resolution(UUID.randomUUID(), author, executor, Set.of(), "x", null, null, NOW))
				.isInstanceOf(BusinessRuleException.class);
	}

	@Test
	void everyTransitionAppendsAHistoryEntryAndAFailedOneAppendsNothing() {
		Resolution r = resolution();
		r.apply(START, executor, null, NOW.plusSeconds(1));
		r.apply(SUBMIT, executor, "  done by hand ", NOW.plusSeconds(2));
		assertThat(r.getCompletionNote()).isEqualTo("done by hand");
		r.apply(REQUEST_REWORK, author, "redo", NOW.plusSeconds(3));
		r.apply(RESUME, executor, null, NOW.plusSeconds(4));
		r.apply(SUBMIT, executor, "now properly", NOW.plusSeconds(5));
		r.apply(ACCEPT, author, null, NOW.plusSeconds(6));

		assertThat(r.getState()).isEqualTo(ACCEPTED);
		assertThat(r.getCompletionNote()).isEqualTo("now properly");
		assertThat(r.getHistory()).extracting(ResolutionHistoryEntry::from)
				.containsExactly(null, NEW, IN_PROGRESS, SUBMITTED, REWORK, IN_PROGRESS, SUBMITTED);
		assertThat(r.getHistory()).extracting(ResolutionHistoryEntry::to)
				.containsExactly(NEW, IN_PROGRESS, SUBMITTED, REWORK, IN_PROGRESS, SUBMITTED, ACCEPTED);
		assertThat(r.getHistory()).extracting(ResolutionHistoryEntry::actorId)
				.containsExactly(author, executor, executor, author, executor, executor, author);
		assertThat(r.getHistory().get(3).comment()).isEqualTo("redo");
		assertThat(r.getHistory().get(6).at()).isEqualTo(NOW.plusSeconds(6));

		int size = r.getHistory().size();
		assertThatThrownBy(() -> r.apply(CANCEL, author, "late", NOW)).isInstanceOf(BusinessRuleException.class);
		assertThatThrownBy(() -> r.apply(START, stranger, null, NOW)).isInstanceOf(DomainException.class);
		assertThat(r.getHistory()).hasSize(size);
		assertThat(r.getState()).isEqualTo(ACCEPTED);
	}

	@Test
	void aFailedSubmitKeepsTheStateAndNote() {
		Resolution r = resolution();
		r.apply(START, executor, null, NOW);
		assertThatThrownBy(() -> r.apply(SUBMIT, executor, "  ", NOW)).isInstanceOf(BusinessRuleException.class);
		assertThat(r.getState()).isEqualTo(IN_PROGRESS);
		assertThat(r.getCompletionNote()).isNull();
		assertThat(r.getHistory()).hasSize(2);
	}

	@Test
	void redirectMovesToRedirectedAndAnOverdueCheckUsesTheEntity() {
		Resolution r = resolution();
		r.apply(START, executor, null, NOW);
		r.apply(REDIRECT, executor, "to the deputy", NOW);
		assertThat(r.getState()).isEqualTo(REDIRECTED);
		Clock late = Clock.fixed(Instant.parse("2026-05-01T00:00:00Z"), ZoneOffset.UTC);
		assertThat(r.isOverdue(late)).isTrue();
		r.apply(CANCEL, author, null, NOW);
		assertThat(r.isOverdue(late)).isFalse();
	}

}
