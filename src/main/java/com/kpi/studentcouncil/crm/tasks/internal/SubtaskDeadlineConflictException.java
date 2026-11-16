package com.kpi.studentcouncil.crm.tasks.internal;

import java.util.List;
import java.util.stream.Collectors;

import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;

/**
 * ST-3: the new parent deadline would leave subtasks with a later deadline (422
 * {@code TASK_DEADLINE_BEFORE_SUBTASKS}). The detail names the subtasks (argument {0}: their titles); the problem
 * additionally carries them as {@code conflictingSubtasks} ({@link TasksExceptionHandler}).
 */
class SubtaskDeadlineConflictException extends BusinessRuleException {

	static final String CODE = "TASK_DEADLINE_BEFORE_SUBTASKS";

	private final transient List<SubtaskRules.Subtask> conflicting;

	SubtaskDeadlineConflictException(List<SubtaskRules.Subtask> conflicting) {
		super(CODE, conflicting.stream().map(s -> "«" + s.title() + "» (" + s.deadline() + ")")
				.collect(Collectors.joining(", ")));
		this.conflicting = List.copyOf(conflicting);
	}

	List<SubtaskRules.Subtask> getConflicting() {
		return conflicting;
	}

}
