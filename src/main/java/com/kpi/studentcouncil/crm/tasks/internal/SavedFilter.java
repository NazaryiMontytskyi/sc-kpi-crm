package com.kpi.studentcouncil.crm.tasks.internal;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableEntity;

/** A task list filter a user saved under a name. Private to its owner; removing it archives it. */
@Entity
@Table(name = "task_saved_filter")
class SavedFilter extends ArchivableEntity {

	/** The stored criteria; they mirror the query parameters of the task list. */
	record Criteria(UUID spaceId, UUID statusId, Set<String> labels, UUID executorId, UUID authorId,
			ResolutionState resolutionState, TaskQuickView view, LocalDate dueFrom, LocalDate dueTo) {
	}

	@Column(name = "owner_id", nullable = false, updatable = false)
	private UUID ownerId;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "space_id")
	private UUID spaceId;

	@Column(name = "status_id")
	private UUID statusId;

	@Column(name = "executor_id")
	private UUID executorId;

	@Column(name = "author_id")
	private UUID authorId;

	@Enumerated(EnumType.STRING)
	@Column(name = "resolution_state", length = 20)
	private ResolutionState resolutionState;

	@Enumerated(EnumType.STRING)
	@Column(name = "quick_view", length = 20)
	private TaskQuickView view;

	@Column(name = "due_from")
	private LocalDate dueFrom;

	@Column(name = "due_to")
	private LocalDate dueTo;

	@ElementCollection(fetch = FetchType.EAGER)
	@Fetch(FetchMode.SELECT)
	@BatchSize(size = 50)
	@CollectionTable(name = "task_saved_filter_label", joinColumns = @JoinColumn(name = "filter_id"))
	@Column(name = "label", nullable = false, length = 50)
	private Set<String> labels = new LinkedHashSet<>();

	protected SavedFilter() {
	}

	SavedFilter(UUID ownerId, String name, Criteria criteria) {
		this.ownerId = ownerId;
		update(name, criteria);
	}

	UUID getOwnerId() {
		return ownerId;
	}

	String getName() {
		return name;
	}

	Criteria criteria() {
		return new Criteria(spaceId, statusId, new LinkedHashSet<>(labels), executorId, authorId, resolutionState, view,
				dueFrom, dueTo);
	}

	void update(String name, Criteria c) {
		this.name = name;
		this.spaceId = c.spaceId();
		this.statusId = c.statusId();
		this.executorId = c.executorId();
		this.authorId = c.authorId();
		this.resolutionState = c.resolutionState();
		this.view = c.view();
		this.dueFrom = c.dueFrom();
		this.dueTo = c.dueTo();
		this.labels.clear();
		this.labels.addAll(c.labels());
	}

	Map<String, Object> auditSnapshot() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("ownerId", ownerId.toString());
		map.put("name", name);
		map.put("spaceId", str(spaceId));
		map.put("statusId", str(statusId));
		map.put("labels", List.copyOf(labels.stream().sorted().toList()));
		map.put("executorId", str(executorId));
		map.put("authorId", str(authorId));
		map.put("resolutionState", resolutionState == null ? null : resolutionState.name());
		map.put("view", view == null ? null : view.name());
		map.put("dueFrom", str(dueFrom));
		map.put("dueTo", str(dueTo));
		map.put("archivedAt", str(getArchivedAt()));
		return map;
	}

	private static String str(Object value) {
		return value == null ? null : value.toString();
	}

}
