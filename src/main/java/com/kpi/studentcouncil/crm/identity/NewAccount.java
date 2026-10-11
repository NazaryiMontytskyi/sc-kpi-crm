package com.kpi.studentcouncil.crm.identity;

import java.util.UUID;

/**
 * Result of account creation.
 *
 * @param id                the new user id
 * @param login             normalised login
 * @param temporaryPassword plaintext temporary password, to be shown once and never stored or logged
 */
public record NewAccount(UUID id, String login, String temporaryPassword) {

	@Override
	public String toString() {
		return "NewAccount[id=" + id + ", login=" + login + ", temporaryPassword=***]";
	}

}
