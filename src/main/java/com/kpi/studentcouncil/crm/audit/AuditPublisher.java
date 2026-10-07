package com.kpi.studentcouncil.crm.audit;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Convenience API for modules to record audit events. Call it inside the transaction of the change. */
@Component
public class AuditPublisher {

	private final ApplicationEventPublisher publisher;

	AuditPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	public void publish(AuditEvent event) {
		publisher.publishEvent(event);
	}

	public void publish(AuditAction action, String entityType, UUID entityId, Object before, Object after) {
		publish(new AuditEvent(action, entityType, entityId, before, after));
	}

}
