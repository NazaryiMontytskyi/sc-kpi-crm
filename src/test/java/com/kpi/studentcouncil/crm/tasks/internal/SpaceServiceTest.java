package com.kpi.studentcouncil.crm.tasks.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.org.OrgService;
import com.kpi.studentcouncil.crm.shared.error.DomainException;
import com.kpi.studentcouncil.crm.tasks.SpaceStatusUsageGuard;
import com.kpi.studentcouncil.crm.tasks.StatusCategory;

class SpaceServiceTest {

	private final SpaceRepository spaces = mock(SpaceRepository.class);
	private final SpaceStatusRepository statuses = mock(SpaceStatusRepository.class);
	private final CustomFieldDefinitionRepository fields = mock(CustomFieldDefinitionRepository.class);
	private final OrgService orgUnits = mock(OrgService.class);
	@SuppressWarnings("unchecked")
	private final ObjectProvider<SpaceStatusUsageGuard> guards = mock(ObjectProvider.class);
	private final SpaceStatusUsageGuard guard = mock(SpaceStatusUsageGuard.class);
	private final MessageSource messages = mock(MessageSource.class);

	private final UUID spaceId = UUID.randomUUID();
	private final Space space = new Space("Space", null, true);
	private final List<SpaceStatus> active = new ArrayList<>();
	private SpaceStatus todo;
	private SpaceStatus progress;
	private SpaceStatus done;
	private SpaceService service;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(space, "id", spaceId);
		todo = status("To do", StatusCategory.TODO, 0);
		progress = status("In progress", StatusCategory.IN_PROGRESS, 1);
		done = status("Done", StatusCategory.DONE, 2);
		when(spaces.lockById(spaceId)).thenReturn(Optional.of(space));
		when(statuses.findBySpaceIdAndArchivedAtIsNullOrderByPositionAscIdAsc(spaceId)).thenAnswer(inv -> List.copyOf(active));
		when(statuses.save(any(SpaceStatus.class))).thenAnswer(inv -> inv.getArgument(0));
		when(guards.orderedStream()).thenAnswer(inv -> Stream.of(guard));
		service = new SpaceService(spaces, statuses, fields, orgUnits, guards, messages, mock(AuditPublisher.class),
				mock(ActorProvider.class), mock(ApplicationEventPublisher.class), Clock.systemUTC());
	}

	private SpaceStatus status(String name, StatusCategory category, int position) {
		SpaceStatus s = new SpaceStatus(spaceId, name, category, position);
		ReflectionTestUtils.setField(s, "id", UUID.randomUUID());
		active.add(s);
		return s;
	}

	private static String code(Throwable t) {
		return ((DomainException) t).getCode();
	}

	@Test
	void lastDoneOrTodoStatusCannotBeArchivedOrRecategorised() {
		assertThatThrownBy(() -> service.archiveStatus(spaceId, done.getId(), null))
				.satisfies(e -> assertThat(code(e)).isEqualTo("SPACE_STATUS_CATEGORY_REQUIRED"));
		assertThatThrownBy(() -> service.updateStatus(spaceId, todo.getId(), "To do", StatusCategory.CANCELED))
				.satisfies(e -> assertThat(code(e)).isEqualTo("SPACE_STATUS_CATEGORY_REQUIRED"));
		assertThat(done.isArchived()).isFalse();
		assertThat(todo.getCategory()).isEqualTo(StatusCategory.TODO);
	}

	@Test
	void secondDoneStatusMakesTheFirstArchivable() {
		status("Shipped", StatusCategory.DONE, 3);
		SpaceStatus archived = service.archiveStatus(spaceId, done.getId(), null);
		assertThat(archived.isArchived()).isTrue();
	}

	@Test
	void statusInUseNeedsReplacementAndTasksAreMoved() {
		when(guard.countUsages(spaceId, progress.getId())).thenReturn(3L);

		assertThatThrownBy(() -> service.archiveStatus(spaceId, progress.getId(), null))
				.satisfies(e -> assertThat(code(e)).isEqualTo("SPACE_STATUS_IN_USE"));
		assertThatThrownBy(() -> service.archiveStatus(spaceId, progress.getId(), progress.getId()))
				.satisfies(e -> assertThat(code(e)).isEqualTo("SPACE_STATUS_REPLACEMENT_INVALID"));
		assertThatThrownBy(() -> service.archiveStatus(spaceId, progress.getId(), UUID.randomUUID()))
				.satisfies(e -> assertThat(code(e)).isEqualTo("SPACE_STATUS_REPLACEMENT_INVALID"));
		verify(guard, never()).reassign(any(), any(), any());
		assertThat(progress.isArchived()).isFalse();

		SpaceStatus archived = service.archiveStatus(spaceId, progress.getId(), todo.getId());
		assertThat(archived.isArchived()).isTrue();
		verify(guard).reassign(spaceId, progress.getId(), todo.getId());
	}

	@Test
	void unusedStatusIsArchivedWithoutReplacement() {
		when(guard.countUsages(spaceId, progress.getId())).thenReturn(0L);
		assertThat(service.archiveStatus(spaceId, progress.getId(), null).isArchived()).isTrue();
		verify(guard, never()).reassign(any(), any(), any());
	}

	@Test
	void reorderPersistsPositionsAndRejectsIncompleteLists() {
		List<UUID> order = List.of(done.getId(), todo.getId(), progress.getId());
		service.reorderStatuses(spaceId, order);
		assertThat(done.getPosition()).isZero();
		assertThat(todo.getPosition()).isEqualTo(1);
		assertThat(progress.getPosition()).isEqualTo(2);

		assertThatThrownBy(() -> service.reorderStatuses(spaceId, List.of(done.getId(), todo.getId())))
				.satisfies(e -> assertThat(code(e)).isEqualTo("SPACE_STATUS_ORDER_MISMATCH"));
	}

	@Test
	void statusNamesAreUniqueIgnoringCase() {
		assertThatThrownBy(() -> service.addStatus(spaceId, " DONE ", StatusCategory.IN_PROGRESS))
				.satisfies(e -> assertThat(code(e)).isEqualTo("SPACE_STATUS_NAME_TAKEN"));
		SpaceStatus added = service.addStatus(spaceId, "Review", StatusCategory.IN_PROGRESS);
		assertThat(added.getPosition()).isEqualTo(3);
	}

	@Test
	void archivedSpaceRejectsChanges() {
		space.archive(UUID.randomUUID(), java.time.Instant.now());
		assertThatThrownBy(() -> service.addStatus(spaceId, "Review", StatusCategory.IN_PROGRESS))
				.satisfies(e -> assertThat(code(e)).isEqualTo("SPACE_ARCHIVED"));
	}

}
