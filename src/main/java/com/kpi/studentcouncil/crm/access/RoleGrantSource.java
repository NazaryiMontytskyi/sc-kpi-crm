package com.kpi.studentcouncil.crm.access;

import java.util.UUID;

/**
 * Identifies why a role was granted (ADR-0006). A grant made by another module on behalf of a domain record
 * (e.g. an {@code Appointment}) carries that record as its source; such a grant is revoked only through the same
 * source and is independent of manual grants and of grants from other sources.
 *
 * @param type a short stable code of the kind of record, at most 30 characters (e.g. {@code APPOINTMENT})
 * @param id   the record id
 */
public record RoleGrantSource(String type, UUID id) {

	public RoleGrantSource {
		if (type == null || type.isBlank() || type.length() > 30 || id == null) {
			throw new IllegalArgumentException("A grant source needs a type (max 30 chars) and an id");
		}
	}

}
