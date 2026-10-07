package com.kpi.studentcouncil.crm.audit.internal;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.kpi.studentcouncil.crm.audit.AuditAction;

/** Immutable audit record; the database additionally rejects UPDATE/DELETE. */
@Entity
@Immutable
@Table(name = "audit_log")
public class AuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "actor_id", updatable = false)
	private UUID actorId;

	@Enumerated(EnumType.STRING)
	@Column(name = "action", nullable = false, updatable = false)
	private AuditAction action;

	@Column(name = "entity_type", nullable = false, updatable = false)
	private String entityType;

	@Column(name = "entity_id", updatable = false)
	private UUID entityId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "before_state", columnDefinition = "jsonb", updatable = false)
	private Map<String, Object> before;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "after_state", columnDefinition = "jsonb", updatable = false)
	private Map<String, Object> after;

	@Column(name = "at", nullable = false, updatable = false)
	private Instant at;

	@Column(name = "ip", length = 45, updatable = false)
	private String ip;

	protected AuditLog() {
	}

	public AuditLog(UUID actorId, AuditAction action, String entityType, UUID entityId, Map<String, Object> before,
			Map<String, Object> after, Instant at, String ip) {
		this.actorId = actorId;
		this.action = action;
		this.entityType = entityType;
		this.entityId = entityId;
		this.before = before;
		this.after = after;
		this.at = at;
		this.ip = ip;
	}

	public UUID getId() {
		return id;
	}

	public UUID getActorId() {
		return actorId;
	}

	public AuditAction getAction() {
		return action;
	}

	public String getEntityType() {
		return entityType;
	}

	public UUID getEntityId() {
		return entityId;
	}

	public Map<String, Object> getBefore() {
		return before;
	}

	public Map<String, Object> getAfter() {
		return after;
	}

	public Instant getAt() {
		return at;
	}

	public String getIp() {
		return ip;
	}

}
