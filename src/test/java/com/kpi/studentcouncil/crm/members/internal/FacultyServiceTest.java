package com.kpi.studentcouncil.crm.members.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.audit.AuditAction;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.members.FacultyEvent;
import com.kpi.studentcouncil.crm.shared.error.ConflictException;
import com.kpi.studentcouncil.crm.shared.error.DomainException;

class FacultyServiceTest {

	FacultyRepository repo = mock(FacultyRepository.class);
	AuditPublisher audit = mock(AuditPublisher.class);
	ActorProvider actors = mock(ActorProvider.class);
	ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
	FacultyService service;

	@BeforeEach
	void setUp() {
		service = new FacultyService(repo, audit, actors, events,
				Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
		when(repo.save(any(Faculty.class))).thenAnswer(i -> i.getArgument(0));
	}

	private static String code(Throwable t) {
		return ((DomainException) t).getCode();
	}

	@Test
	void createNormalizesCodeAndEmitsAuditAndEvent() {
		Faculty created = service.create(" fac-1 ", " Назва ", " Name ");
		assertThat(created.getCode()).isEqualTo("FAC-1");
		assertThat(created.getNameUk()).isEqualTo("Назва");
		verify(audit).publish(any(AuditAction.class), anyString(), any(), any(), any());
		verify(events).publishEvent(any(FacultyEvent.class));
	}

	@Test
	void duplicateCodeIsRejected() {
		when(repo.existsByCodeIgnoreCaseAndArchivedAtIsNullAndIdNot(anyString(), any())).thenReturn(true);
		assertThatThrownBy(() -> service.create("A", "uk", "en")).isInstanceOf(ConflictException.class)
				.extracting(e -> code(e)).isEqualTo("FACULTY_CODE_TAKEN");
	}

	@Test
	void duplicateNamesAreRejected() {
		when(repo.existsByNameUkIgnoreCaseAndArchivedAtIsNullAndIdNot(anyString(), any())).thenReturn(true);
		assertThatThrownBy(() -> service.create("A", "uk", "en")).extracting(e -> code(e))
				.isEqualTo("FACULTY_NAME_TAKEN");
		when(repo.existsByNameUkIgnoreCaseAndArchivedAtIsNullAndIdNot(anyString(), any())).thenReturn(false);
		when(repo.existsByNameEnIgnoreCaseAndArchivedAtIsNullAndIdNot(anyString(), any())).thenReturn(true);
		assertThatThrownBy(() -> service.create("A", "uk", "en")).extracting(e -> code(e))
				.isEqualTo("FACULTY_NAME_TAKEN");
	}

	@Test
	void archivedFacultyCannotBeEditedOrArchivedTwiceAndRestoreChecksUniqueness() {
		Faculty f = new Faculty("A", "uk", "en");
		UUID id = UUID.randomUUID();
		when(repo.findById(id)).thenReturn(Optional.of(f));

		service.archive(id);
		assertThat(f.isArchived()).isTrue();
		assertThatThrownBy(() -> service.update(id, "B", "x", "y")).extracting(e -> code(e))
				.isEqualTo("FACULTY_ARCHIVED");
		assertThatThrownBy(() -> service.archive(id)).extracting(e -> code(e)).isEqualTo("ALREADY_ARCHIVED");

		when(repo.existsByCodeIgnoreCaseAndArchivedAtIsNullAndIdNot(anyString(), any())).thenReturn(true);
		assertThatThrownBy(() -> service.restore(id)).extracting(e -> code(e)).isEqualTo("FACULTY_CODE_TAKEN");
		when(repo.existsByCodeIgnoreCaseAndArchivedAtIsNullAndIdNot(anyString(), any())).thenReturn(false);
		assertThat(service.restore(id).isArchived()).isFalse();
		assertThatThrownBy(() -> service.restore(id)).extracting(e -> code(e)).isEqualTo("NOT_ARCHIVED");
	}

}
