package com.kpi.studentcouncil.crm.tasks.internal;

import java.time.LocalDate;
import java.util.ArrayList;
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
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableEntity;

/**
 * A unit of work in a space. There is no task hierarchy; related links live in {@link TaskLink}. The author is the
 * creating user ({@code createdBy}).
 */
@Entity
@Table(name = "task")
class Task extends ArchivableEntity {

	@Column(name = "space_id", nullable = false, updatable = false)
	private UUID spaceId;

	@Column(name = "title", nullable = false, length = 300)
	private String title;

	/** Rich-text document (TipTap JSON), or null. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "description", columnDefinition = "jsonb")
	private Map<String, Object> description;

	@Column(name = "status_id", nullable = false)
	private UUID statusId;

	@Enumerated(EnumType.STRING)
	@Column(name = "priority", nullable = false, length = 10)
	private TaskPriority priority;

	@Column(name = "due_date")
	private LocalDate dueDate;

	/** Custom field values keyed by field definition id; already validated against the space definition. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "custom_field_values", columnDefinition = "jsonb", nullable = false)
	private Map<String, Object> customFieldValues = new LinkedHashMap<>();

	@ElementCollection(fetch = FetchType.EAGER)
	@Fetch(FetchMode.SELECT)
	@BatchSize(size = 50)
	@CollectionTable(name = "task_label", joinColumns = @JoinColumn(name = "task_id"))
	@Column(name = "label", nullable = false, length = 50)
	private Set<String> labels = new LinkedHashSet<>();

	@ElementCollection(fetch = FetchType.EAGER)
	@Fetch(FetchMode.SELECT)
	@BatchSize(size = 50)
	@CollectionTable(name = "task_attachment", joinColumns = @JoinColumn(name = "task_id"))
	@OrderColumn(name = "position")
	@Column(name = "file_id", nullable = false)
	private List<UUID> attachmentIds = new ArrayList<>();

	protected Task() {
	}

	Task(UUID spaceId, String title, Map<String, Object> description, UUID statusId, TaskPriority priority,
			LocalDate dueDate, Set<String> labels, List<UUID> attachmentIds, Map<String, Object> customFieldValues) {
		this.spaceId = spaceId;
		this.statusId = statusId;
		update(title, description, priority, dueDate, labels, attachmentIds, customFieldValues);
	}

	UUID getSpaceId() {
		return spaceId;
	}

	String getTitle() {
		return title;
	}

	Map<String, Object> getDescription() {
		return description;
	}

	UUID getStatusId() {
		return statusId;
	}

	TaskPriority getPriority() {
		return priority;
	}

	LocalDate getDueDate() {
		return dueDate;
	}

	Map<String, Object> getCustomFieldValues() {
		return new LinkedHashMap<>(customFieldValues);
	}

	Set<String> getLabels() {
		return new LinkedHashSet<>(labels);
	}

	List<UUID> getAttachmentIds() {
		return List.copyOf(attachmentIds);
	}

	UUID getAuthorId() {
		return getCreatedBy();
	}

	void update(String title, Map<String, Object> description, TaskPriority priority, LocalDate dueDate,
			Set<String> labels, List<UUID> attachmentIds, Map<String, Object> customFieldValues) {
		this.title = title;
		this.description = description;
		this.priority = priority;
		this.dueDate = dueDate;
		this.labels.clear();
		this.labels.addAll(labels);
		this.attachmentIds.clear();
		this.attachmentIds.addAll(attachmentIds);
		this.customFieldValues.clear();
		this.customFieldValues.putAll(customFieldValues);
	}

	void changeStatus(UUID statusId) {
		this.statusId = statusId;
	}

	/** State recorded in audit events; {@code relatedTaskIds} come from the link store. */
	Map<String, Object> auditSnapshot(Set<UUID> relatedTaskIds) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("spaceId", spaceId.toString());
		map.put("title", title);
		map.put("description", description);
		map.put("statusId", statusId.toString());
		map.put("priority", priority.name());
		map.put("dueDate", dueDate == null ? null : dueDate.toString());
		map.put("labels", labels.stream().sorted().toList());
		map.put("customFieldValues", new LinkedHashMap<>(customFieldValues));
		map.put("attachmentIds", attachmentIds.stream().map(UUID::toString).toList());
		map.put("relatedTaskIds", relatedTaskIds.stream().map(UUID::toString).sorted().toList());
		map.put("archivedAt", getArchivedAt() == null ? null : getArchivedAt().toString());
		return map;
	}

}
