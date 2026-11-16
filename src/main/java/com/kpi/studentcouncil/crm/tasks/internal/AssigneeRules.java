package com.kpi.studentcouncil.crm.tasks.internal;

import java.util.Set;
import java.util.UUID;

import com.kpi.studentcouncil.crm.access.Permission;
import com.kpi.studentcouncil.crm.audit.ActorProvider;

/**
 * Pure rule of who may add or remove an assignee of a task (AS-3, ADR-0010):
 * <ul>
 * <li>{@code tasks.assign} lets the actor add or remove anybody (Admin holds every key);</li>
 * <li>without it the actor may only add or remove <em>themselves</em>, and only as the creator of the task holding
 * {@code tasks.create}, or when holding {@code tasks.edit.any};</li>
 * <li>the system actor never assigns through the API.</li>
 * </ul>
 * Who may <em>be</em> assigned (active accounts only, spec Q9: any active user) is checked by the service.
 */
final class AssigneeRules {

	private AssigneeRules() {
	}

	/**
	 * @param actorId     the caller
	 * @param targetId    the user being added or removed
	 * @param creatorId   the creator of the task
	 * @param permissions effective permission keys of the caller
	 */
	static boolean canManage(UUID actorId, UUID targetId, UUID creatorId, Set<String> permissions) {
		if (actorId == null || ActorProvider.SYSTEM_ACTOR.equals(actorId) || targetId == null) {
			return false;
		}
		if (permissions.contains(Permission.TASKS_ASSIGN.key())) {
			return true;
		}
		if (!actorId.equals(targetId)) {
			return false;
		}
		return permissions.contains(Permission.TASKS_EDIT_ANY.key())
				|| (actorId.equals(creatorId) && permissions.contains(Permission.TASKS_CREATE.key()));
	}

}
