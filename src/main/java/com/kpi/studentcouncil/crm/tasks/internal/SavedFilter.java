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

/**
 * A task list filter a user saved under a name. Private to its owner; removing it archives it. The legacy resolution
 * criteria columns of the table (V17) are no longer mapped: never written, ignored when read.
 */
@Entity
@Table(name = "task_saved_filter")
class SavedFilter extends ArchivableEntity {

	/** The stored criteria; they mirror the query parameters of the task list. */
	record Criteria(UUID spaceId, UUID statusId, Set<String> labels, UUID assignee, UUID creator, boolean overdue,
			TaskQuickView view, LocalDate deadlineFrom, LocalDate deadlineTo) {
	}

	@Column(name = "owner_id", nullable = false, updatable = false)
	private UUID ownerId;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "space_id")
	private UUID spaceId;

	@Column(name = "status_id")
	private UUID statusId;

	@Column(name = "assignee_id")
	private UUID assignee;

	@Column(name = "creator_id")
	private UUID creator;

	@Column(name = "overdue", nullable = false)
	private boolean overdue;

	@Enumerated(EnumType.STRING)
	@Column(name = "quick_view", length = 20)
	private TaskQuickView view;

	@Column(name = "deadline_from")
	private LocalDate deadlineFrom;

	@Column(name = "deadline_to")
	private LocalDate deadlineTo;

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
		return new Criteria(spaceId, statusId, new LinkedHashSet<>(labels), assignee, creator, overdue, view,
				deadlineFrom, deadlineTo);
	}

	void update(String name, Criteria c) {
		this.name = name;
		this.spaceId = c.spaceId();
		this.statusId = c.statusId();
		this.assignee = c.assignee();
		this.creator = c.creator();
		this.overdue = c.overdue();
		this.view = c.view();
		this.deadlineFrom = c.deadlineFrom();
		this.deadlineTo = c.deadlineTo();
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
		map.put("assignee", str(assignee));
		map.put("creator", str(creator));
		map.put("overdue", overdue);
		map.put("view", view == null ? null : view.name());
		map.put("deadlineFrom", str(deadlineFrom));
		map.put("deadlineTo", str(deadlineTo));
		map.put("archivedAt", str(getArchivedAt()));
		return map;
	}

	private static String str(Object value) {
		return value == null ? null : value.toString();
	}

}
