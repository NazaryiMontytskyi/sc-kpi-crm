package com.kpi.studentcouncil.crm.tasks.internal;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.kpi.studentcouncil.crm.shared.pagination.PageParams;
import com.kpi.studentcouncil.crm.shared.pagination.PageResponse;
import com.kpi.studentcouncil.crm.shared.web.ApiV1;
import com.kpi.studentcouncil.crm.tasks.internal.SavedFilter.Criteria;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.SaveFilterRequest;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.SavedFilterCriteria;
import com.kpi.studentcouncil.crm.tasks.internal.TasksDtos.SavedFilterResponse;

/**
 * Saved task filters. Open to every authenticated user, but strictly private: all operations act on the caller's own
 * filters only, anybody else's filter answers 404.
 */
@ApiV1
@Tag(name = "Saved task filters")
class SavedFilterController {

	private static final Set<String> SORTABLE = Set.of("name", "createdAt");
	private static final Sort DEFAULT_SORT = Sort.by("name").and(Sort.by("id"));

	private final SavedFilterService service;

	SavedFilterController(SavedFilterService service) {
		this.service = service;
	}

	@GetMapping("/task-filters")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "My saved task filters (paginated, sortable by name, createdAt; default name)")
	PageResponse<SavedFilterResponse> list(@ParameterObject @Valid PageParams page) {
		return PageResponse.of(service.list(page.toPageable(SORTABLE, DEFAULT_SORT)), SavedFilterController::response);
	}

	@GetMapping("/task-filters/{id}")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "Get one of my saved task filters", description = "404 for a filter of another user.")
	SavedFilterResponse get(@PathVariable UUID id) {
		return response(service.get(id));
	}

	@PostMapping("/task-filters")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "Save a task filter for myself",
			description = "The name must be unique among my filters (409 SAVED_FILTER_NAME_TAKEN); at most 50 filters "
					+ "(422 SAVED_FILTER_LIMIT).")
	ResponseEntity<SavedFilterResponse> create(@Valid @RequestBody SaveFilterRequest request) {
		SavedFilter created = service.create(request.name(), criteria(request.criteria()));
		return ResponseEntity.status(HttpStatus.CREATED).location(URI.create("/api/v1/task-filters/" + created.getId()))
				.body(response(created));
	}

	@PutMapping("/task-filters/{id}")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "Replace one of my saved task filters", description = "404 for a filter of another user.")
	SavedFilterResponse update(@PathVariable UUID id, @Valid @RequestBody SaveFilterRequest request) {
		return response(service.update(id, request.name(), criteria(request.criteria())));
	}

	@DeleteMapping("/task-filters/{id}")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "Remove one of my saved task filters (archives it)", description = "404 for a filter of another user.")
	ResponseEntity<Void> remove(@PathVariable UUID id) {
		service.remove(id);
		return ResponseEntity.noContent().build();
	}

	private static Criteria criteria(SavedFilterCriteria c) {
		return new Criteria(c.spaceId(), c.statusId(), c.labels() == null ? Set.of() : new java.util.LinkedHashSet<>(c.labels()),
				c.executorId(), c.authorId(), c.resolutionState(), c.view(), c.dueFrom(), c.dueTo());
	}

	private static SavedFilterResponse response(SavedFilter f) {
		Criteria c = f.criteria();
		return new SavedFilterResponse(f.getId(), f.getName(),
				new SavedFilterCriteria(c.spaceId(), c.statusId(), c.labels().stream().sorted().toList(), c.executorId(),
						c.authorId(), c.resolutionState(), c.view(), c.dueFrom(), c.dueTo()),
				f.getCreatedAt(), f.getUpdatedAt());
	}

}
