package com.kpi.studentcouncil.crm.tasks.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;
import com.kpi.studentcouncil.crm.shared.error.ForbiddenException;

/**
 * Pure state machine of resolutions, transitions exactly per CONTEXT.md 5.3. Stable error codes:
 * {@code RESOLUTION_TRANSITION_NOT_ALLOWED} (422), {@code RESOLUTION_NOT_EXECUTOR} and {@code RESOLUTION_NOT_AUTHOR}
 * (403), {@code RESOLUTION_COMPLETION_NOTE_REQUIRED} and {@code RESOLUTION_REWORK_COMMENT_REQUIRED} (422). Checks run
 * in that order: transition, actor, comment.
 */
final class ResolutionStateMachine {

	static final ZoneId DISPLAY_ZONE = ZoneId.of("Europe/Kyiv");

	private ResolutionStateMachine() {
	}

	/** Who may perform an action. */
	enum Role {
		AUTHOR, EXECUTOR
	}

	static Role requiredRole(ResolutionAction action) {
		return switch (action) {
			case START, SUBMIT, RESUME, REDIRECT -> Role.EXECUTOR;
			case ACCEPT, REQUEST_REWORK, CANCEL -> Role.AUTHOR;
		};
	}

	/** The target state; throws {@code RESOLUTION_TRANSITION_NOT_ALLOWED} when the action is not allowed from {@code from}. */
	static ResolutionState target(ResolutionState from, ResolutionAction action) {
		ResolutionState to = switch (action) {
			case START -> from == ResolutionState.NEW ? ResolutionState.IN_PROGRESS : null;
			case SUBMIT -> from == ResolutionState.IN_PROGRESS ? ResolutionState.SUBMITTED : null;
			case ACCEPT -> from == ResolutionState.SUBMITTED ? ResolutionState.ACCEPTED : null;
			case REQUEST_REWORK -> from == ResolutionState.SUBMITTED ? ResolutionState.REWORK : null;
			case RESUME -> from == ResolutionState.REWORK ? ResolutionState.IN_PROGRESS : null;
			case REDIRECT -> from == ResolutionState.IN_PROGRESS ? ResolutionState.REDIRECTED : null;
			case CANCEL -> from.isFinal() ? null : ResolutionState.CANCELED;
		};
		if (to == null) {
			throw new BusinessRuleException("RESOLUTION_TRANSITION_NOT_ALLOWED", from.name(), action.name());
		}
		return to;
	}

	/**
	 * Validates the whole transition and returns the target state.
	 *
	 * @param comment completion note (SUBMIT) or rework comment (REQUEST_REWORK); ignored by other actions
	 */
	static ResolutionState check(ResolutionState from, ResolutionAction action, UUID actorId, UUID authorId,
			UUID executorId, String comment) {
		ResolutionState to = target(from, action);
		if (requiredRole(action) == Role.EXECUTOR) {
			if (actorId == null || !actorId.equals(executorId)) {
				throw new ForbiddenException("RESOLUTION_NOT_EXECUTOR");
			}
		}
		else if (actorId == null || !actorId.equals(authorId)) {
			throw new ForbiddenException("RESOLUTION_NOT_AUTHOR");
		}
		if (action == ResolutionAction.SUBMIT && isBlank(comment)) {
			throw new BusinessRuleException("RESOLUTION_COMPLETION_NOTE_REQUIRED");
		}
		if (action == ResolutionAction.REQUEST_REWORK && isBlank(comment)) {
			throw new BusinessRuleException("RESOLUTION_REWORK_COMMENT_REQUIRED");
		}
		return to;
	}

	/** Overdue: the due date is before today (Europe/Kyiv) and the state is neither ACCEPTED nor CANCELED. */
	static boolean isOverdue(ResolutionState state, LocalDate dueDate, Clock clock) {
		return dueDate != null && !state.isFinal() && dueDate.isBefore(LocalDate.now(clock.withZone(DISPLAY_ZONE)));
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

}
