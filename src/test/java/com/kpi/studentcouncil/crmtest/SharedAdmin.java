package com.kpi.studentcouncil.crmtest;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Explicit, idempotent fixture for an Admin account in the database shared by all integration-test contexts.
 * Tests must not rely on the first-admin bootstrap (it only runs on an entirely empty database). The Admin role is
 * hardcoded and has no {@code role} row: it is represented by a {@code role_assignment} to {@link #ADMIN_ROLE_ID}.
 */
public final class SharedAdmin {

	public static final String LOGIN = "shared.admin";

	public static final String PASSWORD = "Bootstrap-pass-42";

	/** Same value as {@code AccessService.ADMIN_ROLE_ID}. */
	private static final UUID ADMIN_ROLE_ID = new UUID(0L, 1L);

	private SharedAdmin() {
	}

	/** Ensures the account exists, is active, has no pending password change and holds the Admin role. */
	public static UUID ensure(JdbcTemplate jdbc, PasswordEncoder encoder) {
		jdbc.update("insert into users (id, login, password_hash, status, locale, must_change_password, created_at,"
				+ " updated_at) values (?, ?, ?, 'ACTIVE', 'uk', false, now(), now()) on conflict (login) do nothing",
				UUID.randomUUID(), LOGIN, encoder.encode(PASSWORD));
		jdbc.update("update users set status = 'ACTIVE', must_change_password = false, archived_at = null"
				+ " where login = ?", LOGIN);
		UUID id = jdbc.queryForObject("select id from users where login = ?", UUID.class, LOGIN);
		jdbc.update("insert into role_assignment (id, user_id, role_id, created_at, updated_at)"
				+ " select ?, ?, ?, now(), now() where not exists (select 1 from role_assignment where user_id = ?"
				+ " and role_id = ? and scope_org_unit_id is null and archived_at is null)", UUID.randomUUID(), id,
				ADMIN_ROLE_ID, id, ADMIN_ROLE_ID);
		return id;
	}

}
