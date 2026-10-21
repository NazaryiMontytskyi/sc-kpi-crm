package com.kpi.studentcouncil.crm.members;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API of the {@code members} module for the {@code search} module: searchable text of member profiles.
 * Read-only; the index applies {@code members.view} when it answers a query.
 */
public interface MemberTextProvider {

	/** The member text, or empty when the profile does not exist. Archived profiles are returned (flagged). */
	Optional<MemberText> findMember(UUID memberId);

	/** Member ids (equal to user ids) of all non-archived profiles, for a full reindex. */
	List<UUID> findActiveIds();

}
