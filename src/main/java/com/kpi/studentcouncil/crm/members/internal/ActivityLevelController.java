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

import com.kpi.studentcouncil.crm.members.internal.ActivityLevelDtos.DefinitionRequest;
import com.kpi.studentcouncil.crm.members.internal.ActivityLevelDtos.DefinitionResponse;
import com.kpi.studentcouncil.crm.shared.pagination.PageParams;
import com.kpi.studentcouncil.crm.shared.pagination.PageResponse;
import com.kpi.studentcouncil.crm.shared.web.ApiV1;

/** Activity level scale API. Reads need authentication only; mutations need {@code settings.manage}. */
@ApiV1
@RequestMapping("/activity-levels")
@Tag(name = "Activity levels")
class ActivityLevelController {

	private static final String MANAGE = "settings.manage";
	private static final Set<String> SORTABLE = Set.of("sortOrder", "code", "labelUk", "labelEn");

	private final ActivityLevelService service;

	ActivityLevelController(ActivityLevelService service) {
		this.service = service;
	}

	@GetMapping
	@Operation(summary = "List activity level definitions (paginated, default order by sortOrder)",
			description = "Active definitions only. includeArchived=true is honoured only for holders of "
					+ "settings.manage. The scale is data (OPEN-Q3): clients must not hardcode codes.")
	PageResponse<DefinitionResponse> list(@Valid PageParams page,
			@RequestParam(defaultValue = "false") boolean includeArchived, Authentication authentication) {
		boolean archivedAllowed = includeArchived && canManage(authentication);
		Sort sort = Sort.by(Sort.Direction.ASC, "sortOrder").and(Sort.by(Sort.Direction.ASC, "id"));
		return PageResponse.of(service.listDefinitions(archivedAllowed, page.toPageable(SORTABLE, sort)),
				ActivityLevelController::toResponse);
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get one definition, including an archived one")
	DefinitionResponse get(@PathVariable UUID id) {
		return toResponse(service.getDefinition(id));
	}

	@PostMapping
	@PreAuthorize("hasAuthority('settings.manage')")
	@Operation(summary = "Create a definition", description = "Code is unique among active definitions (409 otherwise).")
	ResponseEntity<DefinitionResponse> create(@Valid @RequestBody DefinitionRequest request) {
		ActivityLevelDefinition created = service.create(request.code(), request.labelUk(), request.labelEn(),
				request.sortOrder());
		return ResponseEntity.status(HttpStatus.CREATED).location(URI.create("/api/v1/activity-levels/" + created.getId()))
				.body(toResponse(created));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('settings.manage')")
	@Operation(summary = "Edit an active definition")
	DefinitionResponse update(@PathVariable UUID id, @Valid @RequestBody DefinitionRequest request) {
		return toResponse(service.update(id, request.code(), request.labelUk(), request.labelEn(), request.sortOrder()));
	}

	@PostMapping("/{id}/archive")
	@PreAuthorize("hasAuthority('settings.manage')")
	@Operation(summary = "Archive a definition (soft)",
			description = "Members keep the level and the history keeps the reference; it just stops being selectable.")
	DefinitionResponse archive(@PathVariable UUID id) {
		return toResponse(service.archive(id));
	}

	@PostMapping("/{id}/restore")
	@PreAuthorize("hasAuthority('settings.manage')")
	@Operation(summary = "Restore an archived definition", description = "409 if its code is now used by an active one.")
	DefinitionResponse restore(@PathVariable UUID id) {
		return toResponse(service.restore(id));
	}

	private static boolean canManage(Authentication authentication) {
		return authentication != null
				&& authentication.getAuthorities().stream().anyMatch(a -> MANAGE.equals(a.getAuthority()));
	}

	static DefinitionResponse toResponse(ActivityLevelDefinition d) {
		return new DefinitionResponse(d.getId(), d.getCode(), d.getLabelUk(), d.getLabelEn(), d.getSortOrder(),
				d.isArchived(), d.getArchivedAt());
	}

}
