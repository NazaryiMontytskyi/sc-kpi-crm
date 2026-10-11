package com.kpi.studentcouncil.crm.members.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.kpi.studentcouncil.crm.access.AccessService;
import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.files.FileService;
import com.kpi.studentcouncil.crm.identity.AccountService;
import com.kpi.studentcouncil.crm.identity.NewAccount;
import com.kpi.studentcouncil.crm.members.FacultyLookup;
import com.kpi.studentcouncil.crm.members.MemberArchiving;
import com.kpi.studentcouncil.crm.members.internal.MemberDtos.CreateMemberRequest;
import com.kpi.studentcouncil.crm.members.internal.MemberDtos.MemberRequest;
import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;
import com.kpi.studentcouncil.crm.shared.error.ConflictException;
import com.kpi.studentcouncil.crm.shared.error.DomainException;

/** Validation and business rules of member profiles, independent of any database. */
class MemberServiceTest {

	private final MemberProfileRepository profiles = mock(MemberProfileRepository.class);
	private final AccountService accounts = mock(AccountService.class);
	private final AccessService access = mock(AccessService.class);
	private final FacultyLookup faculties = mock(FacultyLookup.class);
	private final FileService files = mock(FileService.class);
	private final AuditPublisher audit = mock(AuditPublisher.class);
	private final ActorProvider actors = mock(ActorProvider.class);
	private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	private final UUID actor = UUID.randomUUID();
	private final UUID faculty = UUID.randomUUID();
	private MemberService service;

	@BeforeEach
	void setUp() {
		service = new MemberService(profiles, accounts, access, faculties, files, audit, actors, events,
				Clock.systemUTC());
		when(actors.currentActorId()).thenReturn(actor);
		when(profiles.save(any(MemberProfile.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	private CreateMemberRequest create(UUID facultyId, String telegram) {
		return new CreateMemberRequest("fake.user", "Testenko", "Test", null, facultyId, "XX-11",
				telegram, LocalDate.of(2025, 9, 1), null, null, "uk", null);
	}

	@Test
	void missingRequiredFieldsAreListedByName() {
		var empty = new CreateMemberRequest(null, null, " ", null, null, "", "not a handle", null, null, null, null,
				null);
		Set<String> fields = new java.util.TreeSet<>();
		validator.validate(empty).forEach(v -> fields.add(v.getPropertyPath().toString()));
		assertThat(fields).containsExactlyInAnyOrder("login", "lastName", "firstName", "facultyId", "studyGroup",
				"telegram", "joinedAt");

		var edit = new MemberRequest("A", "B", null, null, "G", "valid_nick", null, null, null);
		Set<String> editFields = new java.util.TreeSet<>();
		validator.validate(edit).forEach(v -> editFields.add(v.getPropertyPath().toString()));
		assertThat(editFields).containsExactlyInAnyOrder("facultyId", "joinedAt");
	}

	@Test
	void telegramIsNormalisedAndValidated() {
		assertThat(MemberRules.normalizeTelegram(" @valid_nick ")).isEqualTo("valid_nick");
		assertThat(MemberRules.normalizeTelegram("valid_nick")).isEqualTo("valid_nick");
		assertThatThrownBy(() -> MemberRules.normalizeTelegram("abc")).isInstanceOf(BusinessRuleException.class);
		assertThatThrownBy(() -> MemberRules.normalizeTelegram("bad nick!")).isInstanceOf(BusinessRuleException.class);
	}

	@Test
	void unknownOrArchivedFacultyIsRejectedAndNothingIsCreated() {
		when(faculties.isSelectable(faculty)).thenReturn(false);
		assertThatThrownBy(() -> service.create(create(faculty, "valid_nick")))
				.isInstanceOfSatisfying(BusinessRuleException.class,
						e -> assertThat(e.getCode()).isEqualTo("FACULTY_NOT_SELECTABLE"));
		verify(accounts, never()).createAccount(any(), any());
	}

	@Test
	void createUsesAccountAndReturnsTemporaryPasswordOnlyInResult() {
		when(faculties.isSelectable(faculty)).thenReturn(true);
		UUID userId = UUID.randomUUID();
		when(accounts.createAccount("fake.user", "uk")).thenReturn(new NewAccount(userId, "fake.user", "Tmp-pass-1234"));
		MemberService.Created created = service.create(create(faculty, "@valid_nick"));
		assertThat(created.temporaryPassword()).isEqualTo("Tmp-pass-1234");
		assertThat(created.toString()).doesNotContain("Tmp-pass-1234");
		assertThat(created.profile().getUserId()).isEqualTo(userId);
		assertThat(created.profile().getTelegram()).isEqualTo("valid_nick");
		assertThat(created.profile().auditSnapshot().toString()).doesNotContain("Tmp-pass-1234");
	}

	@Test
	void keepingAnAlreadyStoredArchivedFacultyIsAllowedOnEdit() {
		UUID userId = UUID.randomUUID();
		MemberProfile profile = new MemberProfile(userId, new MemberData("A", "B", null, faculty, "G", "valid_nick",
				LocalDate.of(2024, 9, 1), null, null));
		when(profiles.findByUserId(userId)).thenReturn(java.util.Optional.of(profile));
		when(faculties.isSelectable(faculty)).thenReturn(false);
		var request = new MemberRequest("A2", "B", null, faculty, "G", "valid_nick", LocalDate.of(2024, 9, 1), null,
				"bio");
		assertThat(service.update(userId, request).getLastName()).isEqualTo("A2");

		var other = new MemberRequest("A2", "B", null, UUID.randomUUID(), "G", "valid_nick", LocalDate.of(2024, 9, 1),
				null, null);
		assertThatThrownBy(() -> service.update(userId, other)).isInstanceOf(BusinessRuleException.class);
	}

	@Test
	void archivingCanBeVetoedAndYourselfCannotBeArchived() {
		UUID userId = UUID.randomUUID();
		MemberProfile profile = new MemberProfile(userId, new MemberData("A", "B", null, faculty, "G", "valid_nick",
				LocalDate.of(2024, 9, 1), null, null));
		when(profiles.findByUserId(userId)).thenReturn(java.util.Optional.of(profile));
		doThrow(new ConflictException("HANDOVER_REQUIRED")).when(events).publishEvent(any(MemberArchiving.class));
		assertThatThrownBy(() -> service.archive(userId)).isInstanceOf(DomainException.class);
		verify(accounts, never()).archiveAccount(any());
		assertThat(profile.isArchived()).isFalse();

		MemberProfile own = new MemberProfile(actor, new MemberData("A", "B", null, faculty, "G", "valid_nick",
				LocalDate.of(2024, 9, 1), null, null));
		when(profiles.findByUserId(actor)).thenReturn(java.util.Optional.of(own));
		assertThatThrownBy(() -> service.archive(actor)).isInstanceOfSatisfying(BusinessRuleException.class,
				e -> assertThat(e.getCode()).isEqualTo("CANNOT_ARCHIVE_SELF"));
	}

}
