package com.kpi.studentcouncil.crm.search.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A parsed user query: lower-cased text and its word tokens. Pure; builds the pieces the SQL needs (prefix
 * tsquery, escaped LIKE pattern) and the result snippet.
 *
 * @param text   lower-cased, whitespace-normalised query
 * @param tokens letter/digit words of the query (never empty)
 */
record SearchQuery(String text, List<String> tokens) {

	static final int MIN_LENGTH = 2;
	static final int MAX_LENGTH = 100;

	private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

	/** Empty when the query is blank or shorter than {@link #MIN_LENGTH} characters. */
	static Optional<SearchQuery> parse(String raw) {
		if (raw == null) {
			return Optional.empty();
		}
		String text = WHITESPACE.matcher(raw.strip()).replaceAll(" ");
		if (text.length() > MAX_LENGTH) {
			text = text.substring(0, MAX_LENGTH).strip();
		}
		text = text.toLowerCase(Locale.ROOT);
		if (text.length() < MIN_LENGTH) {
			return Optional.empty();
		}
		List<String> tokens = new ArrayList<>();
		for (String part : NON_WORD.split(text)) {
			if (!part.isEmpty()) {
				tokens.add(part);
			}
		}
		return tokens.isEmpty() ? Optional.empty() : Optional.of(new SearchQuery(text, List.copyOf(tokens)));
	}

	/** Prefix tsquery source: every word is a prefix term, all must match ({@code ab:* & cd:*}). */
	String prefixTsQuery() {
		return String.join(" & ", tokens.stream().map(t -> t + ":*").toList());
	}

	/** LIKE pattern for a substring match, with {@code \}, {@code %} and {@code _} escaped. */
	String likePattern() {
		return "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
	}

	/** A short excerpt of {@code body} around the first matching word, or its beginning when nothing matches. */
	String snippet(String body) {
		if (body == null || body.isBlank()) {
			return "";
		}
		String flat = WHITESPACE.matcher(body.strip()).replaceAll(" ");
		String lower = flat.toLowerCase(Locale.ROOT);
		int hit = -1;
		for (String token : tokens) {
			int at = lower.indexOf(token);
			if (at >= 0 && (hit < 0 || at < hit)) {
				hit = at;
			}
		}
		hit = Math.min(Math.max(hit, 0), flat.length());
		int start = Math.max(0, hit - 60);
		int end = Math.min(flat.length(), hit + 140);
		return (start > 0 ? "\u2026" : "") + flat.substring(start, end).strip() + (end < flat.length() ? "\u2026" : "");
	}

}
