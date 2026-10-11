package com.kpi.studentcouncil.crm.access.internal;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.kpi.studentcouncil.crm.access.RoleGrantSource;
import com.kpi.studentcouncil.crm.shared.domain.ArchivableEntity;

/**
 * Grants a role to a user. Revoking archives the row (history is kept). {@code scopeOrgUnitId} is reserved for
 * org-unit-scoped roles and is not used yet [ASSUMPTION]. {@code roleId} may be the reserved Admin id, so it has
 * no foreign key; {@code userId} has none either (identity is a separate module).
 */
@Entity
@Table(name = "role_assignment")
class RoleAssignment extends ArchivableEntity {

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "role_id", nullable = false, updatable = false)
	private UUID roleId;

	@Column(name = "scope_org_unit_id", updatable = false)
	private UUID scopeOrgUnitId;

	/** Kind of record that caused the grant (e.g. APPOINTMENT); null for a manual grant (ADR-0006). */
	@Column(name = "source_type", updatable = false, length = 30)
	private String sourceType;

	@Column(name = "source_id", updatable = false)
	private UUID sourceId;

	protected RoleAssignment() {
	}

	static RoleAssignment create(UUID userId, UUID roleId) {
		RoleAssignment assignment = new RoleAssignment();
		assignment.userId = userId;
		assignment.roleId = roleId;
		return assignment;
	}

	static RoleAssignment create(UUID userId, UUID roleId, RoleGrantSource source) {
		RoleAssignment assignment = create(userId, roleId);
		if (source != null) {
			assignment.sourceType = source.type();
			assignment.sourceId = source.id();
		}
		return assignment;
	}

	String getSourceType() {
		return sourceType;
	}

	UUID getSourceId() {
		return sourceId;
	}

	UUID getUserId() {
		return userId;
	}

	UUID getRoleId() {
		return roleId;
	}

	UUID getScopeOrgUnitId() {
		return scopeOrgUnitId;
	}

	Map<String, Object> auditSnapshot(String roleName) {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("userId", userId);
		snapshot.put("roleId", roleId);
		snapshot.put("roleName", roleName);
		if (sourceType != null) {
			snapshot.put("sourceType", sourceType);
			snapshot.put("sourceId", sourceId);
		}
		snapshot.put("active", !isArchived());
		return snapshot;
	}

}
