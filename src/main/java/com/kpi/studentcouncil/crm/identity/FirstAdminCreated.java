package com.kpi.studentcouncil.crm.identity;

import java.util.UUID;

/**
 * Published (inside the creating transaction) when the very first account is bootstrapped from the environment.
 * The access module (INC-005) subscribes to it to grant the hardcoded {@code Admin} role to that account.
 */
public record FirstAdminCreated(UUID userId) {
}
