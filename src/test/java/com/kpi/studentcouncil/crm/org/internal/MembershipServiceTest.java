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
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import com.kpi.studentcouncil.crm.audit.AuditAction;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.identity.UserDirectory;
import com.kpi.studentcouncil.crm.org.MembershipEvent;
import com.kpi.studentcouncil.crm.org.OrgService;
import com.kpi.studentcouncil.crm.shared.error.DomainException;
import com.kpi.studentcouncil.crm.shared.error.NotFoundException;

/** Business rules of memberships, independent of any database. */
class MembershipServiceTest {

	// 2026-03-10 22:30 UTC is already 2026-03-11 in Kyiv (UTC+2).
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-10T22:30:00Z"), ZoneOffset.UTC);
	private static final LocalDate TODAY_KYIV = LocalDate.of(2026, 3, 11);

	private final MembershipRepository repo = mock(MembershipRepository.class);
	private final OrgService orgUnits = mock(OrgService.class);
	private final UserDirectory users = mock(UserDirectory.class);
	private final AuditPublisher audit = mock(AuditPublisher.class);
	private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

	private final UUID user = UUID.randomUUID();
	private final UUID unitA = UUID.randomUUID();
	private final UUID unitB = UUID.randomUUID();
	private MembershipService service;

	@BeforeEach
	void setUp() {
		service = new MembershipService(repo, orgUnits, users, audit, events, CLOCK);
		when(repo.save(any(Membership.class))).thenAnswer(inv -> inv.getArgument(0));
		when(users.exists(user)).thenReturn(true);
		when(orgUnits.isActive(unitA)).thenReturn(true);
		when(orgUnits.isActive(unitB)).thenReturn(true);
	}

	private static String code(Throwable t) {
		return ((DomainException) t).getCode();
	}

	@Test
	void startDefaultsToTodayInKyivAndEmitsAuditAndEvent() {
		Membership m = service.start(unitA, user, null);

		assertThat(m.getSince()).isEqualTo(TODAY_KYIV);
		assertThat(m.isActive()).isTrue();
		verify(audit).publish(any(AuditAction.class), any(), any(), any(), any());
		ArgumentCaptor<MembershipEvent> event = ArgumentCaptor.forClass(MembershipEvent.class);
		verify(events).publishEvent(event.capture());
		assertThat(event.getValue().kind()).isEqualTo(MembershipEvent.Kind.STARTED);
		assertThat(event.getValue().userId()).isEqualTo(user);
	}

	@Test
	void duplicateActiveMembershipInSameUnitIsRejected() {
		when(repo.existsByUserIdAndOrgUnitIdAndUntilIsNull(user, unitA)).thenReturn(true);

		assertThatThrownBy(() -> service.start(unitA, user, null)).isInstanceOf(DomainException.class)
				.extracting(e -> code(e)).isEqualTo("MEMBERSHIP_DUPLICATE");
		verify(repo, never()).save(any());
	}

	@Test
	void sameUserMayJoinSeveralUnits() {
		service.start(unitA, user, null);
		service.start(unitB, user, null);

		verify(repo, org.mockito.Mockito.times(2)).save(any(Membership.class));
	}

	@Test
	void archivedOrUnknownUnitAndUnknownUserAreRejected() {
		when(orgUnits.isActive(unitA)).thenReturn(false);
		assertThatThrownBy(() -> service.start(unitA, user, null)).extracting(e -> code(e))
				.isEqualTo("MEMBERSHIP_ORG_UNIT_ARCHIVED");

		UUID missing = UUID.randomUUID();
		when(orgUnits.require(missing)).thenThrow(new NotFoundException("OrgUnit", missing));
		assertThatThrownBy(() -> service.start(missing, user, null)).isInstanceOf(NotFoundException.class);

		UUID ghost = UUID.randomUUID();
		assertThatThrownBy(() -> service.start(unitB, ghost, null)).extracting(e -> code(e))
				.isEqualTo("MEMBERSHIP_USER_NOT_FOUND");
	}

	@Test
	void endSetsUntilKeepsRecordAndRejectsSecondEnd() {
		Membership m = new Membership(user, unitA, LocalDate.of(2026, 1, 1));
		when(repo.findById(any())).thenReturn(Optional.of(m));

		service.end(UUID.randomUUID(), null);

		assertThat(m.getUntil()).isEqualTo(TODAY_KYIV);
		assertThat(m.isActive()).isFalse();
		assertThatThrownBy(() -> service.end(UUID.randomUUID(), null)).extracting(e -> code(e))
				.isEqualTo("MEMBERSHIP_ALREADY_ENDED");
	}

	@Test
	void endBeforeStartIsRejected() {
		Membership m = new Membership(user, unitA, LocalDate.of(2026, 2, 1));
		when(repo.findById(any())).thenReturn(Optional.of(m));

		assertThatThrownBy(() -> service.end(UUID.randomUUID(), LocalDate.of(2026, 1, 31)))
				.extracting(e -> code(e)).isEqualTo("MEMBERSHIP_INVALID_PERIOD");
		assertThat(m.isActive()).isTrue();
	}

	@Test
	void defaultEndNeverPrecedesAFutureStart() {
		Membership m = new Membership(user, unitA, LocalDate.of(2026, 6, 1));
		when(repo.findById(any())).thenReturn(Optional.of(m));

		service.end(UUID.randomUUID(), null);

		assertThat(m.getUntil()).isEqualTo(LocalDate.of(2026, 6, 1));
	}

	@Test
	void moveEndsOldAndStartsNewOnTheSameDate() {
		Membership old = new Membership(user, unitA, LocalDate.of(2026, 1, 1));
		when(repo.findById(any())).thenReturn(Optional.of(old));
		LocalDate date = LocalDate.of(2026, 3, 1);

		Membership created = service.move(UUID.randomUUID(), unitB, date);

		assertThat(old.getUntil()).isEqualTo(date);
		assertThat(created.getOrgUnitId()).isEqualTo(unitB);
		assertThat(created.getUserId()).isEqualTo(user);
		assertThat(created.getSince()).isEqualTo(date);
		assertThat(created.isActive()).isTrue();
		ArgumentCaptor<MembershipEvent> events = ArgumentCaptor.forClass(MembershipEvent.class);
		verify(this.events, org.mockito.Mockito.times(2)).publishEvent(events.capture());
		assertThat(events.getAllValues()).extracting(MembershipEvent::kind)
				.containsExactly(MembershipEvent.Kind.ENDED, MembershipEvent.Kind.STARTED);
	}

	@Test
	void moveToSameOrArchivedUnitIsRejected() {
		Membership old = new Membership(user, unitA, LocalDate.of(2026, 1, 1));
		when(repo.findById(any())).thenReturn(Optional.of(old));

		assertThatThrownBy(() -> service.move(UUID.randomUUID(), unitA, null)).extracting(e -> code(e))
				.isEqualTo("MEMBERSHIP_MOVE_SAME_UNIT");
		when(orgUnits.isActive(unitB)).thenReturn(false);
		assertThatThrownBy(() -> service.move(UUID.randomUUID(), unitB, null)).extracting(e -> code(e))
				.isEqualTo("MEMBERSHIP_ORG_UNIT_ARCHIVED");
		assertThat(old.isActive()).isTrue();
	}

	@Test
	void moveToUnitWhereAlreadyMemberIsRejectedAndLeavesOldMembershipUntouched() {
		Membership old = new Membership(user, unitA, LocalDate.of(2026, 1, 1));
		when(repo.findById(any())).thenReturn(Optional.of(old));
		when(repo.existsByUserIdAndOrgUnitIdAndUntilIsNull(user, unitB)).thenReturn(true);

		assertThatThrownBy(() -> service.move(UUID.randomUUID(), unitB, null)).extracting(e -> code(e))
				.isEqualTo("MEMBERSHIP_DUPLICATE");
		assertThat(old.isActive()).isTrue();
	}

	@Test
	void endAllOfUserEndsEveryActiveMembership() {
		Membership a = new Membership(user, unitA, LocalDate.of(2026, 1, 1));
		Membership b = new Membership(user, unitB, LocalDate.of(2026, 2, 1));
		when(repo.findByUserIdAndUntilIsNull(user)).thenReturn(List.of(a, b));

		int ended = service.endAllOfUser(user);

		assertThat(ended).isEqualTo(2);
		assertThat(a.isActive()).isFalse();
		assertThat(b.isActive()).isFalse();
		verify(audit, org.mockito.Mockito.times(2)).publish(any(AuditAction.class), any(), any(), any(), any());
	}

}
