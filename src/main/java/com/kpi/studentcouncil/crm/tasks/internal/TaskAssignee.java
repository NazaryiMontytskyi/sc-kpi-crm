package com.kpi.studentcouncil.crm.tasks.internal;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.kpi.studentcouncil.crm.shared.domain.BaseEntity;

/**
 * A user assigned to a task (AS-1). Unassigning sets {@code removedAt}; the row is kept (AS-4). A user is an active
 * assignee of a task at most once (partial unique index {@code uq_task_assignee_active}). A row of its own (not a
 * user-id collection on the task) so that a per-assignee state can be added later (AS-2, ADR-0010).
 */
@Entity
@Table(name = "task_assignee")
class TaskAssignee extends BaseEntity {

	@Column(name = "task_id", nullable = false, updatable = false)
	private UUID taskId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "assigned_by", nullable = false, updatable = false)
	private UUID assignedBy;

	@Column(name = "assigned_at", nullable = false, updatable = false)
	private Instant assignedAt;

	@Column(name = "removed_at")
	private Instant removedAt;

	@Column(name = "removed_by")
	private UUID removedBy;

	protected TaskAssignee() {
	}

	TaskAssignee(UUID taskId, UUID userId, UUID assignedBy, Instant assignedAt) {
		this.taskId = taskId;
		this.userId = userId;
		this.assignedBy = assignedBy;
		this.assignedAt = assignedAt;
	}

	UUID getTaskId() {
		return taskId;
	}

	UUID getUserId() {
		return userId;
	}

	UUID getAssignedBy() {
		return assignedBy;
	}

	Instant getAssignedAt() {
		return assignedAt;
	}

	Instant getRemovedAt() {
		return removedAt;
	}

	UUID getRemovedBy() {
		return removedBy;
	}

	boolean isActive() {
		return removedAt == null;
	}

	/** Ends the assignment; the row stays as history. */
	void remove(UUID actorId, Instant at) {
		this.removedAt = at;
		this.removedBy = actorId;
	}

	Map<String, Object> auditSnapshot() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("id", getId() == null ? null : getId().toString());
		map.put("userId", userId.toString());
		map.put("assignedBy", assignedBy.toString());
		map.put("assignedAt", assignedAt.toString());
		map.put("removedAt", removedAt == null ? null : removedAt.toString());
		map.put("removedBy", removedBy == null ? null : removedBy.toString());
		return map;
	}

}
