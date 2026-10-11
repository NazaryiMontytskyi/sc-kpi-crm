package com.kpi.studentcouncil.crm.org.internal;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.kpi.studentcouncil.crm.org.internal.OrgDtos.AppointRequest;
import com.kpi.studentcouncil.crm.org.internal.OrgDtos.AppointmentResponse;
import com.kpi.studentcouncil.crm.org.internal.OrgDtos.CreatePositionRequest;
import com.kpi.studentcouncil.crm.org.internal.OrgDtos.EndAppointmentRequest;
import com.kpi.studentcouncil.crm.org.internal.OrgDtos.PositionResponse;
import com.kpi.studentcouncil.crm.org.internal.OrgDtos.UpdatePositionRequest;
import com.kpi.studentcouncil.crm.shared.pagination.PageParams;
import com.kpi.studentcouncil.crm.shared.pagination.PageResponse;
import com.kpi.studentcouncil.crm.shared.web.ApiV1;

/**
 * Positions and appointments API. Reads need authentication only (openness); position CRUD needs
 * {@code positions.manage}, appointing and ending need {@code positions.appoint} (separate permissions).
 */
@ApiV1
@Tag(name = "Org positions")
class PositionController {

	private static final Set<String> POSITION_SORTABLE = Set.of("title", "createdAt");
	private static final Sort POSITION_SORT = Sort.by("title").and(Sort.by("id"));
	private static final Set<String> APPOINTMENT_SORTABLE = Set.of("startDate", "endDate");
	private static final Sort APPOINTMENT_SORT = Sort.by(Sort.Direction.DESC, "startDate").and(Sort.by("id"));

	private final PositionService positions;
	private final AppointmentService appointments;

	PositionController(PositionService positions, AppointmentService appointments) {
		this.positions = positions;
		this.appointments = appointments;
	}

	@GetMapping("/org-units/{id}/positions")
	@Operation(summary = "Positions of a unit (paginated, sortable by title, createdAt; default title)",
			description = "Archived positions only with includeArchived=true. Each position lists its active holders; "
					+ "a position without holders is vacant.")
	PageResponse<PositionResponse> ofUnit(@PathVariable UUID id, @Valid PageParams page,
			@RequestParam(defaultValue = "false") boolean includeArchived) {
		Page<Position> result = positions.listOfUnit(id, includeArchived,
				page.toPageable(POSITION_SORTABLE, POSITION_SORT));
		Map<UUID, List<UUID>> holders = positions.activeHolders(result.map(Position::getId).getContent());
		return PageResponse.of(result, p -> toResponse(p, holders.getOrDefault(p.getId(), List.of())));
	}

	@GetMapping("/positions/{id}")
	@Operation(summary = "Get one position, including archived ones")
	PositionResponse get(@PathVariable UUID id) {
		return withHolders(positions.get(id));
	}

	@PostMapping("/org-units/{id}/positions")
	@PreAuthorize("hasAuthority('positions.manage')")
	@Operation(summary = "Create a position in a unit",
			description = "Bound roles must exist and cannot be Admin (403). A unit has at most one active head "
					+ "position (409); a head position has capacity 1. Capacity defaults to 1.")
	ResponseEntity<PositionResponse> create(@PathVariable UUID id, @Valid @RequestBody CreatePositionRequest request) {
		Position created = positions.create(id, request.title(), Boolean.TRUE.equals(request.isHead()),
				request.capacity(),
				request.defaultRoleIds());
		return ResponseEntity.status(HttpStatus.CREATED).location(URI.create("/api/v1/positions/" + created.getId()))
				.body(withHolders(created));
	}

	@PutMapping("/positions/{id}")
	@PreAuthorize("hasAuthority('positions.manage')")
	@Operation(summary = "Update title, capacity and bound roles of a position",
			description = "A role change is applied to the current holders (added roles granted, removed roles "
					+ "revoked). Capacity cannot drop below the number of holders (409). isHead is fixed.")
	PositionResponse update(@PathVariable UUID id, @Valid @RequestBody UpdatePositionRequest request) {
		return withHolders(positions.update(id, request.title(), request.capacity(), request.defaultRoleIds()));
	}

	@PostMapping("/positions/{id}/archive")
	@PreAuthorize("hasAuthority('positions.manage')")
	@Operation(summary = "Archive a position (soft)", description = "409 while it has active appointments.")
	PositionResponse archive(@PathVariable UUID id) {
		return withHolders(positions.archive(id));
	}

	@PostMapping("/positions/{id}/restore")
	@PreAuthorize("hasAuthority('positions.manage')")
	@Operation(summary = "Restore an archived position")
	PositionResponse restore(@PathVariable UUID id) {
		return withHolders(positions.restore(id));
	}

	@GetMapping("/positions/{id}/appointments")
	@Operation(summary = "History of a position: who held it and when (paginated, sortable by startDate, endDate; "
			+ "default startDate desc)", description = "Ended appointments are included unless includeEnded=false.")
	PageResponse<AppointmentResponse> history(@PathVariable UUID id, @Valid PageParams page,
			@RequestParam(defaultValue = "true") boolean includeEnded) {
		return PageResponse.of(
				appointments.listOfPosition(id, includeEnded, page.toPageable(APPOINTMENT_SORTABLE, APPOINTMENT_SORT)),
				PositionController::toResponse);
	}

	@GetMapping("/appointments")
	@Operation(summary = "Appointments of a person (paginated; active only unless includeEnded=true)")
	PageResponse<AppointmentResponse> ofUser(@RequestParam UUID userId, @Valid PageParams page,
			@RequestParam(defaultValue = "false") boolean includeEnded) {
		return PageResponse.of(
				appointments.listOfUser(userId, includeEnded, page.toPageable(APPOINTMENT_SORTABLE, APPOINTMENT_SORT)),
				PositionController::toResponse);
	}

	@GetMapping("/appointments/{id}")
	@Operation(summary = "Get one appointment")
	AppointmentResponse getAppointment(@PathVariable UUID id) {
		return toResponse(appointments.get(id));
	}

	@PostMapping("/positions/{id}/appointments")
	@PreAuthorize("hasAuthority('positions.appoint')")
	@Operation(summary = "Appoint a person to a position",
			description = "Grants the position's bound roles. 409 when the position is full, archived or the "
					+ "person already holds it; the unit head follows a head position's holder.")
	ResponseEntity<AppointmentResponse> appoint(@PathVariable UUID id, @Valid @RequestBody AppointRequest request) {
		Appointment created = appointments.appoint(id, request.userId(), request.startDate());
		return ResponseEntity.status(HttpStatus.CREATED)
				.location(URI.create("/api/v1/appointments/" + created.getId())).body(toResponse(created));
	}

	@PostMapping("/appointments/{id}/end")
	@PreAuthorize("hasAuthority('positions.appoint')")
	@Operation(summary = "End an appointment",
			description = "Sets endDate (default today) and revokes the roles this appointment granted; a role the "
					+ "person has from another source stays. The record is kept as history.")
	AppointmentResponse end(@PathVariable UUID id, @RequestBody(required = false) EndAppointmentRequest request) {
		return toResponse(appointments.end(id, request == null ? null : request.endDate()));
	}

	private PositionResponse withHolders(Position position) {
		return toResponse(position, positions.activeHolders(List.of(position.getId()))
				.getOrDefault(position.getId(), List.of()));
	}

	private static PositionResponse toResponse(Position p, List<UUID> holders) {
		return new PositionResponse(p.getId(), p.getTitle(), p.getOrgUnitId(), p.isHead(), p.getCapacity(),
				p.getDefaultRoleIds().stream().sorted().toList(), holders, holders.isEmpty(), p.isArchived(),
				p.getArchivedAt(), p.getCreatedAt(), p.getUpdatedAt());
	}

	private static AppointmentResponse toResponse(Appointment a) {
		return new AppointmentResponse(a.getId(), a.getPositionId(), a.getUserId(), a.getStartDate(), a.getEndDate(),
				a.isActive(), a.getCreatedAt(), a.getUpdatedAt());
	}

}
