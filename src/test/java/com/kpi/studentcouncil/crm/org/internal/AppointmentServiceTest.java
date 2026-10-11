package com.kpi.studentcouncil.crm.org.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.kpi.studentcouncil.crm.access.AccessService;
import com.kpi.studentcouncil.crm.access.RoleGrantSource;
import com.kpi.studentcouncil.crm.audit.AuditAction;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.identity.UserDirectory;
import com.kpi.studentcouncil.crm.org.AppointmentEvent;
import com.kpi.studentcouncil.crm.org.OrgService;
import com.kpi.studentcouncil.crm.shared.error.DomainException;

/** Business rules of appointments and role grant/revoke, independent of any database. */
class AppointmentServiceTest {

	// 2026-03-10 22:30 UTC is already 2026-03-11 in Kyiv (UTC+2).
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-10T22:30:00Z"), ZoneOffset.UTC);
	private static final LocalDate TODAY_KYIV = LocalDate.of(2026, 3, 11);

	private final AppointmentRepository appointments = mock(AppointmentRepository.class);
	private final PositionRepository positions = mock(PositionRepository.class);
	private final OrgService orgUnits = mock(OrgService.class);
	private final UserDirectory users = mock(UserDirectory.class);
	private final AccessService access = mock(AccessService.class);
	private final AuditPublisher audit = mock(AuditPublisher.class);
	private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

	private final UUID unit = UUID.randomUUID();
	private final UUID user = UUID.randomUUID();
	private final UUID roleA = UUID.randomUUID();
	private final UUID roleB = UUID.randomUUID();
	private Position position;
	private AppointmentService service;

	@BeforeEach
	void setUp() {
		service = new AppointmentService(appointments, positions, orgUnits, users, access, audit, events, CLOCK);
		position = new Position("Secretary", unit, false, 1, Set.of(roleA, roleB));
		when(positions.lockById(any())).thenReturn(Optional.of(position));
		when(positions.findById(any())).thenReturn(Optional.of(position));
		when(orgUnits.isActive(unit)).thenReturn(true);
		when(users.exists(user)).thenReturn(true);
		when(access.isAssignableRole(any())).thenReturn(true);
		when(appointments.save(any(Appointment.class))).thenAnswer(inv -> {
			Appointment saved = inv.getArgument(0);
			if (saved.getId() == null) {
				ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
			}
			return saved;
		});
	}

	private Appointment appt(LocalDate start) {
		Appointment a = new Appointment(UUID.randomUUID(), user, start);
		ReflectionTestUtils.setField(a, "id", UUID.randomUUID());
		return a;
	}

	private static String code(Throwable t) {
		return ((DomainException) t).getCode();
	}

	@Test
	void appointGrantsEveryBoundRoleFromTheAppointmentSourceAndAuditsAppoint() {
		Appointment a = service.appoint(UUID.randomUUID(), user, null);

		assertThat(a.getStartDate()).isEqualTo(TODAY_KYIV);
		assertThat(a.isActive()).isTrue();
		ArgumentCaptor<RoleGrantSource> source = ArgumentCaptor.forClass(RoleGrantSource.class);
		verify(access, org.mockito.Mockito.times(2)).grantRole(any(), any(), source.capture());
		assertThat(source.getValue().type()).isEqualTo("APPOINTMENT");
		assertThat(source.getValue().id()).isEqualTo(a.getId());
		verify(access).grantRole(user, roleA, source.getValue());
		verify(access).grantRole(user, roleB, source.getValue());
		verify(audit).publish(any(AuditAction.class), any(), any(), any(), any());
		ArgumentCaptor<AppointmentEvent> event = ArgumentCaptor.forClass(AppointmentEvent.class);
		verify(events).publishEvent(event.capture());
		assertThat(event.getValue().kind()).isEqualTo(AppointmentEvent.Kind.STARTED);
		assertThat(event.getValue().userId()).isEqualTo(user);
	}

	@Test
	void appointAuditActionIsAppoint() {
		service.appoint(UUID.randomUUID(), user, null);
		ArgumentCaptor<AuditAction> action = ArgumentCaptor.forClass(AuditAction.class);
		verify(audit).publish(action.capture(), any(), any(), any(), any());
		assertThat(action.getValue()).isEqualTo(AuditAction.APPOINT);
	}

	@Test
	void fullPositionRejectsAnotherHolder() {
		when(appointments.countByPositionIdAndEndDateIsNull(any())).thenReturn(1L);

		assertThatThrownBy(() -> service.appoint(UUID.randomUUID(), user, null)).isInstanceOf(DomainException.class)
				.satisfies(e -> assertThat(code(e)).isEqualTo("POSITION_FULL"));
		verify(access, never()).grantRole(any(), any(), any());
	}

	@Test
	void positionWithCapacityTwoAcceptsASecondHolderButNotTheSamePersonTwice() {
		position = new Position("Member", unit, false, 2, Set.of());
		when(positions.lockById(any())).thenReturn(Optional.of(position));
		when(appointments.countByPositionIdAndEndDateIsNull(any())).thenReturn(1L);

		assertThat(service.appoint(UUID.randomUUID(), user, null)).isNotNull();

		when(appointments.existsByPositionIdAndUserIdAndEndDateIsNull(any(), any())).thenReturn(true);
		assertThatThrownBy(() -> service.appoint(UUID.randomUUID(), user, null))
				.satisfies(e -> assertThat(code(e)).isEqualTo("APPOINTMENT_DUPLICATE"));
	}

	@Test
	void archivedPositionArchivedUnitUnknownUserAndFutureDateAreRejected() {
		when(orgUnits.isActive(unit)).thenReturn(false);
		assertThatThrownBy(() -> service.appoint(UUID.randomUUID(), user, null))
				.satisfies(e -> assertThat(code(e)).isEqualTo("ORG_UNIT_ARCHIVED"));
		when(orgUnits.isActive(unit)).thenReturn(true);

		assertThatThrownBy(() -> service.appoint(UUID.randomUUID(), UUID.randomUUID(), null))
				.satisfies(e -> assertThat(code(e)).isEqualTo("APPOINTMENT_USER_NOT_FOUND"));
		assertThatThrownBy(() -> service.appoint(UUID.randomUUID(), user, TODAY_KYIV.plusDays(1)))
				.satisfies(e -> assertThat(code(e)).isEqualTo("APPOINTMENT_DATE_IN_FUTURE"));

		position.archive(UUID.randomUUID(), Instant.now());
		assertThatThrownBy(() -> service.appoint(UUID.randomUUID(), user, null))
				.satisfies(e -> assertThat(code(e)).isEqualTo("POSITION_ARCHIVED"));
	}

	@Test
	void unavailableBoundRoleBlocksTheAppointmentBeforeAnythingIsSaved() {
		when(access.isAssignableRole(roleB)).thenReturn(false);

		assertThatThrownBy(() -> service.appoint(UUID.randomUUID(), user, null))
				.satisfies(e -> assertThat(code(e)).isEqualTo("POSITION_ROLE_UNAVAILABLE"));
		verify(appointments, never()).save(any());
	}

	@Test
	void endingRevokesOnlyGrantsOfThisAppointmentAndKeepsTheRecord() {
		Appointment a = appt(LocalDate.of(2026, 1, 5));
		when(appointments.findById(any())).thenReturn(Optional.of(a));
		when(access.revokeAllGrantedBy(any())).thenReturn(2);

		Appointment ended = service.end(UUID.randomUUID(), null);

		assertThat(ended.getEndDate()).isEqualTo(TODAY_KYIV);
		assertThat(ended.isActive()).isFalse();
		ArgumentCaptor<RoleGrantSource> source = ArgumentCaptor.forClass(RoleGrantSource.class);
		verify(access).revokeAllGrantedBy(source.capture());
		assertThat(source.getValue()).isEqualTo(new RoleGrantSource("APPOINTMENT", a.getId()));
		ArgumentCaptor<AuditAction> action = ArgumentCaptor.forClass(AuditAction.class);
		verify(audit).publish(action.capture(), any(), any(), any(), any());
		assertThat(action.getValue()).isEqualTo(AuditAction.DISMISS);
	}

	@Test
	void endingTwiceBeforeStartOrInTheFutureIsRejected() {
		Appointment a = appt(LocalDate.of(2026, 1, 5));
		when(appointments.findById(any())).thenReturn(Optional.of(a));

		assertThatThrownBy(() -> service.end(UUID.randomUUID(), LocalDate.of(2026, 1, 4)))
				.satisfies(e -> assertThat(code(e)).isEqualTo("APPOINTMENT_INVALID_PERIOD"));
		assertThatThrownBy(() -> service.end(UUID.randomUUID(), TODAY_KYIV.plusDays(1)))
				.satisfies(e -> assertThat(code(e)).isEqualTo("APPOINTMENT_DATE_IN_FUTURE"));
		service.end(UUID.randomUUID(), LocalDate.of(2026, 2, 1));
		assertThatThrownBy(() -> service.end(UUID.randomUUID(), null))
				.satisfies(e -> assertThat(code(e)).isEqualTo("APPOINTMENT_ALREADY_ENDED"));
	}

	@Test
	void endAllOfUserEndsEveryActiveAppointmentAndRevokesTheirRoles() {
		Appointment a1 = appt(LocalDate.of(2026, 1, 5));
		Appointment a2 = appt(LocalDate.of(2026, 2, 5));
		when(appointments.findByUserIdAndEndDateIsNull(user)).thenReturn(List.of(a1, a2));

		assertThat(service.endAllOfUser(user)).isEqualTo(2);

		assertThat(a1.isActive()).isFalse();
		assertThat(a2.isActive()).isFalse();
		verify(access, org.mockito.Mockito.times(2)).revokeAllGrantedBy(any());
	}

}
