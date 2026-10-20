package com.kpi.studentcouncil.crm.tasks.internal;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.shared.error.ForbiddenException;

/**
 * THE single place that decides who may edit or archive a comment: its author or an {@code Admin}. Commenting itself
 * needs authentication only. The {@code tasks.edit.any} permission is deliberately not consulted (same decision as
 * {@link TaskEditPolicy}); change the rule here (and nowhere else) if the product decision changes.
 */
@Component
class CommentPolicy {

	private final AdminCheck admin;
	private final ActorProvider actors;

	CommentPolicy(AdminCheck admin, ActorProvider actors) {
		this.admin = admin;
		this.actors = actors;
	}

	/** Edit and archive. */
	void assertCanModify(Comment comment) {
		UUID actor = actors.currentActorId();
		if (!canModify(actor, comment.getAuthorId(), admin.isAdmin(actor))) {
			throw new ForbiddenException("COMMENT_EDIT_FORBIDDEN");
		}
	}

	/** The pure rule: the author or an Admin; the system actor never modifies through the API. */
	static boolean canModify(UUID actorId, UUID authorId, boolean actorIsAdmin) {
		if (actorId == null || ActorProvider.SYSTEM_ACTOR.equals(actorId)) {
			return false;
		}
		return actorIsAdmin || actorId.equals(authorId);
	}

}
