package com.kpi.studentcouncil.crm.access.internal;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.MessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.kpi.studentcouncil.crm.access.AccessService;
import com.kpi.studentcouncil.crm.access.Permission;
import com.kpi.studentcouncil.crm.access.PermissionGroup;
import com.kpi.studentcouncil.crm.identity.UserDirectory;
import com.kpi.studentcouncil.crm.shared.error.NotFoundException;
import com.kpi.studentcouncil.crm.shared.web.ApiV1;

/** Read-only permission endpoints: the fixed catalog and a user's effective permissions. */
@ApiV1
@RequestMapping
@Tag(name = "Permissions", description = "Permission catalog and effective permissions of users")
class PermissionController {

	private final MessageSource messages;

	private final AccessService access;

	private final UserDirectory users;

	PermissionController(MessageSource messages, AccessService access, UserDirectory users) {
		this.messages = messages;
		this.access = access;
		this.users = users;
	}

	record PermissionDto(String key, String label) {
	}

	record PermissionGroupDto(String code, String label, List<PermissionDto> permissions) {
	}

	record UserPermissionsDto(UUID userId, List<String> permissions) {
	}

	@GetMapping("/permissions")
	@Operation(summary = "Permission catalog grouped as in CONTEXT.md 5.2, labels in the request locale")
	List<PermissionGroupDto> catalog(Locale locale) {
		return Arrays.stream(PermissionGroup.values()).map(group -> new PermissionGroupDto(group.code(),
				messages.getMessage("permission.group." + group.code(), null, locale),
				Arrays.stream(Permission.values()).filter(p -> p.group() == group)
						.map(p -> new PermissionDto(p.key(),
								messages.getMessage("permission." + p.key() + ".label", null, locale)))
						.toList()))
				.toList();
	}

	@GetMapping("/users/{id}/permissions")
	@PreAuthorize("hasAuthority('roles.assign') or @permissionGuard.isSelf(#id)")
	@Operation(summary = "Effective permissions of a user (self or roles.assign)")
	UserPermissionsDto userPermissions(@PathVariable UUID id) {
		if (!users.exists(id)) {
			throw new NotFoundException("User", id);
		}
		return new UserPermissionsDto(id, access.effectivePermissions(id).stream().sorted().toList());
	}

}
