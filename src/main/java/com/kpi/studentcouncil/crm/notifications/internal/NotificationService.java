package com.kpi.studentcouncil.crm.notifications.internal;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.audit.AuditAction;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.shared.error.NotFoundException;

/** The caller's own inbox. Every operation is scoped to the current user; others' notifications look non-existent. */
@Service
class NotificationService {

	static final String ENTITY = "Notification";

	private final NotificationRepository repository;

	private final ActorProvider actors;

	private final AuditPublisher audit;

	private final Clock clock;

	NotificationService(NotificationRepository repository, ActorProvider actors, AuditPublisher audit, Clock clock) {
		this.repository = repository;
		this.actors = actors;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	Page<Notification> list(boolean unreadOnly, Pageable pageable) {
		UUID me = actors.currentActorId();
		return unreadOnly ? repository.findByRecipientIdAndReadAtIsNull(me, pageable)
				: repository.findByRecipientId(me, pageable);
	}

	@Transactional(readOnly = true)
	long unreadCount() {
		return repository.countByRecipientIdAndReadAtIsNull(actors.currentActorId());
	}

	/** Marks one of the caller's notifications read (idempotent). 404 when it is not the caller's. */
	@Transactional
	Notification markRead(UUID id) {
		UUID me = actors.currentActorId();
		Notification notification = repository.findByIdAndRecipientId(id, me)
				.orElseThrow(() -> new NotFoundException(ENTITY, id));
		if (notification.markRead(clock.instant())) {
			Notification saved = repository.save(notification);
			audit.publish(AuditAction.UPDATE, ENTITY, id, Map.of("read", false), Map.of("read", true));
			return saved;
		}
		return notification;
	}

	/** Marks all unread notifications of the caller read; returns how many changed. */
	@Transactional
	int markAllRead() {
		UUID me = actors.currentActorId();
		int changed = repository.markAllRead(me, clock.instant());
		if (changed > 0) {
			audit.publish(AuditAction.UPDATE, ENTITY, null, Map.of("unread", changed), Map.of("unread", 0));
		}
		return changed;
	}

}
