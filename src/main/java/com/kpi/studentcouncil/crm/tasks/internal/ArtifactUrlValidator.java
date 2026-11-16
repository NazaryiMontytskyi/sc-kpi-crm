package com.kpi.studentcouncil.crm.tasks.internal;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;

/**
 * The URL rule of link artifacts (AR-2), as a pure function: only absolute {@code http}/{@code https} URLs (scheme
 * case-insensitive) with a host, at most {@value #MAX_LENGTH} characters after leading and trailing whitespace is
 * stripped; whitespace or control characters inside the URL are rejected. Everything else ({@code javascript:},
 * {@code data:}, {@code ftp:}, {@code mailto:}, relative or protocol-relative URLs, ...) is invalid. The database check
 * {@code ck_task_artifact_url_scheme} is the last line of defence.
 */
final class ArtifactUrlValidator {

	static final int MAX_LENGTH = 2048;

	private static final Set<String> SCHEMES = Set.of("http", "https");

	private ArtifactUrlValidator() {
	}

	/**
	 * Returns the URL to store (stripped of surrounding whitespace), or throws 422 {@code TASK_ARTIFACT_URL_TOO_LONG}
	 * (argument: the limit) or 422 {@code TASK_ARTIFACT_URL_INVALID}.
	 */
	static String requireValid(String raw) {
		if (raw == null) {
			throw invalid();
		}
		String url = raw.strip();
		if (url.length() > MAX_LENGTH) {
			throw new BusinessRuleException("TASK_ARTIFACT_URL_TOO_LONG", MAX_LENGTH);
		}
		if (!isValid(url)) {
			throw invalid();
		}
		return url;
	}

	/** True when {@code url} (already stripped) is an acceptable link; the length is checked by the caller. */
	static boolean isValid(String url) {
		if (url == null || url.isEmpty()) {
			return false;
		}
		for (int i = 0; i < url.length(); i++) {
			char c = url.charAt(i);
			if (Character.isWhitespace(c) || Character.isSpaceChar(c) || Character.isISOControl(c)) {
				return false;
			}
		}
		URI uri;
		try {
			uri = new URI(url);
		}
		catch (URISyntaxException e) {
			return false;
		}
		String scheme = uri.getScheme();
		if (scheme == null || !SCHEMES.contains(scheme.toLowerCase(Locale.ROOT)) || uri.isOpaque()) {
			return false;
		}
		return hasHost(uri);
	}

	/**
	 * A server authority with a host. {@link URI#getHost()} is null for host names {@code java.net.URI} does not parse
	 * as a server (e.g. internationalized names), so the raw authority is checked as a fallback.
	 */
	private static boolean hasHost(URI uri) {
		if (uri.getHost() != null && !uri.getHost().isEmpty()) {
			return true;
		}
		String authority = uri.getRawAuthority();
		if (authority == null) {
			return false;
		}
		String host = authority.substring(authority.lastIndexOf('@') + 1);
		int port = host.lastIndexOf(':');
		if (port >= 0 && !host.endsWith("]")) {
			host = host.substring(0, port);
		}
		return !host.isEmpty() && !host.startsWith(".");
	}

	private static BusinessRuleException invalid() {
		return new BusinessRuleException("TASK_ARTIFACT_URL_INVALID");
	}

}
