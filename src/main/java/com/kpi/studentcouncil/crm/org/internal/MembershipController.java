package com.kpi.studentcouncil.crm.org.internal;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.kpi.studentcouncil.crm.org.internal.OrgDtos.EndMembershipRequest;
import com.kpi.studentcouncil.crm.org.internal.OrgDtos.MembershipResponse;
import com.kpi.studentcouncil.crm.org.internal.OrgDtos.MoveMembershipRequest;
import com.kpi.studentcouncil.crm.org.internal.OrgDtos.StartMembershipRequest;
import com.kpi.studentcouncil.crm.shared.pagination.PageParams;
import com.kpi.studentcouncil.crm.shared.pagination.PageResponse;
import com.kpi.studentcouncil.crm.shared.web.ApiV1;

/**
 * Membership API. Reads need authentication only (openness); every mutation needs {@code org.manage}.
 * Dates are calendar days (Europe/Kyiv, ISO yyyy-MM-dd).
 */
@ApiV1
@Tag(name = "Org memberships")
class MembershipController {

	private static final Set<String> SORTABLE = Set.of("since", "until");
	private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "since").and(Sort.by("id"));

	private final MembershipService service;

	MembershipController(MembershipService service) {
		this.service = service;
	}

	@GetMapping("/org-units/{id}/memberships")
	@Operation(summary = "Memberships of a unit (paginated, sortable by since, until; default since desc)",
			description = "Active memberships only unless includeEnded=true (history).")
	PageResponse<MembershipResponse> ofUnit(@PathVariable UUID id, @Valid PageParams page,
			@RequestParam(defaultValue = "false") boolean includeEnded) {
		return PageResponse.of(service.listOfUnit(id, includeEnded, page.toPageable(SORTABLE, DEFAULT_SORT)),
				MembershipController::toResponse);
	}

	@GetMapping("/org-memberships")
	@Operation(summary = "Memberships of a person (paginated; a person may be in several units)",
			description = "Active memberships only unless includeEnded=true (history).")
	PageResponse<MembershipResponse> ofUser(@RequestParam UUID userId, @Valid PageParams page,
			@RequestParam(defaultValue = "false") boolean includeEnded) {
		return PageResponse.of(service.listOfUser(userId, includeEnded, page.toPageable(SORTABLE, DEFAULT_SORT)),
				MembershipController::toResponse);
	}

	@GetMapping("/org-memberships/{id}")
	@Operation(summary = "Get one membership")
	MembershipResponse get(@PathVariable UUID id) {
		return toResponse(service.get(id));
	}

	@PostMapping("/org-units/{id}/memberships")
	@PreAuthorize("hasAuthority('org.manage')")
	@Operation(summary = "Add a person to a unit",
			description = "409 when the person already has an active membership in the unit or the unit is archived.")
	ResponseEntity<MembershipResponse> start(@PathVariable UUID id,
			@Valid @RequestBody StartMembershipRequest request) {
		Membership created = service.start(id, request.userId(), request.since());
		return ResponseEntity.status(HttpStatus.CREATED)
				.location(URI.create("/api/v1/org-memberships/" + created.getId())).body(toResponse(created));
	}

	@PostMapping("/org-memberships/{id}/end")
	@PreAuthorize("hasAuthority('org.manage')")
	@Operation(summary = "End a membership", description = "Sets until (default today) and keeps the record.")
	MembershipResponse end(@PathVariable UUID id, @RequestBody(required = false) EndMembershipRequest request) {
		return toResponse(service.end(id, request == null ? null : request.until()));
	}

	@PostMapping("/org-memberships/{id}/move")
	@PreAuthorize("hasAuthority('org.manage')")
	@Operation(summary = "Move a person to another unit",
			description = "Ends this membership and starts a new one in the target unit on the same date; "
					+ "returns the new membership.")
	MembershipResponse move(@PathVariable UUID id, @Valid @RequestBody MoveMembershipRequest request) {
		return toResponse(service.move(id, request.targetOrgUnitId(), request.date()));
	}

	private static MembershipResponse toResponse(Membership m) {
		return new MembershipResponse(m.getId(), m.getUserId(), m.getOrgUnitId(), m.getSince(), m.getUntil(),
				m.isActive(), m.getCreatedAt(), m.getUpdatedAt());
	}

}
