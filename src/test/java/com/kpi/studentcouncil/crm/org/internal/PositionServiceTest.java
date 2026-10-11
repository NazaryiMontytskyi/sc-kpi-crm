package com.kpi.studentcouncil.crm.org.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.kpi.studentcouncil.crm.access.AccessService;
import com.kpi.studentcouncil.crm.access.RoleGrantSource;
import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.org.OrgService;
import com.kpi.studentcouncil.crm.shared.error.DomainException;
import com.kpi.studentcouncil.crm.shared.error.ForbiddenException;

class PositionServiceTest {

	private final PositionRepository positions = mock(PositionRepository.class);
	private final AppointmentRepository appointments = mock(AppointmentRepository.class);
	private final OrgService orgUnits = mock(OrgService.class);
	private final AccessService access = mock(AccessService.class);
	private final AuditPublisher audit = mock(AuditPublisher.class);
	private final ActorProvider actors = mock(ActorProvider.class);

	private final UUID unit = UUID.randomUUID();
	private final UUID roleA = UUID.randomUUID();
	private final UUID roleB = UUID.randomUUID();
	private PositionService service;

	@BeforeEach
	void setUp() {
		service = new PositionService(positions, appointments, orgUnits, access, audit, actors, Clock.systemUTC());
		when(orgUnits.isActive(unit)).thenReturn(true);
		when(access.isAssignableRole(any())).thenReturn(true);
		when(positions.save(any(Position.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	private static String code(Throwable t) {
		return ((DomainException) t).getCode();
	}

	@Test
	void capacityDefaultsToOneAndHeadPositionMustHaveExactlyOneHolder() {
		Position p = service.create(unit, " Chair ", false, null, Set.of(roleA));
		assertThat(p.getCapacity()).isEqualTo(1);
		assertThat(p.getTitle()).isEqualTo("Chair");

		assertThatThrownBy(() -> service.create(unit, "Head", true, 2, Set.of()))
				.satisfies(e -> assertThat(code(e)).isEqualTo("POSITION_HEAD_SINGLE_HOLDER"));
		assertThatThrownBy(() -> service.create(unit, "X", false, 0, Set.of()))
				.satisfies(e -> assertThat(code(e)).isEqualTo("POSITION_CAPACITY_INVALID"));
	}

	@Test
	void onlyOneActiveHeadPositionPerUnit() {
		when(positions.existsByOrgUnitIdAndHeadTrueAndArchivedAtIsNull(unit)).thenReturn(true);

		assertThatThrownBy(() -> service.create(unit, "Head", true, null, Set.of()))
				.satisfies(e -> assertThat(code(e)).isEqualTo("POSITION_HEAD_EXISTS"));
	}

	@Test
	void adminRoleCanNeverBeBoundToAPosition() {
		assertThatThrownBy(() -> service.create(unit, "Boss", false, null, Set.of(AccessService.ADMIN_ROLE_ID)))
				.isInstanceOf(ForbiddenException.class).hasMessage("ADMIN_ROLE_RESERVED");
		verify(positions, never()).save(any());
	}

	@Test
	void unknownOrArchivedRoleIsRejected() {
		when(access.isAssignableRole(roleA)).thenReturn(false);

		assertThatThrownBy(() -> service.create(unit, "X", false, null, Set.of(roleA)))
				.satisfies(e -> assertThat(code(e)).isEqualTo("POSITION_ROLE_UNAVAILABLE"));
	}

	@Test
	void changingBoundRolesGrantsAndRevokesForCurrentHoldersFromTheirOwnAppointments() {
		Position p = new Position("Chair", unit, false, 1, Set.of(roleA));
		Appointment holder = new Appointment(UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 1, 1));
		ReflectionTestUtils.setField(holder, "id", UUID.randomUUID());
		when(positions.lockById(any())).thenReturn(Optional.of(p));
		when(appointments.findByPositionIdAndEndDateIsNull(any())).thenReturn(List.of(holder));

		service.update(UUID.randomUUID(), "Chair", null, Set.of(roleB));

		RoleGrantSource source = AppointmentService.sourceOf(holder.getId());
		verify(access).revokeRole(holder.getUserId(), roleA, source);
		verify(access).grantRole(holder.getUserId(), roleB, source);
		assertThat(p.getDefaultRoleIds()).containsExactly(roleB);
	}

	@Test
	void capacityCannotDropBelowCurrentHolders() {
		Position p = new Position("Member", unit, false, 3, Set.of());
		when(positions.lockById(any())).thenReturn(Optional.of(p));
		when(appointments.findByPositionIdAndEndDateIsNull(any())).thenReturn(List.of(
				new Appointment(UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 1, 1)),
				new Appointment(UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 1, 1))));

		assertThatThrownBy(() -> service.update(UUID.randomUUID(), "Member", 1, null))
				.satisfies(e -> assertThat(code(e)).isEqualTo("POSITION_CAPACITY_BELOW_HOLDERS"));
	}

	@Test
	void positionWithHoldersCannotBeArchived() {
		Position p = new Position("Chair", unit, false, 1, Set.of());
		when(positions.lockById(any())).thenReturn(Optional.of(p));
		when(appointments.countByPositionIdAndEndDateIsNull(any())).thenReturn(1L);

		assertThatThrownBy(() -> service.archive(UUID.randomUUID()))
				.satisfies(e -> assertThat(code(e)).isEqualTo("POSITION_HAS_ACTIVE_APPOINTMENTS"));
	}

}
