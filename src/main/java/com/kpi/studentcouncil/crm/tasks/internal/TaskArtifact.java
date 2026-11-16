package com.kpi.studentcouncil.crm.tasks.internal;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableEntity;

/**
 * A file or a link attached to a task (AR-1). A FILE artifact holds the id of a file of the files module (never a
 * path); a LINK artifact an http(s) URL ({@link ArtifactUrlValidator}) and an optional title. Removal archives the row
 * (AR-3); the file itself stays in storage. Nothing here is ever changed after creation except the archive state.
 */
@Entity
@Table(name = "task_artifact")
class TaskArtifact extends ArchivableEntity {

	@Column(name = "task_id", nullable = false, updatable = false)
	private UUID taskId;

	@Enumerated(EnumType.STRING)
	@Column(name = "artifact_type", nullable = false, updatable = false, length = 10)
	private TaskArtifactType type;

	@Column(name = "file_id", updatable = false)
	private UUID fileId;

	@Column(name = "url", updatable = false, length = 2048)
	private String url;

	@Column(name = "title", updatable = false, length = 300)
	private String title;

	@Column(name = "added_by", nullable = false, updatable = false)
	private UUID addedBy;

	@Column(name = "added_at", nullable = false, updatable = false)
	private Instant addedAt;

	protected TaskArtifact() {
	}

	private TaskArtifact(UUID taskId, TaskArtifactType type, UUID fileId, String url, String title, UUID addedBy,
			Instant addedAt) {
		this.taskId = taskId;
		this.type = type;
		this.fileId = fileId;
		this.url = url;
		this.title = title;
		this.addedBy = addedBy;
		this.addedAt = addedAt;
	}

	static TaskArtifact file(UUID taskId, UUID fileId, UUID addedBy, Instant addedAt) {
		return new TaskArtifact(taskId, TaskArtifactType.FILE, fileId, null, null, addedBy, addedAt);
	}

	/** {@code url} must already be validated; {@code title} may be null. */
	static TaskArtifact link(UUID taskId, String url, String title, UUID addedBy, Instant addedAt) {
		return new TaskArtifact(taskId, TaskArtifactType.LINK, null, url, title, addedBy, addedAt);
	}

	UUID getTaskId() {
		return taskId;
	}

	TaskArtifactType getType() {
		return type;
	}

	UUID getFileId() {
		return fileId;
	}

	String getUrl() {
		return url;
	}

	String getTitle() {
		return title;
	}

	UUID getAddedBy() {
		return addedBy;
	}

	Instant getAddedAt() {
		return addedAt;
	}

	Map<String, Object> auditSnapshot() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("id", getId() == null ? null : getId().toString());
		map.put("type", type.name());
		map.put("fileId", fileId == null ? null : fileId.toString());
		map.put("url", url);
		map.put("title", title);
		map.put("addedBy", addedBy.toString());
		map.put("addedAt", addedAt.toString());
		map.put("archivedAt", getArchivedAt() == null ? null : getArchivedAt().toString());
		map.put("archivedBy", getArchivedBy() == null ? null : getArchivedBy().toString());
		return map;
	}

}
