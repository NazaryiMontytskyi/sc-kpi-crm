package com.kpi.studentcouncil.crm.audit;

import java.util.Objects;
import java.util.UUID;

/**
 * Public audit event. Other modules publish it (preferably via {@link AuditPublisher}) inside the transaction
 * of the change; it is persisted in that same transaction.
 *
 * @param action     what happened
 * @param entityType logical entity type, e.g. "Resolution"
 * @param entityId   id of the entity, may be null (e.g. a failed sign-in)
 * @param before     state before the change (any JSON-serialisable object), may be null
 * @param after      state after the change (any JSON-serialisable object), may be null
 * @param actorId    explicit actor; null means "resolve via {@link ActorProvider}"
 */
public record AuditEvent(AuditAction action, String entityType, UUID entityId, Object before, Object after,
		UUID actorId) {

	public AuditEvent {
		Objects.requireNonNull(action, "action");
		Objects.requireNonNull(entityType, "entityType");
	}

	public AuditEvent(AuditAction action, String entityType, UUID entityId, Object before, Object after) {
		this(action, entityType, entityId, before, after, null);
	}

}
