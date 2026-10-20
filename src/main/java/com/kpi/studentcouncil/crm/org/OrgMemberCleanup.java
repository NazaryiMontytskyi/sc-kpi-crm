package com.kpi.studentcouncil.crm.org;

import java.util.UUID;

/**
 * Public command API of the org module for the member lifecycle: ends everything a person holds in the
 * organisation structure. Called by the members module when a member is archived (the dependency points from
 * members to org only, so the modules stay free of cycles). Runs in the caller's transaction.
 */
public interface OrgMemberCleanup {

	/** Ends all active memberships and appointments of the user (appointments revoke the roles they granted). */
	void endAllOfUser(UUID userId);

}
