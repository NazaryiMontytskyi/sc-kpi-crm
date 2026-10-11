package com.kpi.studentcouncil.crm.identity.internal;

import java.security.SecureRandom;

/** Generates random temporary passwords (unambiguous characters, always a letter and a digit). */
final class TemporaryPasswords {

	private static final String LETTERS = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";

	private static final String DIGITS = "23456789";

	private static final String ALL = LETTERS + DIGITS;

	static final int LENGTH = 16;

	private final SecureRandom random = new SecureRandom();

	/** A password that satisfies {@code policy} for the given login. */
	String generate(PasswordPolicy policy, String login) {
		int length = Math.max(LENGTH, policy.minLength());
		for (int attempt = 0; attempt < 100; attempt++) {
			char[] chars = new char[length];
			for (int i = 0; i < length; i++) {
				chars[i] = ALL.charAt(random.nextInt(ALL.length()));
			}
			chars[random.nextInt(length)] = LETTERS.charAt(random.nextInt(LETTERS.length()));
			int digitAt = random.nextInt(length);
			chars[digitAt] = DIGITS.charAt(random.nextInt(DIGITS.length()));
			String candidate = new String(chars);
			if (policy.isValid(candidate, login)) {
				return candidate;
			}
		}
		throw new IllegalStateException("Cannot generate a temporary password satisfying the password policy");
	}

}
