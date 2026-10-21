package com.kpi.studentcouncil.crm.knowledge.internal;

import org.springframework.stereotype.Component;

import com.kpi.studentcouncil.crm.access.AccessService;
import com.kpi.studentcouncil.crm.access.Permission;
import com.kpi.studentcouncil.crm.audit.ActorProvider;

/**
 * THE single place of the knowledge module that decides whether the current actor may see restricted articles
 * ({@code kb.restricted.view}; the access module resolves it, including for an Admin).
 */
@Component
class ArticleViewer {

	private final AccessService access;
	private final ActorProvider actors;

	ArticleViewer(AccessService access, ActorProvider actors) {
		this.access = access;
		this.actors = actors;
	}

	boolean canViewRestricted() {
		return access.hasPermission(actors.currentActorId(), Permission.KB_RESTRICTED_VIEW);
	}

	boolean canSee(Article article) {
		return !article.isRestricted() || canViewRestricted();
	}

}
