package com.kpi.studentcouncil.crm.tasks.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;

/** Pure validation rules of comments (no I/O), unit-tested. */
final class CommentRules {

	static final int MAX_TEXT_LENGTH = 10_000;
	static final int MAX_MENTIONS = 50;
	static final int MAX_ATTACHMENTS = 10;
	private static final int MAX_DEPTH = 50;

	private CommentRules() {
	}

	/** The body must be a TipTap document with some content (text, a mention or - checked by the caller - files). */
	static void validateBody(Map<String, Object> body, boolean hasAttachments) {
		if (body == null || !"doc".equals(body.get("type"))) {
			throw new BusinessRuleException("COMMENT_BODY_INVALID");
		}
		StringBuilder text = new StringBuilder();
		boolean[] hasMention = { false };
		walk(body, 0, text, hasMention, null);
		if (text.length() > MAX_TEXT_LENGTH) {
			throw new BusinessRuleException("COMMENT_TOO_LONG", MAX_TEXT_LENGTH);
		}
		if (text.toString().isBlank() && !hasMention[0] && !hasAttachments) {
			throw new BusinessRuleException("COMMENT_EMPTY");
		}
	}

	/** User ids of the {@code mention} nodes of the body, in document order, distinct. */
	static Set<UUID> extractMentions(Map<String, Object> body) {
		Set<UUID> ids = new LinkedHashSet<>();
		walk(body, 0, new StringBuilder(), new boolean[1], ids);
		return ids;
	}

	/**
	 * The ids the client sent must be exactly the mention nodes of the body, so the stored mentions can never drift
	 * from what the text shows.
	 */
	static Set<UUID> checkMentions(Collection<UUID> sent, Map<String, Object> body) {
		Set<UUID> fromBody = extractMentions(body);
		Set<UUID> declared = new LinkedHashSet<>();
		if (sent != null) {
			for (UUID id : sent) {
				if (id == null) {
					throw new BusinessRuleException("COMMENT_MENTION_INVALID");
				}
				declared.add(id);
			}
		}
		if (!declared.equals(fromBody)) {
			throw new BusinessRuleException("COMMENT_MENTIONS_MISMATCH");
		}
		if (fromBody.size() > MAX_MENTIONS) {
			throw new BusinessRuleException("COMMENT_TOO_MANY_MENTIONS", MAX_MENTIONS);
		}
		return fromBody;
	}

	/** Attachment ids keep their order; duplicates collapse. */
	static List<UUID> normalizeAttachments(Collection<UUID> ids) {
		List<UUID> result = new ArrayList<>();
		if (ids != null) {
			for (UUID id : ids) {
				if (id == null) {
					throw new BusinessRuleException("COMMENT_ATTACHMENT_INVALID");
				}
				if (!result.contains(id)) {
					result.add(id);
				}
			}
		}
		if (result.size() > MAX_ATTACHMENTS) {
			throw new BusinessRuleException("COMMENT_TOO_MANY_ATTACHMENTS", MAX_ATTACHMENTS);
		}
		return result;
	}

	private static void walk(Object node, int depth, StringBuilder text, boolean[] hasMention, Set<UUID> mentions) {
		if (depth > MAX_DEPTH) {
			throw new BusinessRuleException("COMMENT_BODY_INVALID");
		}
		if (!(node instanceof Map<?, ?> map)) {
			return;
		}
		Object type = map.get("type");
		if (map.get("text") instanceof String s) {
			text.append(s);
		}
		if ("mention".equals(type)) {
			hasMention[0] = true;
			if (mentions != null) {
				mentions.add(mentionId(map.get("attrs")));
			}
		}
		if (map.get("content") instanceof List<?> children) {
			for (Object child : children) {
				walk(child, depth + 1, text, hasMention, mentions);
			}
		}
	}

	private static UUID mentionId(Object attrs) {
		if (attrs instanceof Map<?, ?> a && a.get("id") instanceof String id) {
			try {
				return UUID.fromString(id);
			}
			catch (IllegalArgumentException e) {
				throw new BusinessRuleException("COMMENT_MENTION_INVALID");
			}
		}
		throw new BusinessRuleException("COMMENT_MENTION_INVALID");
	}

}
