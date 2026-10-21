package com.kpi.studentcouncil.crm.knowledge;

import java.util.UUID;

/**
 * Domain event published (inside the transaction) after every change of an {@code Article}: created, updated, moved
 * to another parent, archived and restored. {@code search} and {@code notifications} subscribe to it. The event
 * carries no title or content, so subscribers must read the text through {@link ArticleTextProvider}, which applies
 * the visibility of restricted articles.
 *
 * @param kind            what happened
 * @param id              the article
 * @param parentArticleId the parent after the change, or null for a top-level article
 * @param restricted      whether the article needs {@code kb.restricted.view}
 */
public record ArticleEvent(Kind kind, UUID id, UUID parentArticleId, boolean restricted) {

	public enum Kind {
		CREATED, UPDATED, MOVED, ARCHIVED, RESTORED
	}

}
