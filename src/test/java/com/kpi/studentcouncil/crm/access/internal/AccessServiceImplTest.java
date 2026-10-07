package com.kpi.studentcouncil.crm.access.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.kpi.studentcouncil.crm.access.AccessService;
import com.kpi.studentcouncil.crm.access.Permission;
import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;

class AccessServiceImplTest {

	RoleRepository roles = mock(RoleRepository.class);

	RoleAssignmentRepository assignments = mock(RoleAssignmentRepository.class);

	PermissionCache cache = new PermissionCache();

	AuditPublisher audit = mock(AuditPublisher.class);

	AccessServiceImpl service;

	RoleService roleService;

	UUID user = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		ActorProvider actors = () -> ActorProvider.SYSTEM_ACTOR;
		service = new AccessServiceImpl(roles, assignments, cache, audit, actors, Clock.systemUTC(), id -> true);
		roleService = new RoleService(roles, cache, audit, actors, Clock.systemUTC());
	}

	private Role role(String name, Permission... permissions) {
		return Role.create(name, null, Set.of(Arrays.stream(permissions).map(Permission::key).toArray(String[]::new)));
	}

	@Test
	void userWithTwoRolesHasTheUnion() {
		when(assignments.findActiveRoleIds(user)).thenReturn(List.of(UUID.randomUUID(), UUID.randomUUID()));
		when(roles.findByIdInAndArchivedAtIsNull(any())).thenReturn(List.of(
				role("A", Permission.TASKS_VIEW, Permission.TASKS_CREATE),
				role("B", Permission.TASKS_CREATE, Permission.KB_VIEW)));

		assertThat(service.effectivePermissions(user)).containsExactlyInAnyOrder("tasks.view", "tasks.create", "kb.view");
		assertThat(service.hasPermission(user, Permission.KB_VIEW)).isTrue();
		assertThat(service.hasPermission(user, Permission.AUDIT_VIEW)).isFalse();
	}

	@Test
	void userWithoutRolesHasNoPermissions() {
		when(assignments.findActiveRoleIds(user)).thenReturn(List.of());
		when(roles.findByIdInAndArchivedAtIsNull(any())).thenReturn(List.of());

		assertThat(service.effectivePermissions(user)).isEmpty();
	}

	@Test
	void adminHasEveryPermissionWithoutAnyRoleRow() {
		when(assignments.findActiveRoleIds(user)).thenReturn(List.of(AccessService.ADMIN_ROLE_ID));
		when(roles.findByIdInAndArchivedAtIsNull(any())).thenReturn(List.of());

		assertThat(service.effectivePermissions(user)).isEqualTo(Permission.allKeys());
	}

	@Test
	void unknownStoredKeysAreIgnored() {
		Role stale = Role.create("Old", null, Set.of("tasks.view", "removed.in.code"));
		assertThat(stale.getPermissionKeys()).containsExactly("tasks.view");
	}

	@Test
	void adminRoleCannotBeGrantedRevokedEditedOrArchived() {
		assertThatThrownBy(() -> service.grantRole(user, AccessService.ADMIN_ROLE_ID))
				.isInstanceOf(BusinessRuleException.class).hasMessage("ADMIN_ROLE_RESERVED");
		assertThatThrownBy(() -> service.revokeRole(user, AccessService.ADMIN_ROLE_ID))
				.isInstanceOf(BusinessRuleException.class).hasMessage("ADMIN_ROLE_RESERVED");
		assertThatThrownBy(() -> roleService.update(AccessService.ADMIN_ROLE_ID, "X", null, Set.of()))
				.isInstanceOf(BusinessRuleException.class).hasMessage("SYSTEM_ROLE_IMMUTABLE");
		assertThatThrownBy(() -> roleService.archive(AccessService.ADMIN_ROLE_ID))
				.isInstanceOf(BusinessRuleException.class).hasMessage("SYSTEM_ROLE_IMMUTABLE");
		verifyNoInteractions(audit);
	}

	@Test
	void reservedNameAndUnknownPermissionAreRejected() {
		assertThatThrownBy(() -> roleService.create(" admin ", null, Set.of())).hasMessage("ROLE_NAME_RESERVED");
		assertThatThrownBy(() -> roleService.create("Ok", null, Set.of("bogus.key"))).hasMessage("UNKNOWN_PERMISSION");
	}

	@Test
	void cacheServesRepeatedReadsAndFlushesOnInvalidate() {
		when(assignments.findActiveRoleIds(user)).thenReturn(List.of());
		when(roles.findByIdInAndArchivedAtIsNull(any())).thenReturn(List.of());
		service.effectivePermissions(user);
		service.effectivePermissions(user);
		verify(assignments, times(1)).findActiveRoleIds(user);
		cache.invalidateAll();
		service.effectivePermissions(user);
		verify(assignments, times(2)).findActiveRoleIds(user);
	}

}
