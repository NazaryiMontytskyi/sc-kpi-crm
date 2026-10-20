package com.kpi.studentcouncil.crm.tasks.internal;

import java.time.Instant;
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
import com.kpi.studentcouncil.crm.tasks.CommentTargetType;

/** A comment on a record (task, later resolution). The author is the creating user ({@code createdBy}). */
@Entity
@Table(name = "comment")
class Comment extends ArchivableEntity {

	@Enumerated(EnumType.STRING)
	@Column(name = "target_type", nullable = false, updatable = false, length = 20)
	private CommentTargetType targetType;

	@Column(name = "target_id", nullable = false, updatable = false)
	private UUID targetId;

	/** Rich-text document (TipTap JSON). */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "body", columnDefinition = "jsonb", nullable = false)
	private Map<String, Object> body;

	@Column(name = "edited_at")
	private Instant editedAt;

	@ElementCollection(fetch = FetchType.EAGER)
	@Fetch(FetchMode.SELECT)
	@BatchSize(size = 50)
	@CollectionTable(name = "comment_mention", joinColumns = @JoinColumn(name = "comment_id"))
	@Column(name = "user_id", nullable = false)
	private Set<UUID> mentionUserIds = new LinkedHashSet<>();

	@ElementCollection(fetch = FetchType.EAGER)
	@Fetch(FetchMode.SELECT)
	@BatchSize(size = 50)
	@CollectionTable(name = "comment_attachment", joinColumns = @JoinColumn(name = "comment_id"))
	@OrderColumn(name = "position")
	@Column(name = "file_id", nullable = false)
	private List<UUID> attachmentIds = new ArrayList<>();

	protected Comment() {
	}

	Comment(CommentTargetType targetType, UUID targetId, Map<String, Object> body, Set<UUID> mentionUserIds,
			List<UUID> attachmentIds) {
		this.targetType = targetType;
		this.targetId = targetId;
		this.body = body;
		this.mentionUserIds.addAll(mentionUserIds);
		this.attachmentIds.addAll(attachmentIds);
	}

	CommentTargetType getTargetType() {
		return targetType;
	}

	UUID getTargetId() {
		return targetId;
	}

	Map<String, Object> getBody() {
		return body;
	}

	Instant getEditedAt() {
		return editedAt;
	}

	Set<UUID> getMentionUserIds() {
		return new LinkedHashSet<>(mentionUserIds);
	}

	List<UUID> getAttachmentIds() {
		return List.copyOf(attachmentIds);
	}

	UUID getAuthorId() {
		return getCreatedBy();
	}

	void edit(Map<String, Object> body, Set<UUID> mentionUserIds, List<UUID> attachmentIds, Instant at) {
		this.body = body;
		this.mentionUserIds.clear();
		this.mentionUserIds.addAll(mentionUserIds);
		this.attachmentIds.clear();
		this.attachmentIds.addAll(attachmentIds);
		this.editedAt = at;
	}

	/** State recorded in audit events. */
	Map<String, Object> auditSnapshot() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("targetType", targetType.name());
		map.put("targetId", targetId.toString());
		map.put("body", body);
		map.put("mentionUserIds", mentionUserIds.stream().map(UUID::toString).sorted().toList());
		map.put("attachmentIds", attachmentIds.stream().map(UUID::toString).toList());
		map.put("archivedAt", getArchivedAt() == null ? null : getArchivedAt().toString());
		return map;
	}

}
