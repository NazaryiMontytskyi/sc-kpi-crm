package com.kpi.studentcouncil.crm.tasks.internal;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableEntity;
import com.kpi.studentcouncil.crm.tasks.StatusCategory;

/** A configurable status of a space; {@code position} is its place among the statuses of the space. */
@Entity
@Table(name = "task_space_status")
class SpaceStatus extends ArchivableEntity {

	@Column(name = "space_id", nullable = false, updatable = false)
	private UUID spaceId;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "category", nullable = false, length = 20)
	private StatusCategory category;

	@Column(name = "position", nullable = false)
	private int position;

	protected SpaceStatus() {
	}

	SpaceStatus(UUID spaceId, String name, StatusCategory category, int position) {
		this.spaceId = spaceId;
		this.name = name;
		this.category = category;
		this.position = position;
	}

	UUID getSpaceId() {
		return spaceId;
	}

	String getName() {
		return name;
	}

	StatusCategory getCategory() {
		return category;
	}

	int getPosition() {
		return position;
	}

	void update(String name, StatusCategory category) {
		this.name = name;
		this.category = category;
	}

	void moveTo(int position) {
		this.position = position;
	}

	Map<String, Object> auditSnapshot() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("spaceId", spaceId.toString());
		map.put("name", name);
		map.put("category", category.name());
		map.put("position", position);
		map.put("archivedAt", getArchivedAt() == null ? null : getArchivedAt().toString());
		return map;
	}

}
