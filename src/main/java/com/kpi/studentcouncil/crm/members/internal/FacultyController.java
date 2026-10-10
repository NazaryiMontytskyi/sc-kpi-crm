package com.kpi.studentcouncil.crm.members.internal;

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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.kpi.studentcouncil.crm.members.internal.FacultyDtos.FacultyRequest;
import com.kpi.studentcouncil.crm.members.internal.FacultyDtos.FacultyResponse;
import com.kpi.studentcouncil.crm.shared.pagination.PageParams;
import com.kpi.studentcouncil.crm.shared.pagination.PageResponse;
import com.kpi.studentcouncil.crm.shared.web.ApiV1;

/**
 * Faculty/institute dictionary API. Reads need authentication only; all mutations need {@code settings.manage}.
 */
@ApiV1
@RequestMapping("/faculties")
@Tag(name = "Faculties")
class FacultyController {

	private static final String MANAGE = "settings.manage";
	private static final Set<String> SORTABLE = Set.of("code", "nameUk", "nameEn");

	private final FacultyService service;

	FacultyController(FacultyService service) {
		this.service = service;
	}

	@GetMapping
	@Operation(summary = "List faculties (paginated, sortable by code, nameUk, nameEn; default nameUk)",
			description = "Active faculties only. includeArchived=true is honoured only for holders of "
					+ "settings.manage; for everyone else it is ignored.")
	PageResponse<FacultyResponse> list(@Valid PageParams page,
			@RequestParam(defaultValue = "false") boolean includeArchived, Authentication authentication) {
		boolean archivedAllowed = includeArchived && canManage(authentication);
		return PageResponse.of(
				service.list(archivedAllowed, page.toPageable(SORTABLE, Sort.by(Sort.Direction.ASC, "nameUk"))),
				FacultyController::toResponse);
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get one faculty, including an archived one (existing profiles may reference it)")
	FacultyResponse get(@PathVariable UUID id) {
		return toResponse(service.get(id));
	}

	@PostMapping
	@PreAuthorize("hasAuthority('settings.manage')")
	@Operation(summary = "Create a faculty",
			description = "Code and both names are unique among active faculties, case-insensitively (409 otherwise).")
	ResponseEntity<FacultyResponse> create(@Valid @RequestBody FacultyRequest request) {
		Faculty created = service.create(request.code(), request.nameUk(), request.nameEn());
		return ResponseEntity.status(HttpStatus.CREATED).location(URI.create("/api/v1/faculties/" + created.getId()))
				.body(toResponse(created));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('settings.manage')")
	@Operation(summary = "Edit an active faculty")
	FacultyResponse update(@PathVariable UUID id, @Valid @RequestBody FacultyRequest request) {
		return toResponse(service.update(id, request.code(), request.nameUk(), request.nameEn()));
	}

	@PostMapping("/{id}/archive")
	@PreAuthorize("hasAuthority('settings.manage')")
	@Operation(summary = "Archive a faculty (soft)",
			description = "Allowed even when profiles use it: they keep the reference, the faculty just stops "
					+ "being selectable.")
	FacultyResponse archive(@PathVariable UUID id) {
		return toResponse(service.archive(id));
	}

	@PostMapping("/{id}/restore")
	@PreAuthorize("hasAuthority('settings.manage')")
	@Operation(summary = "Restore an archived faculty",
			description = "409 if its code or a name is now used by an active faculty.")
	FacultyResponse restore(@PathVariable UUID id) {
		return toResponse(service.restore(id));
	}

	private static boolean canManage(Authentication authentication) {
		return authentication != null
				&& authentication.getAuthorities().stream().anyMatch(a -> MANAGE.equals(a.getAuthority()));
	}

	private static FacultyResponse toResponse(Faculty f) {
		return new FacultyResponse(f.getId(), f.getCode(), f.getNameUk(), f.getNameEn(), f.isArchived(),
				f.getArchivedAt(), f.getCreatedAt(), f.getUpdatedAt());
	}

}
