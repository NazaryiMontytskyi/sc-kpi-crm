package com.kpi.studentcouncil.crm.files.internal;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Builds {@code Content-Disposition} values with a safely encoded file name (RFC 6266 / RFC 5987). */
final class ContentDispositions {

	/** Types rendered by the browser; everything else is downloaded. */
	private static final Set<String> INLINE_TYPES = Set.of("application/pdf", "image/png", "image/jpeg", "image/gif",
			"image/webp");

	private ContentDispositions() {
	}

	static boolean isInline(String mimeType) {
		return INLINE_TYPES.contains(mimeType);
	}

	/** {@code <type>; filename="<ascii fallback>"; filename*=UTF-8''<percent-encoded name>}. */
	static String build(boolean inline, String fileName) {
		return (inline ? "inline" : "attachment") + "; filename=\"" + asciiFallback(fileName) + "\"; filename*=UTF-8''"
				+ encode(fileName);
	}

	private static String asciiFallback(String name) {
		StringBuilder sb = new StringBuilder(name.length());
		name.codePoints().forEach(cp -> {
			boolean safe = cp >= 0x20 && cp < 0x7F && cp != '"' && cp != '\\' && cp != '%' && cp != ';';
			sb.append(safe ? (char) cp : '_');
		});
		return sb.toString();
	}

	private static String encode(String name) {
		StringBuilder sb = new StringBuilder();
		for (byte b : name.getBytes(StandardCharsets.UTF_8)) {
			int c = b & 0xFF;
			if (isAttrChar(c)) {
				sb.append((char) c);
			}
			else {
				sb.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16)))
						.append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
			}
		}
		return sb.toString();
	}

	/** RFC 5987 attr-char. */
	private static boolean isAttrChar(int c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || "!#$&+-.^_`|~".indexOf(c) >= 0;
	}

}
