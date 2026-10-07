package com.kpi.studentcouncil.crm.identity.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;

/**
 * Creates the first {@code Admin} account from {@code CRM_BOOTSTRAP_ADMIN_LOGIN} / {@code CRM_BOOTSTRAP_ADMIN_PASSWORD}
 * on an empty database. It does nothing when the variables are unset or any account already exists, so the
 * variables can stay in the environment. The account must change its password on first sign-in.
 */
@Component
class AdminBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

	private final BootstrapAdminProperties properties;

	private final UserAccountService accounts;

	AdminBootstrap(BootstrapAdminProperties properties, UserAccountService accounts) {
		this.properties = properties;
		this.accounts = accounts;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!properties.isConfigured()) {
			return;
		}
		try {
			if (accounts.createFirstAdminIfAbsent(properties.login(), properties.password())) {
				log.info("Bootstrapped the first administrator account '{}'; the password must be changed on first sign-in.",
						User.normalizeLogin(properties.login()));
			}
			else {
				log.info("Accounts already exist; the bootstrap administrator settings are ignored.");
			}
		}
		catch (BusinessRuleException ex) {
			throw new IllegalStateException("CRM_BOOTSTRAP_ADMIN_PASSWORD does not satisfy the password policy", null);
		}
	}

}
