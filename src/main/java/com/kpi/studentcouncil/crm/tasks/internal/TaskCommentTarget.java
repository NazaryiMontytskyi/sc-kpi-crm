package com.kpi.studentcouncil.crm.tasks.internal;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.kpi.studentcouncil.crm.shared.error.ConflictException;
import com.kpi.studentcouncil.crm.shared.error.NotFoundException;
import com.kpi.studentcouncil.crm.tasks.CommentTarget;
import com.kpi.studentcouncil.crm.tasks.CommentTargetType;

/** Comments on tasks: the task must exist and not be archived. */
@Component
class TaskCommentTarget implements CommentTarget {

	private final TaskRepository tasks;

	TaskCommentTarget(TaskRepository tasks) {
		this.tasks = tasks;
	}

	@Override
	public CommentTargetType type() {
		return CommentTargetType.TASK;
	}

	@Override
	public void assertAcceptsComments(UUID targetId) {
		Task task = tasks.findById(targetId).orElseThrow(() -> new NotFoundException(TaskService.TASK, targetId));
		if (task.isArchived()) {
			throw new ConflictException("TASK_ARCHIVED");
		}
	}

	@Override
	public String viewPermission() {
		return "tasks.view";
	}

}
