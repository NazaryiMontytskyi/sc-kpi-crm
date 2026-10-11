package com.kpi.studentcouncil.crm.org.internal;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableEntity;

/**
 * A post in an OrgUnit. It exists independently of the people holding it (it may be vacant). {@code head} and the
 * unit are fixed at creation; the unit head is derived from the active appointment to the head position.
 */
@Entity
@Table(name = "org_position")
class Position extends ArchivableEntity {

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@Column(name = "org_unit_id", nullable = false, updatable = false)
	private UUID orgUnitId;

	@Column(name = "is_head", nullable = false, updatable = false)
	private boolean head;

	@Column(name = "capacity", nullable = false)
	private int capacity;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "org_position_role", joinColumns = @JoinColumn(name = "position_id"))
	@Column(name = "role_id", nullable = false)
	private Set<UUID> defaultRoleIds = new TreeSet<>();

	protected Position() {
	}

	Position(String title, UUID orgUnitId, boolean head, int capacity, Set<UUID> defaultRoleIds) {
		this.title = title;
		this.orgUnitId = orgUnitId;
		this.head = head;
		this.capacity = capacity;
		this.defaultRoleIds = new TreeSet<>(defaultRoleIds);
	}

	String getTitle() {
		return title;
	}

	UUID getOrgUnitId() {
		return orgUnitId;
	}

	boolean isHead() {
		return head;
	}

	int getCapacity() {
		return capacity;
	}

	Set<UUID> getDefaultRoleIds() {
		return Set.copyOf(defaultRoleIds);
	}

	void update(String title, int capacity, Set<UUID> roleIds) {
		this.title = title;
		this.capacity = capacity;
		this.defaultRoleIds.clear();
		this.defaultRoleIds.addAll(roleIds);
	}

	Map<String, Object> auditSnapshot() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("title", title);
		map.put("orgUnitId", orgUnitId.toString());
		map.put("isHead", head);
		map.put("capacity", capacity);
		map.put("defaultRoleIds", defaultRoleIds.stream().map(UUID::toString).sorted().toList());
		map.put("archivedAt", getArchivedAt() == null ? null : getArchivedAt().toString());
		return map;
	}

}
