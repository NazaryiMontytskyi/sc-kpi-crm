package com.kpi.studentcouncil.crm.org.internal;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.kpi.studentcouncil.crm.org.OrgUnitType;
import com.kpi.studentcouncil.crm.shared.domain.ArchivableEntity;

/** Node of the org structure. Never hard-deleted: archiving hides it from the default tree. */
@Entity
@Table(name = "org_unit")
class OrgUnit extends ArchivableEntity {

	@Column(name = "name", nullable = false, length = 200)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, updatable = false, length = 20)
	private OrgUnitType type;

	@Column(name = "parent_id")
	private UUID parentId;

	@Column(name = "description", length = 2000)
	private String description;

	protected OrgUnit() {
	}

	OrgUnit(String name, OrgUnitType type, UUID parentId, String description) {
		this.name = name;
		this.type = type;
		this.parentId = parentId;
		this.description = description;
	}

	String getName() {
		return name;
	}

	OrgUnitType getType() {
		return type;
	}

	UUID getParentId() {
		return parentId;
	}

	String getDescription() {
		return description;
	}

	void update(String name, String description) {
		this.name = name;
		this.description = description;
	}

	void moveTo(UUID parentId) {
		this.parentId = parentId;
	}

}
