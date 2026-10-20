package com.kpi.studentcouncil.crm.tasks.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableEntity;
import com.kpi.studentcouncil.crm.tasks.CustomFieldType;

/** A custom field a space defines for its tasks. The type is fixed at creation; options exist only for SELECT. */
@Entity
@Table(name = "task_custom_field")
class CustomFieldDefinition extends ArchivableEntity {

	@Column(name = "space_id", nullable = false, updatable = false)
	private UUID spaceId;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "field_type", nullable = false, updatable = false, length = 20)
	private CustomFieldType type;

	@Column(name = "required", nullable = false)
	private boolean required;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "task_custom_field_option", joinColumns = @JoinColumn(name = "field_id"))
	@OrderColumn(name = "position")
	@Column(name = "value", nullable = false, length = 100)
	private List<String> options = new ArrayList<>();

	protected CustomFieldDefinition() {
	}

	CustomFieldDefinition(UUID spaceId, String name, CustomFieldType type, boolean required, List<String> options) {
		this.spaceId = spaceId;
		this.name = name;
		this.type = type;
		this.required = required;
		this.options = new ArrayList<>(options);
	}

	UUID getSpaceId() {
		return spaceId;
	}

	String getName() {
		return name;
	}

	CustomFieldType getType() {
		return type;
	}

	boolean isRequired() {
		return required;
	}

	List<String> getOptions() {
		return List.copyOf(options);
	}

	void update(String name, boolean required, List<String> options) {
		this.name = name;
		this.required = required;
		this.options.clear();
		this.options.addAll(options);
	}

	Map<String, Object> auditSnapshot() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("spaceId", spaceId.toString());
		map.put("name", name);
		map.put("type", type.name());
		map.put("required", required);
		map.put("options", List.copyOf(options));
		map.put("archivedAt", getArchivedAt() == null ? null : getArchivedAt().toString());
		return map;
	}

}
