package com.kpi.studentcouncil.crm.tasks.internal;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableEntity;

/** A container of tasks: bound to an OrgUnit (department, division, working group) or general. */
@Entity
@Table(name = "task_space")
class Space extends ArchivableEntity {

	@Column(name = "name", nullable = false, length = 200)
	private String name;

	@Column(name = "org_unit_id")
	private UUID orgUnitId;

	@Column(name = "is_general", nullable = false)
	private boolean general;

	protected Space() {
	}

	Space(String name, UUID orgUnitId, boolean general) {
		this.name = name;
		this.orgUnitId = orgUnitId;
		this.general = general;
	}

	String getName() {
		return name;
	}

	UUID getOrgUnitId() {
		return orgUnitId;
	}

	boolean isGeneral() {
		return general;
	}

	void update(String name, UUID orgUnitId, boolean general) {
		this.name = name;
		this.orgUnitId = orgUnitId;
		this.general = general;
	}

	Map<String, Object> auditSnapshot() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("name", name);
		map.put("orgUnitId", orgUnitId == null ? null : orgUnitId.toString());
		map.put("general", general);
		map.put("archivedAt", getArchivedAt() == null ? null : getArchivedAt().toString());
		return map;
	}

}
