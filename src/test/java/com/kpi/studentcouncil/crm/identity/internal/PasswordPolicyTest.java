package com.kpi.studentcouncil.crm.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;

class PasswordPolicyTest {

	private final PasswordPolicy policy = new PasswordPolicy(10);

	@Test
	void acceptsLongEnoughPasswordWithLetterAndDigit() {
		assertThat(policy.isValid("correct-horse-7", "ivan.test")).isTrue();
		assertThat(policy.isValid("Пароль-довгий-5", "ivan.test")).isTrue();
	}

	@Test
	void rejectsTooShort() {
		assertThat(policy.isValid("abc123xyz", "u")).isFalse();
	}

	@Test
	void rejectsMissingLetterOrDigit() {
		assertThat(policy.isValid("1234567890123", "u")).isFalse();
		assertThat(policy.isValid("abcdefghijklm", "u")).isFalse();
	}

	@Test
	void rejectsOverBcryptLimit() {
		assertThat(policy.isValid("a1".repeat(37), "u")).isFalse();
		assertThat(policy.isValid("a1".repeat(36), "u")).isTrue();
	}

	@Test
	void rejectsPasswordContainingLogin() {
		assertThat(policy.isValid("xx-IVAN.test-99", "ivan.test")).isFalse();
	}

	@Test
	void minimumLengthIsConfigurable() {
		assertThat(new PasswordPolicy(14).isValid("abcdefgh123", "u")).isFalse();
		assertThat(new PasswordPolicy(6).isValid("abc123", "u")).isTrue();
	}

	@Test
	void validateThrowsStableCode() {
		assertThatThrownBy(() -> policy.validate("short1", "u")).isInstanceOfSatisfying(BusinessRuleException.class,
				ex -> assertThat(ex.getCode()).isEqualTo("PASSWORD_POLICY_VIOLATION"));
	}

}
