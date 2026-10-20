package com.kpi.studentcouncil.crm.tasks;

import java.util.UUID;

/**
 * Domain event published (inside the transaction) for every user newly mentioned in a comment, on create and on edit
 * (a mention that was already there before an edit is not published again). {@code notifications} subscribes to it.
 *
 * @param userId     the mentioned user
 * @param commentId  the comment
 * @param targetType the kind of record the comment belongs to
 * @param targetId   the commented record
 * @param authorId   the author of the comment
 */
public record UserMentioned(UUID userId, UUID commentId, CommentTargetType targetType, UUID targetId, UUID authorId) {
}
