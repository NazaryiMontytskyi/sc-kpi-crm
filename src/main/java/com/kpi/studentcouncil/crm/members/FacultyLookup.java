package com.kpi.studentcouncil.crm.members;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public lookup of the faculty/institute dictionary for other modules (member profiles, directory filters). */
public interface FacultyLookup {

	/** True only for an existing, non-archived faculty: use it to validate a faculty chosen for a new/changed profile. */
	boolean isSelectable(UUID id);

	/** Resolves a faculty by id, including archived ones (flagged {@code active=false}). */
	Optional<FacultyInfo> resolve(UUID id);

	/** All active faculties sorted by Ukrainian name. */
	List<FacultyInfo> listActive();

}
