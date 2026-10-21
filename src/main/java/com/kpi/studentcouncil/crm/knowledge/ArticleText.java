package com.kpi.studentcouncil.crm.knowledge;

import java.util.Set;
import java.util.UUID;

/**
 * Searchable text of an article: the title and the plain text extracted from the rich-text content.
 *
 * @param id         the article
 * @param title      the title
 * @param text       plain text of the body, blocks separated by line breaks
 * @param tags       lower-cased tags
 * @param restricted whether the article needs {@code kb.restricted.view}
 * @param archived   whether the article is archived
 */
public record ArticleText(UUID id, String title, String text, Set<String> tags, boolean restricted,
		boolean archived) {
}
