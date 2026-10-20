package com.kpi.studentcouncil.crm.tasks.internal;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.kpi.studentcouncil.crm.access.AccessService;
import com.kpi.studentcouncil.crm.access.Permission;
import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.shared.error.ForbiddenException;

/**
 * THE single place that decides who may edit a task (decision Q7): the task author or an {@code Admin}. Everybody
 * else gets 403. The {@code tasks.edit.any} permission stays in the catalog but is deliberately not part of this
 * rule; change the rule here (and nowhere else) if the product decision changes.
 */
@Component
class TaskEditPolicy {

	private final AccessService access;
	private final ActorProvider actors;

	TaskEditPolicy(AccessService access, ActorProvider actors) {
		this.access = access;
		this.actors = actors;
	}

	/** Edit of fields, links and attachments. */
	void assertCanEdit(Task task) {
		UUID actor = actors.currentActorId();
		if (!canEdit(actor, task.getAuthorId(), isAdmin(actor))) {
			throw new ForbiddenException("TASK_EDIT_FORBIDDEN");
		}
	}

	/**
	 * Moving a task to another status. Follows the edit rule for now; kept separate so executors of resolutions can be
	 * let in later without touching the edit rule.
	 */
	void assertCanChangeStatus(Task task) {
		assertCanEdit(task);
	}

	/** The pure rule: the author or an Admin; the system actor never edits through the API. */
	static boolean canEdit(UUID actorId, UUID authorId, boolean actorIsAdmin) {
		if (actorId == null || ActorProvider.SYSTEM_ACTOR.equals(actorId)) {
			return false;
		}
		return actorIsAdmin || actorId.equals(authorId);
	}

	/**
	 * The Admin role is hardcoded and implies every permission of the catalog; the access module has no dedicated
	 * "is admin" query yet, so Admin is recognised by holding all of them.
	 */
	private boolean isAdmin(UUID actorId) {
		return access.effectivePermissions(actorId).containsAll(Permission.allKeys());
	}

}
