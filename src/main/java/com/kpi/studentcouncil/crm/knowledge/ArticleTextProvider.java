package com.kpi.studentcouncil.crm.knowledge;

import java.util.Optional;
import java.util.UUID;

/**
 * Public API of the knowledge module for the {@code search} module: plain-text extraction of an article for
 * indexing. Restricted articles are only returned to a caller who holds {@code kb.restricted.view}, so the text of a
 * restricted article never reaches a user (or an index query) that may not see it.
 */
public interface ArticleTextProvider {

	/**
	 * The text of an article, or empty when it does not exist or is restricted and the current actor lacks
	 * {@code kb.restricted.view}. Archived articles are returned (flagged), so an index can drop them.
	 */
	Optional<ArticleText> findText(UUID articleId);

}
