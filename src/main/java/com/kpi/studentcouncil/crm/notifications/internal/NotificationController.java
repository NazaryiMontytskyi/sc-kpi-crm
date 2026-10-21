package com.kpi.studentcouncil.crm.notifications.internal;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.kpi.studentcouncil.crm.notifications.NotificationType;
import com.kpi.studentcouncil.crm.shared.pagination.PageParams;
import com.kpi.studentcouncil.crm.shared.pagination.PageResponse;
import com.kpi.studentcouncil.crm.shared.web.ApiV1;

/**
 * The signed-in user's notifications. Any authenticated user may use it; every endpoint acts on the caller's own
 * notifications only (someone else's id yields 404).
 */
@ApiV1
@Tag(name = "Notifications")
class NotificationController {

	private final NotificationService service;

	private final MessageSource messages;

	NotificationController(NotificationService service, MessageSource messages) {
		this.service = service;
		this.messages = messages;
	}

	record NotificationResponse(UUID id, NotificationType type, String targetType, UUID targetId,
			Map<String, Object> payload, String text, boolean read, Instant readAt, Instant createdAt) {
	}

	record UnreadCountResponse(long count) {
	}

	record MarkAllReadResponse(int updated) {
	}

	@GetMapping("/notifications")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "The caller's notifications, newest first (paginated)",
			description = "unread=true returns only unread ones. text is rendered in the caller's locale from the "
					+ "notification.<type> message key; the stored data (type, target, payload) is locale-independent.")
	PageResponse<NotificationResponse> listNotifications(@Valid PageParams page,
			@RequestParam(defaultValue = "false") boolean unread) {
		var pageable = page.toPageable(Set.of("createdAt"), Sort.by(Sort.Direction.DESC, "createdAt", "id"));
		return PageResponse.of(service.list(unread, pageable), this::response);
	}

	@GetMapping("/notifications/unread-count")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "Number of the caller's unread notifications")
	UnreadCountResponse unreadNotificationCount() {
		return new UnreadCountResponse(service.unreadCount());
	}

	@PostMapping("/notifications/{id}/read")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "Mark one of the caller's notifications as read (idempotent)",
			description = "404 NOT_FOUND when the notification does not exist or belongs to someone else.")
	NotificationResponse markNotificationRead(@PathVariable UUID id) {
		return response(service.markRead(id));
	}

	@PostMapping("/notifications/read-all")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "Mark all of the caller's unread notifications as read")
	MarkAllReadResponse markAllNotificationsRead() {
		return new MarkAllReadResponse(service.markAllRead());
	}

	private NotificationResponse response(Notification n) {
		String text = messages.getMessage("notification." + n.getType().name(), null, n.getType().name(),
				LocaleContextHolder.getLocale());
		return new NotificationResponse(n.getId(), n.getType(), n.getTargetType(), n.getTargetId(), n.getPayload(),
				text, n.isRead(), n.getReadAt(), n.getCreatedAt());
	}

}
