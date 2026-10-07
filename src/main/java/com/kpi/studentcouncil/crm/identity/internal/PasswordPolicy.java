package com.kpi.studentcouncil.crm.identity.internal;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;

/**
 * Password rules: minimum length (configurable, default 10), at least one letter and one digit, at most 72 bytes
 * (BCrypt limit), and the password must not contain the login.
 */
public class PasswordPolicy {

	static final int MAX_BYTES = 72;

	private final int minLength;

	public PasswordPolicy(int minLength) {
		this.minLength = minLength;
	}

	public int minLength() {
		return minLength;
	}

	public boolean isValid(String password, String login) {
		if (password == null || password.codePointCount(0, password.length()) < minLength) {
			return false;
		}
		if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
			return false;
		}
		if (password.codePoints().noneMatch(Character::isLetter) || password.codePoints().noneMatch(Character::isDigit)) {
			return false;
		}
		return login == null || login.length() < 3
				|| !password.toLowerCase(Locale.ROOT).contains(login.toLowerCase(Locale.ROOT));
	}

	/** @throws BusinessRuleException with code {@code PASSWORD_POLICY_VIOLATION} when the password is not acceptable */
	public void validate(String password, String login) {
		if (!isValid(password, login)) {
			throw new BusinessRuleException("PASSWORD_POLICY_VIOLATION", String.valueOf(minLength));
		}
	}

}
