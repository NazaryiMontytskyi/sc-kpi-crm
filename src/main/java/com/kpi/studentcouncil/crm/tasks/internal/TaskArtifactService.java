package com.kpi.studentcouncil.crm.tasks.internal;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.audit.AuditAction;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.files.FileInfo;
import com.kpi.studentcouncil.crm.files.FileService;
import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;
import com.kpi.studentcouncil.crm.shared.error.ConflictException;
import com.kpi.studentcouncil.crm.shared.error.NotFoundException;

/**
 * Artifacts of a task (AR-1..AR-5): uploaded files (by id, through the public {@link FileService} only) and http(s)
 * links. Adding and removing follow the contribute rule of {@link TaskEditPolicy} (creator, active assignee,
 * {@code tasks.edit.any}, Admin). Removal archives the artifact; the file stays in storage. Every change is audited on
 * the task (UPDATE with the active artifact ids and the changed artifact before/after).
 * <p>
 * Legacy attachments ({@code Task.attachmentIds}, still written by the old task create/edit API until INC-082) are
 * mirrored by {@link #syncLegacyAttachments}: a newly attached file gets a FILE artifact, a detached file's active FILE
 * artifact is archived. The mirror is one-way: artifacts added here never change {@code attachmentIds}.
 */
@Service
class TaskArtifactService {

	static final String TASK_ARTIFACT = "TaskArtifact";

	/** An artifact with the metadata of its file (null for a link, or when the file is archived or gone). */
	record ArtifactView(TaskArtifact artifact, FileInfo file) {

		/** Display name: the file name, the link title, or the URL of an untitled link. */
		String name() {
			if (artifact.getType() == TaskArtifactType.FILE) {
				return file == null ? null : file.fileName();
			}
			return artifact.getTitle() != null ? artifact.getTitle() : artifact.getUrl();
		}
	}

	private final TaskRepository tasks;
	private final TaskArtifactRepository artifacts;
	private final FileService files;
	private final TaskEditPolicy editPolicy;
	private final AuditPublisher audit;
	private final ActorProvider actors;
	private final Clock clock;

	TaskArtifactService(TaskRepository tasks, TaskArtifactRepository artifacts, FileService files,
			TaskEditPolicy editPolicy, AuditPublisher audit, ActorProvider actors, Clock clock) {
		this.tasks = tasks;
		this.artifacts = artifacts;
		this.files = files;
		this.editPolicy = editPolicy;
		this.audit = audit;
		this.actors = actors;
		this.clock = clock;
	}

	// ---- commands ----

	/**
	 * Adds a FILE ({@code fileId}, no url/title) or LINK ({@code url}, optional {@code title}) artifact. 404 for an
	 * unknown task, 409 {@code TASK_ARCHIVED} for an archived one, 403 {@code TASK_CONTRIBUTE_FORBIDDEN} without the
	 * right; 422 {@code TASK_ARTIFACT_INVALID} for a field combination that does not match the type, 422
	 * {@code TASK_ARTIFACT_FILE_INVALID} for a missing or archived file, 409 {@code TASK_ARTIFACT_DUPLICATE} when the
	 * file is already an active artifact of the task, 422 {@code TASK_ARTIFACT_URL_INVALID} /
	 * {@code TASK_ARTIFACT_URL_TOO_LONG} for a rejected link (AR-2).
	 */
	@Transactional
	ArtifactView add(UUID taskId, TaskArtifactType type, UUID fileId, String url, String title) {
		Task task = lockActive(taskId);
		editPolicy.assertCanContribute(task);
		UUID actor = actors.currentActorId();
		String cleanTitle = title == null || title.isBlank() ? null : title.strip();
		TaskArtifact artifact;
		FileInfo file = null;
		if (type == TaskArtifactType.FILE) {
			if (fileId == null || url != null || cleanTitle != null) {
				throw new BusinessRuleException("TASK_ARTIFACT_INVALID");
			}
			file = files.find(fileId).orElseThrow(() -> new BusinessRuleException("TASK_ARTIFACT_FILE_INVALID"));
			if (!artifacts.findByTaskIdAndTypeAndFileIdAndArchivedAtIsNull(taskId, TaskArtifactType.FILE, fileId)
					.isEmpty()) {
				throw new ConflictException("TASK_ARTIFACT_DUPLICATE");
			}
			artifact = TaskArtifact.file(taskId, fileId, actor, clock.instant());
		}
		else if (type == TaskArtifactType.LINK) {
			if (fileId != null) {
				throw new BusinessRuleException("TASK_ARTIFACT_INVALID");
			}
			String validUrl = ArtifactUrlValidator.requireValid(url);
			artifact = TaskArtifact.link(taskId, validUrl, cleanTitle, actor, clock.instant());
		}
		else {
			throw new BusinessRuleException("TASK_ARTIFACT_INVALID");
		}
		TaskArtifact saved = insert(taskId, artifact);
		return new ArtifactView(saved, file);
	}

	/**
	 * Archives an active artifact of the task (AR-3); the file stays in storage. Same rights as adding; 404 when the
	 * artifact does not belong to the task or is already removed.
	 */
	@Transactional
	ArtifactView remove(UUID taskId, UUID artifactId) {
		Task task = lockActive(taskId);
		editPolicy.assertCanContribute(task);
		TaskArtifact artifact = artifacts.findByIdAndTaskId(artifactId, taskId).filter(a -> !a.isArchived())
				.orElseThrow(() -> new NotFoundException(TASK_ARTIFACT, artifactId));
		TaskArtifact saved = archive(taskId, artifact);
		return new ArtifactView(saved, fileOf(saved));
	}

	/**
	 * Mirrors a change of the legacy {@code attachmentIds} of a task (create or edit through the old API) into FILE
	 * artifacts, within the caller's transaction (the task is already locked and the caller's rights checked). Files
	 * present in {@code after} but not in {@code before} get a FILE artifact unless one is already active; files
	 * dropped from the list have their active FILE artifacts archived. Each change is audited like an API change.
	 */
	void syncLegacyAttachments(UUID taskId, Collection<UUID> before, Collection<UUID> after) {
		Set<UUID> previous = new LinkedHashSet<>(before == null ? List.of() : before);
		Set<UUID> current = new LinkedHashSet<>(after == null ? List.of() : after);
		UUID actor = actors.currentActorId();
		for (UUID fileId : current) {
			if (!previous.contains(fileId) && artifacts
					.findByTaskIdAndTypeAndFileIdAndArchivedAtIsNull(taskId, TaskArtifactType.FILE, fileId).isEmpty()) {
				insert(taskId, TaskArtifact.file(taskId, fileId, actor, clock.instant()));
			}
		}
		for (UUID fileId : previous) {
			if (!current.contains(fileId)) {
				artifacts.findByTaskIdAndTypeAndFileIdAndArchivedAtIsNull(taskId, TaskArtifactType.FILE, fileId)
						.forEach(a -> archive(taskId, a));
			}
		}
	}

	// ---- queries ----

	/** Artifacts of a task (archived tasks too), oldest first; removed ones only with {@code includeArchived}. */
	@Transactional(readOnly = true)
	List<ArtifactView> list(UUID taskId, boolean includeArchived) {
		if (tasks.findById(taskId).isEmpty()) {
			throw new NotFoundException(TaskService.TASK, taskId);
		}
		List<TaskArtifact> rows = includeArchived ? artifacts.findByTaskIdOrderByAddedAtAscIdAsc(taskId)
				: artifacts.findByTaskIdAndArchivedAtIsNullOrderByAddedAtAscIdAsc(taskId);
		return withFiles(rows);
	}

	/**
	 * Active artifacts of each given task (one query for the artifacts, oldest first); every given id is a key. The
	 * file metadata is looked up per file through {@link FileService#find} (the files module has no batch lookup).
	 */
	Map<UUID, List<ArtifactView>> activeOfAll(Collection<UUID> taskIds) {
		Map<UUID, List<ArtifactView>> result = new HashMap<>();
		if (taskIds.isEmpty()) {
			return result;
		}
		taskIds.forEach(id -> result.put(id, new ArrayList<>()));
		for (ArtifactView view : withFiles(artifacts.findByTaskIdInAndArchivedAtIsNullOrderByAddedAtAscIdAsc(taskIds))) {
			result.get(view.artifact().getTaskId()).add(view);
		}
		return result;
	}

	// ---- helpers ----

	private TaskArtifact insert(UUID taskId, TaskArtifact artifact) {
		List<String> before = activeIds(taskId);
		TaskArtifact saved = artifacts.saveAndFlush(artifact);
		audit.publish(AuditAction.UPDATE, TaskService.TASK, taskId, snapshot(before, null),
				snapshot(activeIds(taskId), saved.auditSnapshot()));
		return saved;
	}

	private TaskArtifact archive(UUID taskId, TaskArtifact artifact) {
		List<String> before = activeIds(taskId);
		Map<String, Object> rowBefore = artifact.auditSnapshot();
		artifact.archive(actors.currentActorId(), clock.instant());
		TaskArtifact saved = artifacts.saveAndFlush(artifact);
		audit.publish(AuditAction.UPDATE, TaskService.TASK, taskId, snapshot(before, rowBefore),
				snapshot(activeIds(taskId), saved.auditSnapshot()));
		return saved;
	}

	private List<ArtifactView> withFiles(List<TaskArtifact> rows) {
		Map<UUID, FileInfo> infos = new HashMap<>();
		for (TaskArtifact a : rows) {
			if (a.getFileId() != null && !infos.containsKey(a.getFileId())) {
				infos.put(a.getFileId(), files.find(a.getFileId()).orElse(null));
			}
		}
		return rows.stream().map(a -> new ArtifactView(a, a.getFileId() == null ? null : infos.get(a.getFileId())))
				.toList();
	}

	private FileInfo fileOf(TaskArtifact artifact) {
		return artifact.getFileId() == null ? null : files.find(artifact.getFileId()).orElse(null);
	}

	private Task lockActive(UUID taskId) {
		Task task = tasks.lockById(taskId).orElseThrow(() -> new NotFoundException(TaskService.TASK, taskId));
		if (task.isArchived()) {
			throw new ConflictException("TASK_ARCHIVED");
		}
		return task;
	}

	private List<String> activeIds(UUID taskId) {
		return artifacts.findByTaskIdAndArchivedAtIsNullOrderByAddedAtAscIdAsc(taskId).stream()
				.map(a -> a.getId().toString()).toList();
	}

	/** Audit state of an artifact change: the active artifact ids and the changed artifact (null before adding). */
	private static Map<String, Object> snapshot(List<String> activeIds, Map<String, Object> artifact) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("artifactIds", activeIds);
		map.put("artifact", artifact);
		return map;
	}

}
