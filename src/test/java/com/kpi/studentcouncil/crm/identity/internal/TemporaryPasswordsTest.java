package com.kpi.studentcouncil.crm.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class TemporaryPasswordsTest {

	@Test
	void generatesDistinctPasswordsSatisfyingThePolicy() {
		PasswordPolicy policy = new PasswordPolicy(10);
		TemporaryPasswords generator = new TemporaryPasswords();
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 200; i++) {
			String password = generator.generate(policy, "fake.user");
			assertThat(policy.isValid(password, "fake.user")).isTrue();
			seen.add(password);
		}
		assertThat(seen).hasSize(200);
	}

}
