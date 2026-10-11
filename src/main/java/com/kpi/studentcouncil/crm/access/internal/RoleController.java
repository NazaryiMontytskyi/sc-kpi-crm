package com.kpi.studentcouncil.crm.access.internal;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.kpi.studentcouncil.crm.access.AccessService;
import com.kpi.studentcouncil.crm.identity.UserDirectory;
import com.kpi.studentcouncil.crm.shared.error.NotFoundException;
import com.kpi.studentcouncil.crm.shared.pagination.PageParams;
import com.kpi.studentcouncil.crm.shared.pagination.PageResponse;
import com.kpi.studentcouncil.crm.shared.web.ApiV1;

/**
 * Role builder API. Reads need authentication only (openness by default); role definitions need
 * {@code roles.manage}, granting and revoking needs {@code roles.assign}. The built-in Admin role is listed (all
 * permissions) but can never be edited, archived, granted or revoked (403).
 */
@ApiV1
@RequestMapping
@Tag(name = "Roles", description = "Role builder and role assignment")
class RoleController {

	private static final String MANAGE = "roles.manage";

	private final RoleService roles;

	private final AccessService access;

	private final UserDirectory users;

	RoleController(RoleService roles, AccessService access, UserDirectory users) {
		this.roles = roles;
		this.access = access;
		this.users = users;
	}

	/** {@code permissions} are catalog keys; unknown keys are rejected (422). */
	record RoleRequest(@NotBlank @Size(max = 100) String name, @Size(max = 500) String description,
			@NotNull Set<@NotBlank String> permissions) {
	}

	record RoleResponse(UUID id, String name, String description, boolean system, List<String> permissions,
			boolean archived, Instant archivedAt, long assignedUserCount, Instant createdAt, Instant updatedAt) {
	}

	@GetMapping("/roles")
	@Operation(summary = "List roles (paginated, by name; the built-in Admin role first)",
			description = "includeArchived=true is honoured only for holders of roles.manage.")
	PageResponse<RoleResponse> list(@Valid PageParams page,
			@RequestParam(defaultValue = "false") boolean includeArchived, Authentication authentication) {
		boolean archivedAllowed = includeArchived && authentication != null
				&& authentication.getAuthorities().stream().anyMatch(a -> MANAGE.equals(a.getAuthority()));
		List<RoleResponse> all = roles.list(archivedAllowed).stream().map(RoleController::toResponse).toList();
		var pageable = page.toPageable(Set.of(), Sort.unsorted());
		int from = (int) Math.min(pageable.getOffset(), all.size());
		int to = Math.min(from + pageable.getPageSize(), all.size());
		return PageResponse.of(new PageImpl<>(all.subList(from, to), pageable, all.size()));
	}

	@GetMapping("/roles/{id}")
	@Operation(summary = "Get one role, including an archived one, or the built-in Admin role")
	RoleResponse get(@PathVariable UUID id) {
		return toResponse(roles.get(id));
	}

	@PostMapping("/roles")
	@PreAuthorize("hasAuthority('roles.manage')")
	@Operation(summary = "Create a role",
			description = "The name is unique among active roles (409); Admin is reserved.")
	ResponseEntity<RoleResponse> create(@Valid @RequestBody RoleRequest request) {
		Role created = roles.create(request.name(), request.description(), request.permissions());
		return ResponseEntity.status(HttpStatus.CREATED).location(URI.create("/api/v1/roles/" + created.getId()))
				.body(toResponse(roles.get(created.getId())));
	}

	@PutMapping("/roles/{id}")
	@PreAuthorize("hasAuthority('roles.manage')")
	@Operation(summary = "Rename a role and replace its permission set",
			description = "Takes effect for all holders immediately. 403 for the Admin role.")
	RoleResponse update(@PathVariable UUID id, @Valid @RequestBody RoleRequest request) {
		roles.update(id, request.name(), request.description(), request.permissions());
		return toResponse(roles.get(id));
	}

	@PostMapping("/roles/{id}/archive")
	@PreAuthorize("hasAuthority('roles.manage')")
	@Operation(summary = "Archive a role (soft)",
			description = "Allowed while assigned: holders lose its permissions. 403 for the Admin role.")
	RoleResponse archive(@PathVariable UUID id) {
		roles.archive(id);
		return toResponse(roles.get(id));
	}

	@GetMapping("/users/{userId}/roles")
	@PreAuthorize("hasAnyAuthority('roles.assign', 'roles.manage') or @permissionGuard.isSelf(#userId)")
	@Operation(summary = "Roles currently assigned to a user (self, roles.assign or roles.manage)")
	List<RoleResponse> userRoles(@PathVariable UUID userId) {
		requireUser(userId);
		return roles.rolesOfUser(userId).stream().map(RoleController::toResponse).toList();
	}

	@PutMapping("/users/{userId}/roles/{roleId}")
	@PreAuthorize("hasAuthority('roles.assign')")
	@Operation(summary = "Assign a role to a user (idempotent)",
			description = "404 for an unknown user or an archived/unknown role; 403 for the Admin role.")
	ResponseEntity<Void> assign(@PathVariable UUID userId, @PathVariable UUID roleId) {
		access.grantRole(userId, roleId);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/users/{userId}/roles/{roleId}")
	@PreAuthorize("hasAuthority('roles.assign')")
	@Operation(summary = "Revoke a role from a user (the assignment is archived; no-op when not assigned)",
			description = "403 for the Admin role: it cannot be removed through the API.")
	ResponseEntity<Void> revoke(@PathVariable UUID userId, @PathVariable UUID roleId) {
		requireUser(userId);
		access.revokeRole(userId, roleId);
		return ResponseEntity.noContent().build();
	}

	private void requireUser(UUID userId) {
		if (!users.exists(userId)) {
			throw new NotFoundException("User", userId);
		}
	}

	private static RoleResponse toResponse(RoleService.RoleView v) {
		return new RoleResponse(v.id(), v.name(), v.description(), v.system(), List.copyOf(v.permissions()),
				v.archived(), v.archivedAt(), v.assignedUserCount(), v.createdAt(), v.updatedAt());
	}

}
