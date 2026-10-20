package com.kpi.studentcouncil.crm.tasks.internal;

import java.time.Clock;
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

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableEntity;
import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;

/**
 * An assignment to carry out a task: author to executor (+ co-executors), with an instruction and a due date. All state
 * changes go through {@link ResolutionStateMachine}; each one appends a history entry.
 */
@Entity
@Table(name = "resolution")
class Resolution extends ArchivableEntity {

	@Column(name = "task_id", nullable = false, updatable = false)
	private UUID taskId;

	@Column(name = "author_id", nullable = false, updatable = false)
	private UUID authorId;

	@Column(name = "executor_id", nullable = false)
	private UUID executorId;

	@ElementCollection(fetch = FetchType.EAGER)
	@Fetch(FetchMode.SELECT)
	@BatchSize(size = 50)
	@CollectionTable(name = "resolution_co_executor", joinColumns = @JoinColumn(name = "resolution_id"))
	@Column(name = "user_id", nullable = false)
	private Set<UUID> coExecutorIds = new LinkedHashSet<>();

	@Column(name = "instruction", nullable = false)
	private String instruction;

	@Column(name = "due_date", nullable = false)
	private LocalDate dueDate;

	@Column(name = "parent_resolution_id", updatable = false)
	private UUID parentResolutionId;

	@Enumerated(EnumType.STRING)
	@Column(name = "state", nullable = false, length = 20)
	private ResolutionState state;

	@Column(name = "completion_note")
	private String completionNote;

	@ElementCollection(fetch = FetchType.EAGER)
	@Fetch(FetchMode.SELECT)
	@BatchSize(size = 50)
	@CollectionTable(name = "resolution_history", joinColumns = @JoinColumn(name = "resolution_id"))
	@OrderColumn(name = "position")
	private List<ResolutionHistoryEntry> history = new ArrayList<>();

	protected Resolution() {
	}

	/** Creates a resolution in state NEW; the creation is the first history entry. */
	Resolution(UUID taskId, UUID authorId, UUID executorId, Set<UUID> coExecutorIds, String instruction,
			LocalDate dueDate, UUID parentResolutionId, Instant at) {
		if (instruction == null || instruction.isBlank()) {
			throw new BusinessRuleException("RESOLUTION_INSTRUCTION_REQUIRED");
		}
		if (dueDate == null) {
			throw new BusinessRuleException("RESOLUTION_DUE_DATE_REQUIRED");
		}
		this.taskId = taskId;
		this.authorId = authorId;
		this.executorId = executorId;
		this.coExecutorIds.addAll(coExecutorIds);
		this.coExecutorIds.remove(executorId);
		this.instruction = instruction.strip();
		this.dueDate = dueDate;
		this.parentResolutionId = parentResolutionId;
		this.state = ResolutionState.NEW;
		this.history.add(new ResolutionHistoryEntry(authorId, null, ResolutionState.NEW, null, at, null));
	}

	UUID getTaskId() {
		return taskId;
	}

	UUID getAuthorId() {
		return authorId;
	}

	UUID getExecutorId() {
		return executorId;
	}

	Set<UUID> getCoExecutorIds() {
		return new LinkedHashSet<>(coExecutorIds);
	}

	String getInstruction() {
		return instruction;
	}

	LocalDate getDueDate() {
		return dueDate;
	}

	UUID getParentResolutionId() {
		return parentResolutionId;
	}

	ResolutionState getState() {
		return state;
	}

	String getCompletionNote() {
		return completionNote;
	}

	List<ResolutionHistoryEntry> getHistory() {
		return List.copyOf(history);
	}

	/**
	 * Performs {@code action} as {@code actorId}: validates it with the state machine, changes the state and appends a
	 * history entry. Nothing changes when it throws.
	 *
	 * @param comment completion note (SUBMIT) or rework comment (REQUEST_REWORK); optional note for other actions
	 */
	void apply(ResolutionAction action, UUID actorId, String comment, Instant at) {
		ResolutionState to = ResolutionStateMachine.check(state, action, actorId, authorId, executorId, comment);
		String text = comment == null || comment.isBlank() ? null : comment.strip();
		if (action == ResolutionAction.SUBMIT) {
			this.completionNote = text;
		}
		transition(to, actorId, text, at, null);
	}

	/**
	 * Cancels this resolution because the ancestor {@code causedBy} was canceled by {@code actorId}. The actor needs no
	 * role here (the author's right was checked on the ancestor); the resolution must not be final.
	 */
	void cancelByCascade(UUID actorId, UUID causedBy, String comment, Instant at) {
		ResolutionState to = ResolutionStateMachine.target(state, ResolutionAction.CANCEL);
		transition(to, actorId, comment, at, causedBy);
	}

	private void transition(ResolutionState to, UUID actorId, String comment, Instant at, UUID causedBy) {
		history.add(new ResolutionHistoryEntry(actorId, state, to, comment, at, causedBy));
		this.state = to;
	}

	boolean isOverdue(Clock clock) {
		return ResolutionStateMachine.isOverdue(state, dueDate, clock);
	}

	/** State recorded in audit events. */
	Map<String, Object> auditSnapshot() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("taskId", taskId.toString());
		map.put("authorId", authorId.toString());
		map.put("executorId", executorId.toString());
		map.put("coExecutorIds", coExecutorIds.stream().map(UUID::toString).sorted().toList());
		map.put("instruction", instruction);
		map.put("dueDate", dueDate.toString());
		map.put("parentResolutionId", parentResolutionId == null ? null : parentResolutionId.toString());
		map.put("state", state.name());
		map.put("completionNote", completionNote);
		map.put("archivedAt", getArchivedAt() == null ? null : getArchivedAt().toString());
		return map;
	}

}
