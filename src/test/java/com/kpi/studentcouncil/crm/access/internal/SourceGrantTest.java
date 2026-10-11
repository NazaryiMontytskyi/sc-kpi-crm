package com.kpi.studentcouncil.crm.access.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.kpi.studentcouncil.crm.access.AccessService;
import com.kpi.studentcouncil.crm.access.RoleGrantSource;
import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.shared.error.ForbiddenException;

/** Source-aware grants (ADR-0006): a role granted for a record is revoked only through that record. */
class SourceGrantTest {

	private final RoleRepository roles = mock(RoleRepository.class);
	private final RoleAssignmentRepository assignments = mock(RoleAssignmentRepository.class);
	private final PermissionCache cache = new PermissionCache();
	private final AuditPublisher audit = mock(AuditPublisher.class);
	private final ActorProvider actors = mock(ActorProvider.class);

	private final UUID user = UUID.randomUUID();
	private final Role role = Role.create("Editor", null, Set.of("tasks.view"));
	private final UUID roleId = UUID.randomUUID();
	private final RoleGrantSource source = new RoleGrantSource("APPOINTMENT", UUID.randomUUID());
	private AccessServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new AccessServiceImpl(roles, assignments, cache, audit, actors, Clock.systemUTC(), id -> true);
		when(roles.findByIdAndArchivedAtIsNull(roleId)).thenReturn(Optional.of(role));
		when(assignments.save(any(RoleAssignment.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	@Test
	void sourceGrantCreatesASeparateRowEvenWhenAManualGrantExists() {
		when(assignments.findByUserIdAndRoleIdAndScopeOrgUnitIdIsNullAndArchivedAtIsNull(user, roleId))
				.thenReturn(Optional.of(RoleAssignment.create(user, roleId)));

		service.grantRole(user, roleId, source);

		verify(assignments).save(any(RoleAssignment.class));
	}

	@Test
	void sourceGrantIsIdempotentPerSource() {
		when(assignments.findByUserIdAndRoleIdAndScopeOrgUnitIdIsNullAndSourceTypeAndSourceIdAndArchivedAtIsNull(user,
				roleId, source.type(), source.id()))
				.thenReturn(Optional.of(RoleAssignment.create(user, roleId, source)));

		service.grantRole(user, roleId, source);

		verify(assignments, never()).save(any(RoleAssignment.class));
	}

	@Test
	void revokeBySourceArchivesOnlyThatRowAndNeverTouchesTheManualOne() {
		RoleAssignment fromSource = RoleAssignment.create(user, roleId, source);
		when(assignments.findByUserIdAndRoleIdAndScopeOrgUnitIdIsNullAndSourceTypeAndSourceIdAndArchivedAtIsNull(user,
				roleId, source.type(), source.id())).thenReturn(Optional.of(fromSource));
		when(roles.findById(roleId)).thenReturn(Optional.of(role));

		service.revokeRole(user, roleId, source);

		assertThat(fromSource.isArchived()).isTrue();
		verify(assignments, never()).findByUserIdAndRoleIdAndScopeOrgUnitIdIsNullAndArchivedAtIsNull(any(), any());
	}

	@Test
	void manualRevokeDoesNotRemoveAGrantMadeForARecord() {
		// the manual lookup only matches rows without a source; here there is none, so it is a no-op
		when(assignments.findByUserIdAndRoleIdAndScopeOrgUnitIdIsNullAndArchivedAtIsNull(user, roleId))
				.thenReturn(Optional.empty());

		service.revokeRole(user, roleId);

		verify(audit, never()).publish(any());
	}

	@Test
	void revokeAllGrantedByArchivesEveryRowOfTheSource() {
		RoleAssignment a = RoleAssignment.create(user, roleId, source);
		RoleAssignment b = RoleAssignment.create(user, UUID.randomUUID(), source);
		when(assignments.findBySourceTypeAndSourceIdAndArchivedAtIsNull(source.type(), source.id()))
				.thenReturn(List.of(a, b));

		assertThat(service.revokeAllGrantedBy(source)).isEqualTo(2);
		assertThat(a.isArchived()).isTrue();
		assertThat(b.isArchived()).isTrue();
	}

	@Test
	void adminRoleCannotBeGrantedOrRevokedBySource() {
		assertThatThrownBy(() -> service.grantRole(user, AccessService.ADMIN_ROLE_ID, source))
				.isInstanceOf(ForbiddenException.class).hasMessage("ADMIN_ROLE_RESERVED");
		assertThatThrownBy(() -> service.revokeRole(user, AccessService.ADMIN_ROLE_ID, source))
				.isInstanceOf(ForbiddenException.class).hasMessage("ADMIN_ROLE_RESERVED");
		assertThat(service.isAssignableRole(AccessService.ADMIN_ROLE_ID)).isFalse();
	}

	@Test
	void assignableRoleIsAnExistingNonArchivedNonAdminRole() {
		assertThat(service.isAssignableRole(roleId)).isTrue();
		assertThat(service.isAssignableRole(UUID.randomUUID())).isFalse();
	}

}
