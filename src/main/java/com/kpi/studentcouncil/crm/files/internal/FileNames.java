package com.kpi.studentcouncil.crm.files.internal;

/** Sanitizes user-supplied file names for display. The result is never used to build a storage path. */
final class FileNames {

	static final String FALLBACK = "file";
	static final int MAX_LENGTH = 255;

	private FileNames() {
	}

	/**
	 * Keeps only the last path segment, drops control and bidirectional-override characters, and limits the length
	 * (keeping the extension). Returns {@value #FALLBACK} when nothing usable remains.
	 */
	static String sanitize(String raw) {
		if (raw == null) {
			return FALLBACK;
		}
		String name = raw.replace('\\', '/');
		name = name.substring(name.lastIndexOf('/') + 1);
		StringBuilder clean = new StringBuilder(name.length());
		name.codePoints().filter(cp -> !Character.isISOControl(cp) && !isBidiControl(cp))
				.forEach(clean::appendCodePoint);
		String result = clean.toString().strip();
		if (result.isEmpty() || result.chars().allMatch(c -> c == '.')) {
			return FALLBACK;
		}
		return truncate(result);
	}

	private static boolean isBidiControl(int cp) {
		return (cp >= 0x202A && cp <= 0x202E) || (cp >= 0x2066 && cp <= 0x2069) || cp == 0x200E || cp == 0x200F
				|| cp == 0x061C;
	}

	private static String truncate(String name) {
		if (name.length() <= MAX_LENGTH) {
			return name;
		}
		int dot = name.lastIndexOf('.');
		String extension = dot > 0 && name.length() - dot <= 20 ? name.substring(dot) : "";
		String base = name.substring(0, MAX_LENGTH - extension.length());
		if (Character.isHighSurrogate(base.charAt(base.length() - 1))) {
			base = base.substring(0, base.length() - 1);
		}
		return base + extension;
	}

}
