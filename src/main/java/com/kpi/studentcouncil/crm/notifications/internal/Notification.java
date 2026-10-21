package com.kpi.studentcouncil.crm.notifications.internal;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.kpi.studentcouncil.crm.notifications.NotificationType;
import com.kpi.studentcouncil.crm.shared.domain.BaseEntity;

/** An in-app notification of one user. Never deleted; reading only sets {@code readAt}. */
@Entity
@Table(name = "notification")
class Notification extends BaseEntity {

	@Column(name = "recipient_id", nullable = false, updatable = false)
	private UUID recipientId;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, updatable = false)
	private NotificationType type;

	@Column(name = "target_type", nullable = false, updatable = false)
	private String targetType;

	@Column(name = "target_id", nullable = false, updatable = false)
	private UUID targetId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "payload", nullable = false, columnDefinition = "jsonb", updatable = false)
	private Map<String, Object> payload;

	@Column(name = "read_at")
	private Instant readAt;

	protected Notification() {
	}

	Notification(UUID recipientId, NotificationType type, String targetType, UUID targetId,
			Map<String, Object> payload) {
		this.recipientId = recipientId;
		this.type = type;
		this.targetType = targetType;
		this.targetId = targetId;
		this.payload = payload;
	}

	UUID getRecipientId() {
		return recipientId;
	}

	NotificationType getType() {
		return type;
	}

	String getTargetType() {
		return targetType;
	}

	UUID getTargetId() {
		return targetId;
	}

	Map<String, Object> getPayload() {
		return payload;
	}

	Instant getReadAt() {
		return readAt;
	}

	boolean isRead() {
		return readAt != null;
	}

	/** Marks as read; keeps the first read time. Returns whether anything changed. */
	boolean markRead(Instant at) {
		if (readAt != null) {
			return false;
		}
		readAt = at;
		return true;
	}

}
