package com.kpi.studentcouncil.crm.identity;

import java.util.Optional;
import java.util.UUID;

/**
 * Public account-management API of the identity module for other modules (member administration).
 * Every operation is audited by identity; audit rows never contain a password or hash.
 * Callers must enforce their own permission checks.
 */
public interface AccountService {

	/**
	 * Creates an ACTIVE account with a random temporary password; the user must change it on first sign-in.
	 *
	 * @throws com.kpi.studentcouncil.crm.shared.error.BusinessRuleException {@code LOGIN_INVALID}
	 * @throws com.kpi.studentcouncil.crm.shared.error.ConflictException     {@code LOGIN_TAKEN}
	 */
	NewAccount createAccount(String login, String locale);

	/**
	 * Replaces the password with a new random temporary one and forces a change on next sign-in.
	 *
	 * @return the temporary password in plaintext; the only moment it exists in clear
	 */
	String resetPassword(UUID userId);

	/** Blocks sign-in (archives the account). Open sessions stop working on their next request. */
	void archiveAccount(UUID userId);

	/** Re-enables sign-in of an archived account. */
	void restoreAccount(UUID userId);

	/** Login of an account (any status), if it exists. */
	Optional<String> findLogin(UUID userId);

}
