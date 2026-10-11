package com.kpi.studentcouncil.crm.members.internal;

import java.util.regex.Pattern;

import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;

/** Normalisation of free-text profile values. */
final class MemberRules {

	/** Telegram username, optionally prefixed with an at sign. */
	static final String TELEGRAM_REGEX = "@?[A-Za-z0-9_]{5,32}";

	private static final Pattern TELEGRAM = Pattern.compile(TELEGRAM_REGEX);

	private MemberRules() {
	}

	/** Stores the username without the leading at sign. */
	static String normalizeTelegram(String raw) {
		String value = raw == null ? "" : raw.strip();
		if (!TELEGRAM.matcher(value).matches()) {
			throw new BusinessRuleException("TELEGRAM_INVALID");
		}
		return value.startsWith("@") ? value.substring(1) : value;
	}

	/** Blank optional text becomes null. */
	static String blankToNull(String value) {
		String s = value == null ? null : value.strip();
		return s == null || s.isEmpty() ? null : s;
	}

}
