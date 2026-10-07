package com.kpi.studentcouncil.crm.identity.internal;

import java.util.Optional;
import java.util.UUID;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableRepository;

/** Account lookups. Logins are unique across archived accounts too, so lookups here include archived rows. */
interface UserRepository extends ArchivableRepository<User, UUID> {

	/** {@code login} must be normalised. Includes archived/blocked accounts. */
	Optional<User> findByLogin(String login);

	long count();

}
