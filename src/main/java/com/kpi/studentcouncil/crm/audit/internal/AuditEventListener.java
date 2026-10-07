package com.kpi.studentcouncil.crm.audit.internal;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.audit.AuditEvent;

/**
 * Persists audit events synchronously, in the transaction of the publisher (joins it; starts one if none).
 * A rollback of the business transaction therefore removes the audit row too.
 */
@Component
class AuditEventListener {

	private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
	};

	private final AuditLogRepository repository;

	private final ActorProvider actorProvider;

	private final Clock clock;

	private final JsonMapper jsonMapper;

	AuditEventListener(AuditLogRepository repository, ActorProvider actorProvider, Clock clock,
			JsonMapper jsonMapper) {
		this.repository = repository;
		this.actorProvider = actorProvider;
		this.clock = clock;
		this.jsonMapper = jsonMapper;
	}

	@EventListener
	@Transactional
	void on(AuditEvent event) {
		UUID actor = event.actorId() != null ? event.actorId() : actorProvider.currentActorId();
		repository.save(new AuditLog(actor, event.action(), event.entityType(), event.entityId(),
				toJson(event.before()), toJson(event.after()), clock.instant(), currentClientIp()));
	}

	private Map<String, Object> toJson(Object value) {
		return value == null ? null : jsonMapper.convertValue(value, MAP_TYPE);
	}

	/** Client IP of the current request, or null outside a request. */
	static String currentClientIp() {
		RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
		if (attributes instanceof ServletRequestAttributes servlet) {
			return servlet.getRequest().getRemoteAddr();
		}
		return null;
	}

}
