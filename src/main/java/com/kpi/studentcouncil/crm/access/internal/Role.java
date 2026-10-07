package com.kpi.studentcouncil.crm.access.internal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import com.kpi.studentcouncil.crm.access.Permission;
import com.kpi.studentcouncil.crm.shared.domain.ArchivableEntity;

/**
 * A named set of permissions built in the role builder. The hardcoded Admin role is not a row of this table
 * (see {@code AccessService.ADMIN_ROLE_ID}). Rows with {@code isSystem = true} are immutable through the services.
 */
@Entity
@Table(name = "role")
class Role extends ArchivableEntity {

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "description", length = 500)
	private String description;

	@Column(name = "is_system", nullable = false)
	private boolean system;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "role_permission", joinColumns = @JoinColumn(name = "role_id"))
	@Column(name = "permission_key", nullable = false, length = 100)
	private Set<String> permissionKeys = new TreeSet<>();

	protected Role() {
	}

	static Role create(String name, String description, Set<String> permissionKeys) {
		Role role = new Role();
		role.name = name;
		role.description = description;
		role.permissionKeys = new TreeSet<>(permissionKeys);
		return role;
	}

	void update(String name, String description, Set<String> permissionKeys) {
		this.name = name;
		this.description = description;
		this.permissionKeys.clear();
		this.permissionKeys.addAll(permissionKeys);
	}

	String getName() {
		return name;
	}

	String getDescription() {
		return description;
	}

	boolean isSystem() {
		return system;
	}

	/** Stored keys that still exist in the catalog (a key removed from the code is ignored, never granted). */
	Set<String> getPermissionKeys() {
		Set<String> known = new TreeSet<>();
		for (String key : permissionKeys) {
			if (Permission.isKnown(key)) {
				known.add(key);
			}
		}
		return Collections.unmodifiableSet(known);
	}

	Map<String, Object> auditSnapshot() {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("name", name);
		snapshot.put("description", description);
		snapshot.put("permissions", new TreeSet<>(permissionKeys));
		snapshot.put("archived", isArchived());
		return snapshot;
	}

}
