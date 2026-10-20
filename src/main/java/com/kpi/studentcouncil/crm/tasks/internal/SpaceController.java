package com.kpi.studentcouncil.crm.tasks.internal;

import java.net.URI;
import java.util.List;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.kpi.studentcouncil.crm.shared.pagination.PageParams;
import com.kpi.studentcouncil.crm.shared.pagination.PageResponse;
import com.kpi.studentcouncil.crm.shared.web.ApiV1;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.ArchiveStatusRequest;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.CreateCustomFieldRequest;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.CreateSpaceRequest;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.CustomFieldResponse;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.ReorderStatusesRequest;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.SpaceResponse;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.SpaceStatusResponse;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.SpaceSummaryResponse;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.StatusRequest;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.UpdateCustomFieldRequest;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.UpdateSpaceRequest;

/** Spaces, their statuses and custom fields. Reads need {@code tasks.view}, changes {@code spaces.manage}. */
@ApiV1
@Tag(name = "Task spaces")
class SpaceController {

	private static final Set<String> SORTABLE = Set.of("name", "createdAt");
	private static final Sort DEFAULT_SORT = Sort.by("name").and(Sort.by("id"));

	private final SpaceService service;

	SpaceController(SpaceService service) {
		this.service = service;
	}

	@GetMapping("/spaces")
	@PreAuthorize("hasAuthority('tasks.view')")
	@Operation(summary = "Spaces (paginated, sortable by name, createdAt; default name)",
			description = "Archived spaces only with includeArchived=true. Optional filter by orgUnitId.")
	PageResponse<SpaceSummaryResponse> list(@Valid PageParams page,
			@RequestParam(defaultValue = "false") boolean includeArchived, @RequestParam(required = false) UUID orgUnitId) {
		return PageResponse.of(service.list(includeArchived, orgUnitId, page.toPageable(SORTABLE, DEFAULT_SORT)),
				SpaceController::summary);
	}

	@GetMapping("/spaces/{id}")
	@PreAuthorize("hasAuthority('tasks.view')")
	@Operation(summary = "Get a space with its ordered statuses and custom fields")
	SpaceResponse get(@PathVariable UUID id) {
		return detail(service.get(id));
	}

	@PostMapping("/spaces")
	@PreAuthorize("hasAuthority('spaces.manage')")
	@Operation(summary = "Create a space",
			description = "The new space gets the default statuses (To do, In progress, Done, Canceled). A general "
					+ "space has no orgUnitId (422); the unit must exist and not be archived.")
	ResponseEntity<SpaceResponse> create(@Valid @RequestBody CreateSpaceRequest request) {
		Space created = service.create(request.name(), request.orgUnitId(), Boolean.TRUE.equals(request.general()));
		return ResponseEntity.status(HttpStatus.CREATED).location(URI.create("/api/v1/spaces/" + created.getId()))
				.body(detail(created));
	}

	@PutMapping("/spaces/{id}")
	@PreAuthorize("hasAuthority('spaces.manage')")
	@Operation(summary = "Update name, unit binding and general flag of a space", description = "409 when archived.")
	SpaceResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateSpaceRequest request) {
		return detail(service.update(id, request.name(), request.orgUnitId(), Boolean.TRUE.equals(request.general())));
	}

	@PostMapping("/spaces/{id}/archive")
	@PreAuthorize("hasAuthority('spaces.manage')")
	@Operation(summary = "Archive a space (soft)")
	SpaceResponse archive(@PathVariable UUID id) {
		return detail(service.archive(id));
	}

	@PostMapping("/spaces/{id}/restore")
	@PreAuthorize("hasAuthority('spaces.manage')")
	@Operation(summary = "Restore an archived space")
	SpaceResponse restore(@PathVariable UUID id) {
		return detail(service.restore(id));
	}

	// ---- statuses ----

	@GetMapping("/spaces/{id}/statuses")
	@PreAuthorize("hasAuthority('tasks.view')")
	@Operation(summary = "Active statuses of a space, in order")
	List<SpaceStatusResponse> statuses(@PathVariable UUID id) {
		return service.statusesOf(id).stream().map(SpaceController::toResponse).toList();
	}

	@PostMapping("/spaces/{id}/statuses")
	@PreAuthorize("hasAuthority('spaces.manage')")
	@Operation(summary = "Add a status at the end of the order",
			description = "Names are unique within the space, ignoring case (409).")
	ResponseEntity<SpaceStatusResponse> addStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
		SpaceStatus created = service.addStatus(id, request.name(), request.category());
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
	}

	@PutMapping("/spaces/{id}/statuses/{statusId}")
	@PreAuthorize("hasAuthority('spaces.manage')")
	@Operation(summary = "Rename a status and/or change its category",
			description = "The space must keep at least one TODO and one DONE status (422).")
	SpaceStatusResponse updateStatus(@PathVariable UUID id, @PathVariable UUID statusId,
			@Valid @RequestBody StatusRequest request) {
		return toResponse(service.updateStatus(id, statusId, request.name(), request.category()));
	}

	@PutMapping("/spaces/{id}/statuses/order")
	@PreAuthorize("hasAuthority('spaces.manage')")
	@Operation(summary = "Reorder the statuses",
			description = "statusIds must list every active status of the space exactly once (422 otherwise).")
	List<SpaceStatusResponse> reorderStatuses(@PathVariable UUID id,
			@Valid @RequestBody ReorderStatusesRequest request) {
		return service.reorderStatuses(id, request.statusIds()).stream().map(SpaceController::toResponse).toList();
	}

	@PostMapping("/spaces/{id}/statuses/{statusId}/archive")
	@PreAuthorize("hasAuthority('spaces.manage')")
	@Operation(summary = "Archive a status",
			description = "While tasks use the status, replacementStatusId (another active status of the space) is "
					+ "required (409 otherwise) and the tasks are moved to it. The space must keep a TODO and a "
					+ "DONE status (422).")
	SpaceStatusResponse archiveStatus(@PathVariable UUID id, @PathVariable UUID statusId,
			@RequestBody(required = false) ArchiveStatusRequest request) {
		return toResponse(service.archiveStatus(id, statusId, request == null ? null : request.replacementStatusId()));
	}

	// ---- custom fields ----

	@GetMapping("/spaces/{id}/custom-fields")
	@PreAuthorize("hasAuthority('tasks.view')")
	@Operation(summary = "Active custom field definitions of a space")
	List<CustomFieldResponse> fields(@PathVariable UUID id) {
		return service.fieldsOf(id).stream().map(SpaceController::toResponse).toList();
	}

	@PostMapping("/spaces/{id}/custom-fields")
	@PreAuthorize("hasAuthority('spaces.manage')")
	@Operation(summary = "Define a custom field",
			description = "Types: TEXT, NUMBER, DATE, SELECT, PERSON. SELECT requires options (422); other types "
					+ "must not have any. Names are unique within the space (409).")
	ResponseEntity<CustomFieldResponse> addField(@PathVariable UUID id,
			@Valid @RequestBody CreateCustomFieldRequest request) {
		CustomFieldDefinition created = service.addField(id, request.name(), request.type(),
				Boolean.TRUE.equals(request.required()), request.options());
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
	}

	@PutMapping("/spaces/{id}/custom-fields/{fieldId}")
	@PreAuthorize("hasAuthority('spaces.manage')")
	@Operation(summary = "Update a custom field (the type is fixed)")
	CustomFieldResponse updateField(@PathVariable UUID id, @PathVariable UUID fieldId,
			@Valid @RequestBody UpdateCustomFieldRequest request) {
		return toResponse(service.updateField(id, fieldId, request.name(), Boolean.TRUE.equals(request.required()),
				request.options()));
	}

	@PostMapping("/spaces/{id}/custom-fields/{fieldId}/archive")
	@PreAuthorize("hasAuthority('spaces.manage')")
	@Operation(summary = "Archive a custom field (soft)")
	CustomFieldResponse archiveField(@PathVariable UUID id, @PathVariable UUID fieldId) {
		return toResponse(service.archiveField(id, fieldId));
	}

	private SpaceResponse detail(Space s) {
		return new SpaceResponse(s.getId(), s.getName(), s.getOrgUnitId(), s.isGeneral(), s.isArchived(),
				s.getArchivedAt(), s.getCreatedAt(), s.getUpdatedAt(),
				service.statusesOf(s.getId()).stream().map(SpaceController::toResponse).toList(),
				service.fieldsOf(s.getId()).stream().map(SpaceController::toResponse).toList());
	}

	private static SpaceSummaryResponse summary(Space s) {
		return new SpaceSummaryResponse(s.getId(), s.getName(), s.getOrgUnitId(), s.isGeneral(), s.isArchived(),
				s.getArchivedAt(), s.getCreatedAt(), s.getUpdatedAt());
	}

	private static SpaceStatusResponse toResponse(SpaceStatus s) {
		return new SpaceStatusResponse(s.getId(), s.getSpaceId(), s.getName(), s.getCategory(), s.getPosition(),
				s.isArchived(), s.getCreatedAt(), s.getUpdatedAt());
	}

	private static CustomFieldResponse toResponse(CustomFieldDefinition f) {
		return new CustomFieldResponse(f.getId(), f.getSpaceId(), f.getName(), f.getType(), f.isRequired(),
				f.getOptions(), f.isArchived(), f.getCreatedAt(), f.getUpdatedAt());
	}

}
