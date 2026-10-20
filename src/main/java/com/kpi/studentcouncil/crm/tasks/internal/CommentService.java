package com.kpi.studentcouncil.crm.tasks.internal;

import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kpi.studentcouncil.crm.access.AccessService;
import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.audit.AuditAction;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.files.FileInfo;
import com.kpi.studentcouncil.crm.files.FileService;
import com.kpi.studentcouncil.crm.identity.UserDirectory;
import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;
import com.kpi.studentcouncil.crm.shared.error.ConflictException;
import com.kpi.studentcouncil.crm.shared.error.ForbiddenException;
import com.kpi.studentcouncil.crm.shared.error.NotFoundException;
import com.kpi.studentcouncil.crm.tasks.CommentTarget;
import com.kpi.studentcouncil.crm.tasks.CommentTargetType;
import com.kpi.studentcouncil.crm.tasks.UserMentioned;

/**
 * Commands and queries of comments. Anybody authenticated may comment on a record that accepts comments; reading
 * needs the view permission of the target; edit and archive follow {@link CommentPolicy}.
 */
@Service
class CommentService {

	static final String COMMENT = "Comment";

	/** A comment together with the data that lives outside its row. */
	record CommentView(Comment comment, List<FileInfo> attachments) {
	}

	private final CommentRepository comments;
	private final Map<CommentTargetType, CommentTarget> targets = new EnumMap<>(CommentTargetType.class);
	private final FileService files;
	private final UserDirectory users;
	private final AccessService access;
	private final CommentPolicy policy;
	private final AuditPublisher audit;
	private final ActorProvider actors;
	private final ApplicationEventPublisher events;
	private final Clock clock;

	CommentService(CommentRepository comments, List<CommentTarget> targets, FileService files, UserDirectory users,
			AccessService access, CommentPolicy policy, AuditPublisher audit, ActorProvider actors,
			ApplicationEventPublisher events, Clock clock) {
		this.comments = comments;
		targets.forEach(t -> this.targets.put(t.type(), t));
		this.files = files;
		this.users = users;
		this.access = access;
		this.policy = policy;
		this.audit = audit;
		this.actors = actors;
		this.events = events;
		this.clock = clock;
	}

	// ---- commands ----

	@Transactional
	CommentView create(CommentTargetType type, UUID targetId, Map<String, Object> body, List<UUID> mentionUserIds,
			List<UUID> attachmentIds) {
		CommentTarget target = target(type);
		target.assertAcceptsComments(targetId);
		List<UUID> attachments = CommentRules.normalizeAttachments(attachmentIds);
		CommentRules.validateBody(body, !attachments.isEmpty());
		Set<UUID> mentions = CommentRules.checkMentions(mentionUserIds, body);
		mentions.forEach(this::assertMentionable);
		attachments.forEach(this::assertFileUsable);

		Comment saved = comments.save(new Comment(type, targetId, body, mentions, attachments));
		audit.publish(AuditAction.CREATE, COMMENT, saved.getId(), null, saved.auditSnapshot());
		publishMentions(saved, mentions);
		return view(saved);
	}

	/** Replaces body, mentions and attachments. Author or Admin only ({@link CommentPolicy}). */
	@Transactional
	CommentView edit(UUID id, Map<String, Object> body, List<UUID> mentionUserIds, List<UUID> attachmentIds) {
		Comment comment = lockActive(id);
		policy.assertCanModify(comment);
		List<UUID> attachments = CommentRules.normalizeAttachments(attachmentIds);
		CommentRules.validateBody(body, !attachments.isEmpty());
		Set<UUID> mentions = CommentRules.checkMentions(mentionUserIds, body);
		Set<UUID> before = comment.getMentionUserIds();
		Set<UUID> added = new LinkedHashSet<>(mentions);
		added.removeAll(before);
		// users already mentioned may have been archived since; only new mentions are checked
		added.forEach(this::assertMentionable);
		Set<UUID> keptFiles = new LinkedHashSet<>(comment.getAttachmentIds());
		attachments.stream().filter(a -> !keptFiles.contains(a)).forEach(this::assertFileUsable);

		Map<String, Object> beforeSnapshot = comment.auditSnapshot();
		comment.edit(body, mentions, attachments, clock.instant());
		Comment saved = comments.save(comment);
		audit.publish(AuditAction.UPDATE, COMMENT, id, beforeSnapshot, saved.auditSnapshot());
		publishMentions(saved, added);
		return view(saved);
	}

	/** Soft delete: the comment disappears from lists, the row and its history stay. Author or Admin only. */
	@Transactional
	CommentView archive(UUID id) {
		Comment comment = comments.lockById(id).orElseThrow(() -> new NotFoundException(COMMENT, id));
		policy.assertCanModify(comment);
		Map<String, Object> before = comment.auditSnapshot();
		comment.archive(actors.currentActorId(), clock.instant());
		Comment saved = comments.save(comment);
		audit.publish(AuditAction.ARCHIVE, COMMENT, id, before, saved.auditSnapshot());
		return view(saved);
	}

	// ---- queries ----

	/** Active comments of the record, oldest first; archived ones are hidden. */
	@Transactional(readOnly = true)
	Page<CommentView> list(CommentTargetType type, UUID targetId, Pageable pageable) {
		CommentTarget target = target(type);
		UUID actor = actors.currentActorId();
		if (!access.hasPermission(actor, target.viewPermission())) {
			throw new ForbiddenException("FORBIDDEN");
		}
		return comments.findByTargetTypeAndTargetIdAndArchivedAtIsNull(type, targetId, pageable).map(this::view);
	}

	// ---- helpers ----

	private CommentTarget target(CommentTargetType type) {
		CommentTarget target = targets.get(type);
		if (target == null) {
			throw new BusinessRuleException("COMMENT_TARGET_UNSUPPORTED", type.name());
		}
		return target;
	}

	private Comment lockActive(UUID id) {
		Comment comment = comments.lockById(id).orElseThrow(() -> new NotFoundException(COMMENT, id));
		if (comment.isArchived()) {
			throw new ConflictException("COMMENT_ARCHIVED");
		}
		return comment;
	}

	private void assertMentionable(UUID userId) {
		if (!users.isActive(userId)) {
			throw new BusinessRuleException("COMMENT_MENTION_INVALID");
		}
	}

	private void assertFileUsable(UUID fileId) {
		try {
			files.require(fileId);
		}
		catch (NotFoundException e) {
			throw new BusinessRuleException("COMMENT_ATTACHMENT_INVALID");
		}
	}

	private void publishMentions(Comment comment, Set<UUID> newlyMentioned) {
		for (UUID user : newlyMentioned) {
			events.publishEvent(new UserMentioned(user, comment.getId(), comment.getTargetType(),
					comment.getTargetId(), comment.getAuthorId()));
		}
	}

	private CommentView view(Comment comment) {
		List<FileInfo> infos = new ArrayList<>();
		for (UUID fileId : comment.getAttachmentIds()) {
			files.find(fileId).ifPresent(infos::add);
		}
		return new CommentView(comment, infos);
	}

}
