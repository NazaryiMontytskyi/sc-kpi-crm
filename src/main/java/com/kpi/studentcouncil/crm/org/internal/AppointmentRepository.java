package com.kpi.studentcouncil.crm.org.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Deliberately without delete methods: appointments are history (CONTEXT.md 5.11). */
interface AppointmentRepository extends Repository<Appointment, UUID> {

	Appointment save(Appointment appointment);

	Optional<Appointment> findById(UUID id);

	boolean existsByPositionIdAndUserIdAndEndDateIsNull(UUID positionId, UUID userId);

	long countByPositionIdAndEndDateIsNull(UUID positionId);

	List<Appointment> findByPositionIdAndEndDateIsNull(UUID positionId);

	List<Appointment> findByPositionIdInAndEndDateIsNull(Collection<UUID> positionIds);

	List<Appointment> findByUserIdAndEndDateIsNull(UUID userId);

	Page<Appointment> findByPositionId(UUID positionId, Pageable pageable);

	Page<Appointment> findByPositionIdAndEndDateIsNull(UUID positionId, Pageable pageable);

	Page<Appointment> findByUserId(UUID userId, Pageable pageable);

	Page<Appointment> findByUserIdAndEndDateIsNull(UUID userId, Pageable pageable);

	@Query("select count(a) from Appointment a, Position p where a.positionId = p.id and p.orgUnitId = :unitId"
			+ " and a.endDate is null")
	long countActiveInUnit(@Param("unitId") UUID unitId);

	@Query("select a.userId from Appointment a, Position p where a.positionId = p.id and p.orgUnitId = :unitId"
			+ " and p.head = true and p.archivedAt is null and a.endDate is null")
	List<UUID> activeHeadUserIds(@Param("unitId") UUID unitId);

}
