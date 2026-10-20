package com.kpi.studentcouncil.crm.tasks.internal;

import java.util.Objects;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A symmetric "related" reference between two tasks (never a parent/child relation). It is a plain relation row, not a
 * business entity, so unlinking removes the row; the change is recorded in the audit events of both tasks.
 */
@Entity
@Table(name = "task_link")
class TaskLink {

	@EmbeddedId
	private TaskLinkId id;

	protected TaskLink() {
	}

	TaskLink(TaskLinkId id) {
		this.id = id;
	}

	TaskLinkId getId() {
		return id;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof TaskLink other && Objects.equals(id, other.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}

}
