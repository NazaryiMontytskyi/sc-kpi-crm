package com.kpi.studentcouncil.crm.org.internal;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.kpi.studentcouncil.crm.org.OrgUnitInfo;
import com.kpi.studentcouncil.crm.org.internal.OrgDtos.CreateOrgUnitRequest;
import com.kpi.studentcouncil.crm.org.internal.OrgDtos.MoveOrgUnitRequest;
import com.kpi.studentcouncil.crm.org.internal.OrgDtos.OrgUnitResponse;
import com.kpi.studentcouncil.crm.org.internal.OrgDtos.OrgUnitTreeNode;
import com.kpi.studentcouncil.crm.org.internal.OrgDtos.UpdateOrgUnitRequest;
import com.kpi.studentcouncil.crm.shared.web.ApiV1;

/**
 * Org structure API. Reads need authentication only (openness by default, CONTEXT.md 5.2 "Visibility"); the
 * {@code org.view} permission is kept in the catalog for roles but not enforced on reads. All mutations need
 * {@code org.manage}. The tree is returned whole (a few dozen units), so it is not paginated.
 */
@ApiV1
@RequestMapping("/org-units")
@Tag(name = "Org structure")
class OrgController {

	private final OrgUnitService service;

	OrgController(OrgUnitService service) {
		this.service = service;
	}

	@GetMapping("/tree")
	@Operation(summary = "Org tree",
			description = "Forest of units (roots are units without a parent), siblings sorted by name. "
					+ "Archived units are hidden unless includeArchived=true.")
	List<OrgUnitTreeNode> tree(@RequestParam(defaultValue = "false") boolean includeArchived) {
		return service.tree(includeArchived);
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get one unit, including archived ones, with its ancestor ids and active child ids")
	OrgUnitResponse get(@PathVariable UUID id) {
		return toResponse(service.get(id));
	}

	@PostMapping
	@PreAuthorize("hasAuthority('org.manage')")
	@Operation(summary = "Create a unit", description = "Type/parent rules: LEADERSHIP has no parent and is unique; "
			+ "DEPARTMENT sits under LEADERSHIP or at top level; DIVISION requires a DEPARTMENT parent; "
			+ "WORKING_GROUP is outside the hierarchy (no parent or any active unit).")
	ResponseEntity<OrgUnitResponse> create(@Valid @RequestBody CreateOrgUnitRequest request) {
		OrgUnit created = service.create(request.name(), request.type(), request.parentId(), request.description());
		return ResponseEntity.status(HttpStatus.CREATED).location(URI.create("/api/v1/org-units/" + created.getId()))
				.body(toResponse(created));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('org.manage')")
	@Operation(summary = "Rename a unit / change its description")
	OrgUnitResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateOrgUnitRequest request) {
		return toResponse(service.update(id, request.name(), request.description()));
	}

	@PostMapping("/{id}/move")
	@PreAuthorize("hasAuthority('org.manage')")
	@Operation(summary = "Move a unit under another parent (null = top level)",
			description = "Rejects type-invalid parents and cycles.")
	OrgUnitResponse move(@PathVariable UUID id, @RequestBody MoveOrgUnitRequest request) {
		return toResponse(service.move(id, request.parentId()));
	}

	@PostMapping("/{id}/archive")
	@PreAuthorize("hasAuthority('org.manage')")
	@Operation(summary = "Archive a unit (soft)",
			description = "Refused with 409 while the unit has active children or an archive guard vetoes it. "
					+ "Archiving a working group disbands it.")
	OrgUnitResponse archive(@PathVariable UUID id) {
		return toResponse(service.archive(id));
	}

	@PostMapping("/{id}/restore")
	@PreAuthorize("hasAuthority('org.manage')")
	@Operation(summary = "Restore an archived unit", description = "Requires an active parent.")
	OrgUnitResponse restore(@PathVariable UUID id) {
		return toResponse(service.restore(id));
	}

	private OrgUnitResponse toResponse(OrgUnit unit) {
		OrgUnitInfo info = service.info(unit);
		return new OrgUnitResponse(unit.getId(), unit.getName(), unit.getType(), unit.getParentId(),
				unit.getDescription(), unit.isArchived(), unit.getArchivedAt(), unit.getCreatedAt(),
				unit.getUpdatedAt(), info.ancestorIds(), info.childIds());
	}

}
