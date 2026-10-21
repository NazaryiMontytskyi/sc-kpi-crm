package com.kpi.studentcouncil.crm.knowledge.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;

/** Pure rules of articles (no I/O): content validation, reference and text extraction, hierarchy, tags. */
final class ArticleRules {

	static final int MAX_TEXT_LENGTH = 1_000_000;
	static final int MAX_REFERENCES = 500;
	static final int MAX_TAGS = 20;
	static final int MAX_TAG_LENGTH = 50;
	static final int MAX_ATTACHMENTS = 20;
	private static final int MAX_DEPTH = 100;

	/** Node types that end a line in the extracted plain text. */
	private static final Set<String> BLOCKS = Set.of("paragraph", "heading", "blockquote", "listItem", "taskItem",
			"codeBlock", "tableRow", "callout", "horizontalRule", "hardBreak");

	private ArticleRules() {
	}

	/** A missing content becomes an empty document; anything else must be a TipTap {@code doc}. */
	static Map<String, Object> normalizeContent(Map<String, Object> content) {
		if (content == null) {
			Map<String, Object> empty = new LinkedHashMap<>();
			empty.put("type", "doc");
			empty.put("content", new ArrayList<>());
			return empty;
		}
		if (!"doc".equals(content.get("type"))) {
			throw new BusinessRuleException("ARTICLE_CONTENT_INVALID");
		}
		// Walking validates the depth, the references and the text length.
		extractReferences(content);
		if (extractPlainText(content).length() > MAX_TEXT_LENGTH) {
			throw new BusinessRuleException("ARTICLE_TOO_LONG", MAX_TEXT_LENGTH);
		}
		return content;
	}

	/**
	 * References of the content, in document order, distinct. A {@code mention} node points to a person by default;
	 * {@code attrs.entityType} ({@code person}, {@code article}, {@code task}, case-insensitive) selects another kind
	 * and {@code attrs.id} is the target id.
	 */
	static Set<ArticleReference> extractReferences(Map<String, Object> content) {
		Set<ArticleReference> refs = new LinkedHashSet<>();
		walk(content, 0, null, refs);
		if (refs.size() > MAX_REFERENCES) {
			throw new BusinessRuleException("ARTICLE_TOO_MANY_REFERENCES", MAX_REFERENCES);
		}
		return refs;
	}

	/** Plain text of the body for search: text nodes concatenated, one line per block, trimmed. */
	static String extractPlainText(Map<String, Object> content) {
		StringBuilder text = new StringBuilder();
		walk(content, 0, text, null);
		return text.toString().strip();
	}

	/**
	 * True when making {@code newParentId} the parent of {@code articleId} would close a loop: the new parent is the
	 * article itself or one of its descendants. Walks up from the new parent through {@code parentOf}; a loop that
	 * already exists above it terminates the walk instead of hanging.
	 */
	static boolean wouldCreateCycle(UUID articleId, UUID newParentId, Function<UUID, UUID> parentOf) {
		Set<UUID> seen = new HashSet<>();
		UUID current = newParentId;
		while (current != null && seen.add(current)) {
			if (current.equals(articleId)) {
				return true;
			}
			current = parentOf.apply(current);
		}
		return false;
	}

	/** Tags are stripped, lower-cased and de-duplicated; blank or too long tags are rejected. */
	static Set<String> normalizeTags(Collection<String> tags) {
		Set<String> result = new LinkedHashSet<>();
		if (tags == null) {
			return result;
		}
		for (String raw : tags) {
			String tag = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
			if (tag.isEmpty() || tag.length() > MAX_TAG_LENGTH) {
				throw new BusinessRuleException("ARTICLE_TAG_INVALID", MAX_TAG_LENGTH);
			}
			result.add(tag);
		}
		if (result.size() > MAX_TAGS) {
			throw new BusinessRuleException("ARTICLE_TOO_MANY_TAGS", MAX_TAGS);
		}
		return result;
	}

	/** Attachment ids keep their order; duplicates collapse. */
	static List<UUID> normalizeAttachments(Collection<UUID> ids) {
		List<UUID> result = new ArrayList<>();
		if (ids != null) {
			for (UUID id : ids) {
				if (id == null) {
					throw new BusinessRuleException("ARTICLE_ATTACHMENT_INVALID");
				}
				if (!result.contains(id)) {
					result.add(id);
				}
			}
		}
		if (result.size() > MAX_ATTACHMENTS) {
			throw new BusinessRuleException("ARTICLE_TOO_MANY_ATTACHMENTS", MAX_ATTACHMENTS);
		}
		return result;
	}

	private static void walk(Object node, int depth, StringBuilder text, Set<ArticleReference> refs) {
		if (depth > MAX_DEPTH) {
			throw new BusinessRuleException("ARTICLE_CONTENT_INVALID");
		}
		if (!(node instanceof Map<?, ?> map)) {
			return;
		}
		Object type = map.get("type");
		if (text != null && map.get("text") instanceof String s) {
			text.append(s);
		}
		if ("mention".equals(type)) {
			ArticleReference ref = reference(map.get("attrs"));
			if (refs != null) {
				refs.add(ref);
			}
			if (text != null && map.get("attrs") instanceof Map<?, ?> attrs && attrs.get("label") instanceof String l) {
				text.append(l);
			}
		}
		if (map.get("content") instanceof List<?> children) {
			for (Object child : children) {
				walk(child, depth + 1, text, refs);
			}
		}
		if (text != null && type instanceof String t && BLOCKS.contains(t)
				&& !text.isEmpty() && text.charAt(text.length() - 1) != '\n') {
			text.append('\n');
		}
	}

	private static ArticleReference reference(Object attrs) {
		if (!(attrs instanceof Map<?, ?> a) || !(a.get("id") instanceof String id)) {
			throw new BusinessRuleException("ARTICLE_REFERENCE_INVALID");
		}
		ReferenceType type = ReferenceType.PERSON;
		if (a.get("entityType") != null) {
			if (!(a.get("entityType") instanceof String raw)) {
				throw new BusinessRuleException("ARTICLE_REFERENCE_INVALID");
			}
			try {
				type = ReferenceType.valueOf(raw.strip().toUpperCase(Locale.ROOT));
			}
			catch (IllegalArgumentException e) {
				throw new BusinessRuleException("ARTICLE_REFERENCE_INVALID");
			}
		}
		try {
			return new ArticleReference(type, UUID.fromString(id));
		}
		catch (IllegalArgumentException e) {
			throw new BusinessRuleException("ARTICLE_REFERENCE_INVALID");
		}
	}

}
