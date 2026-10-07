package com.kpi.studentcouncil.crm.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BootstrapAdminPropertiesTest {

	@Test
	void unsetEmptyOrBlankValuesMeanNotConfigured() {
		assertThat(new BootstrapAdminProperties(null, null).isConfigured()).isFalse();
		assertThat(new BootstrapAdminProperties("", "").isConfigured()).isFalse();
		assertThat(new BootstrapAdminProperties("admin", " ").isConfigured()).isFalse();
		assertThat(new BootstrapAdminProperties(" ", "Some-pass-12345").isConfigured()).isFalse();
		assertThat(new BootstrapAdminProperties("admin", "Some-pass-12345").isConfigured()).isTrue();
	}

}
