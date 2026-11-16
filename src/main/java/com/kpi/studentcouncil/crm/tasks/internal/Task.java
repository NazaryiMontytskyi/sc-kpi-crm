package com.kpi.studentcouncil.crm.tasks.internal;

import java.time.Instant;
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
import jakarta.persistence.Version;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableEntity;
import com.kpi.studentcouncil.crm.shared.error.ConflictException;

/**
 * A unit of work in a space. Related links live in {@link TaskLink}. The creator ({@code creatorId}) is the user who
 * issued the task; {@code parentTaskId} marks a subtask (one level only, ADR-0010). Concurrent edits are detected by
 * {@code version} (optimistic locking).
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

	/** Due at the end of this day in Europe/Kyiv; formerly {@code due_date}. */
	@Column(name = "deadline")
	private LocalDate deadline;

	@Column(name = "creator_id", nullable = false, updatable = false)
	private UUID creatorId;

	/** Issue date (UTC instant), set once on creation. */
	@Column(name = "issued_at", nullable = false, updatable = false)
	private Instant issuedAt;

	/** Parent of a subtask; null for a top-level task. */
	@Column(name = "parent_task_id", updatable = false)
	private UUID parentTaskId;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

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

	Task(UUID spaceId, UUID creatorId, Instant issuedAt, String title, Map<String, Object> description,
			UUID statusId, TaskPriority priority, LocalDate deadline, Set<String> labels, List<UUID> attachmentIds,
			Map<String, Object> customFieldValues) {
		this(spaceId, null, creatorId, issuedAt, title, description, statusId, priority, deadline, labels,
				attachmentIds, customFieldValues);
	}

	/** A subtask of {@code parentTaskId} (null: a top-level task); the parent never changes afterwards. */
	Task(UUID spaceId, UUID parentTaskId, UUID creatorId, Instant issuedAt, String title,
			Map<String, Object> description, UUID statusId, TaskPriority priority, LocalDate deadline,
			Set<String> labels, List<UUID> attachmentIds, Map<String, Object> customFieldValues) {
		this.spaceId = spaceId;
		this.parentTaskId = parentTaskId;
		this.creatorId = creatorId;
		this.issuedAt = issuedAt;
		this.statusId = statusId;
		update(title, description, priority, deadline, labels, attachmentIds, customFieldValues);
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

	LocalDate getDeadline() {
		return deadline;
	}

	UUID getCreatorId() {
		return creatorId;
	}

	Instant getIssuedAt() {
		return issuedAt;
	}

	UUID getParentTaskId() {
		return parentTaskId;
	}

	long getVersion() {
		return version;
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

	/** Legacy name of the creator, kept for the {@code authorId} API field. */
	UUID getAuthorId() {
		return creatorId;
	}

	void update(String title, Map<String, Object> description, TaskPriority priority, LocalDate deadline,
			Set<String> labels, List<UUID> attachmentIds, Map<String, Object> customFieldValues) {
		this.title = title;
		this.description = description;
		this.priority = priority;
		this.deadline = deadline;
		this.labels.clear();
		this.labels.addAll(labels);
		this.attachmentIds.clear();
		this.attachmentIds.addAll(attachmentIds);
		this.customFieldValues.clear();
		this.customFieldValues.putAll(customFieldValues);
	}

	/**
	 * Optimistic-locking guard: {@code expectedVersion} is the version the caller last read; null skips the check.
	 * A different current version is a conflict (409 {@code TASK_VERSION_CONFLICT}, argument: current version).
	 */
	void assertVersion(Long expectedVersion) {
		if (expectedVersion != null && expectedVersion != version) {
			throw new ConflictException("TASK_VERSION_CONFLICT", version);
		}
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
		map.put("deadline", deadline == null ? null : deadline.toString());
		map.put("issuedAt", issuedAt == null ? null : issuedAt.toString());
		map.put("creatorId", creatorId == null ? null : creatorId.toString());
		map.put("parentId", parentTaskId == null ? null : parentTaskId.toString());
		map.put("version", version);
		map.put("labels", labels.stream().sorted().toList());
		map.put("customFieldValues", new LinkedHashMap<>(customFieldValues));
		map.put("attachmentIds", attachmentIds.stream().map(UUID::toString).toList());
		map.put("relatedTaskIds", relatedTaskIds.stream().map(UUID::toString).sorted().toList());
		map.put("archivedAt", getArchivedAt() == null ? null : getArchivedAt().toString());
		return map;
	}

}
