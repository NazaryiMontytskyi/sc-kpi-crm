package com.kpi.studentcouncil.crm.tasks;

import java.util.UUID;

/**
 * Extension point: one bean per {@link CommentTargetType} tells the comments how to treat the commented record. The
 * tasks module implements it for {@code TASK}; the governance module implements it for {@code RESOLUTION} once
 * resolutions exist. Until a bean exists for a type, commenting on it is rejected with
 * {@code COMMENT_TARGET_UNSUPPORTED}.
 */
public interface CommentTarget {

	CommentTargetType type();

	/**
	 * Called before a comment is added to the record.
	 *
	 * @throws com.kpi.studentcouncil.crm.shared.error.NotFoundException when the record does not exist
	 * @throws com.kpi.studentcouncil.crm.shared.error.ConflictException when the record no longer accepts comments
	 */
	void assertAcceptsComments(UUID targetId);

	/** Permission key needed to read the comments of the record (the permission to view the record itself). */
	String viewPermission();

}
