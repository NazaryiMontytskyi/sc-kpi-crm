package com.kpi.studentcouncil.crm.members;

import java.util.UUID;

/**
 * Published synchronously, inside the transaction, just before a member is archived. Extension point for the
 * Handover feature [v2]: a listener may veto the archiving by throwing a
 * {@link com.kpi.studentcouncil.crm.shared.error.DomainException}; the whole operation is then rolled back.
 *
 * @param memberId the member about to be archived, equal to the user id
 */
public record MemberArchiving(UUID memberId) {
}
