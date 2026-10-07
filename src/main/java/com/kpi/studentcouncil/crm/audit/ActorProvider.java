package com.kpi.studentcouncil.crm.audit;

import java.util.UUID;

/** Resolves the id of the current actor. INC-004 (identity) provides the authenticated-user implementation. */
public interface ActorProvider {

	/** Nil UUID used for system operations when nobody is authenticated. */
	UUID SYSTEM_ACTOR = new UUID(0L, 0L);

	UUID currentActorId();

}
