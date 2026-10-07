package com.kpi.studentcouncil.crm.identity.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credentials of the first {@code Admin} account ({@code CRM_BOOTSTRAP_ADMIN_LOGIN} /
 * {@code CRM_BOOTSTRAP_ADMIN_PASSWORD}). Used only on an empty users table; the account must change the password on
 * first sign-in. There are no defaults: with no values nothing is created.
 */
@ConfigurationProperties("crm.bootstrap.admin")
public record BootstrapAdminProperties(String login, String password) {

	boolean isConfigured() {
		return login != null && !login.isBlank() && password != null && !password.isBlank();
	}

	@Override
	public String toString() {
		return "BootstrapAdminProperties[login=" + login + ", password=***]";
	}

}
