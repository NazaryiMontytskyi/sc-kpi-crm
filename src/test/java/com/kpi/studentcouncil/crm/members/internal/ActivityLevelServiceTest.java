package com.kpi.studentcouncil.crm.members.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.audit.AuditAction;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;
import com.kpi.studentcouncil.crm.shared.error.ConflictException;
import com.kpi.studentcouncil.crm.shared.error.NotFoundException;

/** Activity level rules, independent of any database. */
class ActivityLevelServiceTest {

	private final ActivityLevelDefinitionRepository definitions = mock(ActivityLevelDefinitionRepository.class);
	private final ActivityLevelChangeRepository changes = mock(ActivityLevelChangeRepository.class);
	private final MemberProfileRepository profiles = mock(MemberProfileRepository.class);
	private final AuditPublisher audit = mock(AuditPublisher.class);
	private final ActorProvider actors = mock(ActorProvider.class);

	private final UUID actor = UUID.randomUUID();
	private final UUID memberId = UUID.randomUUID();
	private ActivityLevelService service;
	private MemberProfile profile;
	private ActivityLevelDefinition high;
	private ActivityLevelDefinition low;

	@BeforeEach
	void setUp() {
		service = new ActivityLevelService(definitions, changes, profiles, audit, actors, Clock.systemUTC());
		when(actors.currentActorId()).thenReturn(actor);
		profile = new MemberProfile(memberId, new MemberData("Testenko", "Test", null, UUID.randomUUID(), "XX-11",
				"fake_nick", LocalDate.of(2025, 9, 1), null, null));
		when(profiles.findByUserId(memberId)).thenReturn(Optional.of(profile));
		high = new ActivityLevelDefinition("HIGH", "Високий", "High", 10);
		low = new ActivityLevelDefinition("LOW", "Низький", "Low", 30);
		when(definitions.findByIdAndArchivedAtIsNull(any())).thenReturn(Optional.empty());
		when(changes.save(any(ActivityLevelChange.class))).thenAnswer(inv -> inv.getArgument(0));
		when(profiles.save(any(MemberProfile.class))).thenAnswer(inv -> inv.getArgument(0));
		when(definitions.save(any(ActivityLevelDefinition.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	private UUID selectable(ActivityLevelDefinition level) {
		UUID id = UUID.randomUUID();
		org.springframework.test.util.ReflectionTestUtils.setField(level, "id", id);
		when(definitions.findByIdAndArchivedAtIsNull(id)).thenReturn(Optional.of(level));
		return id;
	}

	@Test
	void firstAssignmentHasNoPreviousLevelAndIsAudited() {
		UUID id = selectable(high);
		ActivityLevelChange change = service.setLevel(memberId, id, "  joined the team ");
		assertThat(change.getFromLevelId()).isNull();
		assertThat(change.getComment()).isEqualTo("joined the team");
		assertThat(change.getChangedBy()).isEqualTo(actor);
		assertThat(change.getChangedAt()).isNotNull();
		verify(profiles).save(profile);
		verify(audit).publish(eq(AuditAction.CREATE), eq(ActivityLevelService.CHANGE_TYPE), any(), eq(null), any());
	}

	@Test
	void sameLevelIsRejectedAndNothingIsWritten() {
		UUID id = selectable(high);
		profile.setActivityLevelId(id);
		assertThatThrownBy(() -> service.setLevel(memberId, id, null)).isInstanceOfSatisfying(
				BusinessRuleException.class, e -> assertThat(e.getCode()).isEqualTo("ACTIVITY_LEVEL_UNCHANGED"));
		verify(changes, never()).save(any());
	}

	@Test
	void changeRecordsPreviousLevel() {
		UUID lowId = selectable(low);
		UUID previous = UUID.randomUUID();
		profile.setActivityLevelId(previous);
		ActivityLevelChange change = service.setLevel(memberId, lowId, null);
		assertThat(change.getFromLevelId()).isEqualTo(previous);
		assertThat(change.getToLevelId()).isEqualTo(lowId);
		assertThat(profile.getActivityLevelId()).isEqualTo(lowId);
	}

	@Test
	void archivedOrUnknownLevelIsNotSelectable() {
		assertThatThrownBy(() -> service.setLevel(memberId, UUID.randomUUID(), null))
				.isInstanceOfSatisfying(BusinessRuleException.class,
						e -> assertThat(e.getCode()).isEqualTo("ACTIVITY_LEVEL_NOT_SELECTABLE"));
		verify(changes, never()).save(any());
	}

	@Test
	void archivedMemberCannotChange() {
		profile.archive(actor, java.time.Instant.now());
		UUID id = selectable(low);
		assertThatThrownBy(() -> service.setLevel(memberId, id, null)).isInstanceOf(ConflictException.class);
		verify(changes, never()).save(any());
	}

	@Test
	void unknownMemberIsNotFound() {
		UUID other = UUID.randomUUID();
		when(profiles.findByUserId(other)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.setLevel(other, UUID.randomUUID(), null))
				.isInstanceOf(NotFoundException.class);
	}

	@Test
	void historyRepositoryIsAppendOnly() {
		var methods = java.util.Arrays.stream(ActivityLevelChangeRepository.class.getMethods())
				.map(java.lang.reflect.Method::getName).toList();
		assertThat(methods).noneMatch(n -> n.startsWith("delete") || n.startsWith("remove"));
	}

	@Test
	void definitionCodeIsNormalisedAndUnique() {
		ActivityLevelDefinition created = service.create(" gold ", " Золотий ", " Gold ", 5);
		assertThat(created.getCode()).isEqualTo("GOLD");
		assertThat(created.getLabelEn()).isEqualTo("Gold");
		ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
		verify(definitions).existsByCodeIgnoreCaseAndArchivedAtIsNullAndIdNot(code.capture(), any());
		assertThat(code.getValue()).isEqualTo("GOLD");

		when(definitions.existsByCodeIgnoreCaseAndArchivedAtIsNullAndIdNot(eq("DUP"), any())).thenReturn(true);
		assertThatThrownBy(() -> service.create("dup", "Дуб", "Dup", 1)).isInstanceOf(ConflictException.class);
	}

}
