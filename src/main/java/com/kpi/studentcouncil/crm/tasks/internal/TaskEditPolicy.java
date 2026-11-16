package com.kpi.studentcouncil.crm.tasks.internal;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.kpi.studentcouncil.crm.access.AccessService;
import com.kpi.studentcouncil.crm.access.Permission;
import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.shared.error.ForbiddenException;

/**
 * THE single place that decides who may work on a task. Two rules:
 * <ul>
 * <li><b>edit</b> (fields, links, attachments; decision Q7): the creator or an {@code Admin}. {@code tasks.edit.any}
 * is deliberately not part of this rule;</li>
 * <li><b>contribute</b> (status changes, and the artifacts of a later increment; AS-5): the creator, any active
 * assignee, a holder of {@code tasks.edit.any} or an {@code Admin}. Commenting is open to every viewer and needs no
 * rule here.</li>
 * </ul>
 * Who may add or remove assignees is {@link AssigneeRules}. Change the rules here (and nowhere else) if the product
 * decision changes.
 */
@Component
class TaskEditPolicy {

	private final AdminCheck admin;
	private final AccessService access;
	private final TaskAssigneeRepository assignees;
	private final ActorProvider actors;

	TaskEditPolicy(AdminCheck admin, AccessService access, TaskAssigneeRepository assignees, ActorProvider actors) {
		this.admin = admin;
		this.access = access;
		this.assignees = assignees;
		this.actors = actors;
	}

	/** Edit of fields, links and attachments. */
	void assertCanEdit(Task task) {
		UUID actor = actors.currentActorId();
		if (!canEdit(actor, task.getCreatorId(), admin.isAdmin(actor))) {
			throw new ForbiddenException("TASK_EDIT_FORBIDDEN");
		}
	}

	/** Moving a task to another status (AS-5): the contribute rule. */
	void assertCanChangeStatus(Task task) {
		assertCanContribute(task);
	}

	/** Status changes and artifacts (AS-5): creator, active assignee, {@code tasks.edit.any} or Admin. */
	void assertCanContribute(Task task) {
		UUID actor = actors.currentActorId();
		if (actor == null || ActorProvider.SYSTEM_ACTOR.equals(actor)) {
			throw new ForbiddenException("TASK_CONTRIBUTE_FORBIDDEN");
		}
		Set<String> permissions = access.effectivePermissions(actor);
		boolean assignee = assignees.existsByTaskIdAndUserIdAndRemovedAtIsNull(task.getId(), actor);
		if (!canContribute(actor, task.getCreatorId(), assignee,
				permissions.contains(Permission.TASKS_EDIT_ANY.key()), admin.isAdmin(actor))) {
			throw new ForbiddenException("TASK_CONTRIBUTE_FORBIDDEN");
		}
	}

	/** Adding or removing {@code targetUserId} as an assignee ({@link AssigneeRules}). */
	void assertCanManageAssignee(Task task, UUID targetUserId) {
		UUID actor = actors.currentActorId();
		Set<String> permissions = actor == null ? Set.of() : access.effectivePermissions(actor);
		if (!AssigneeRules.canManage(actor, targetUserId, task.getCreatorId(), permissions)) {
			throw new ForbiddenException("TASK_ASSIGN_FORBIDDEN");
		}
	}

	/** The pure edit rule: the creator or an Admin; the system actor never edits through the API. */
	static boolean canEdit(UUID actorId, UUID creatorId, boolean actorIsAdmin) {
		if (actorId == null || ActorProvider.SYSTEM_ACTOR.equals(actorId)) {
			return false;
		}
		return actorIsAdmin || actorId.equals(creatorId);
	}

	/** The pure contribute rule (AS-5); the system actor never contributes through the API. */
	static boolean canContribute(UUID actorId, UUID creatorId, boolean actorIsActiveAssignee, boolean actorHasEditAny,
			boolean actorIsAdmin) {
		if (actorId == null || ActorProvider.SYSTEM_ACTOR.equals(actorId)) {
			return false;
		}
		return actorIsAdmin || actorHasEditAny || actorIsActiveAssignee || actorId.equals(creatorId);
	}

}
