package com.kpi.studentcouncil.crm.notifications.internal;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface NotificationRepository extends JpaRepository<Notification, UUID> {

	Page<Notification> findByRecipientId(UUID recipientId, Pageable pageable);

	Page<Notification> findByRecipientIdAndReadAtIsNull(UUID recipientId, Pageable pageable);

	Optional<Notification> findByIdAndRecipientId(UUID id, UUID recipientId);

	long countByRecipientIdAndReadAtIsNull(UUID recipientId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update Notification n set n.readAt = :at, n.updatedAt = :at, n.updatedBy = :recipientId"
			+ " where n.recipientId = :recipientId and n.readAt is null")
	int markAllRead(@Param("recipientId") UUID recipientId, @Param("at") Instant at);

}
