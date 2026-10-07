package com.kpi.studentcouncil.crm.audit.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

/** Append-only: deliberately exposes only insert and read methods (no update/delete). */
public interface AuditLogRepository extends Repository<AuditLog, UUID> {

	AuditLog save(AuditLog log);

	Optional<AuditLog> findById(UUID id);

	long count();

}
