package com.kpi.studentcouncil.crm.tasks.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.kpi.studentcouncil.crm.tasks.MemberTaskCounts;
import com.kpi.studentcouncil.crm.tasks.PersonalTaskSummary;
import com.kpi.studentcouncil.crm.tasks.StatusCategory;

/** Rules of the personal task summaries: Kyiv day boundary for "overdue", counters, the upcoming limit. */
class PersonalTaskServiceTest {

	private final TaskRepository tasks = mock(TaskRepository.class);
	private final SpaceStatusRepository statuses = mock(SpaceStatusRepository.class);
	private final UUID user = UUID.randomUUID();

	/** 2026-03-10 22:30 UTC is already 2026-03-11 in Kyiv (UTC+2). */
	private final Clock lateEveningUtc = Clock.fixed(Instant.parse("2026-03-10T22:30:00Z"), ZoneOffset.UTC);

	@Test
	void overdueStartsTheDayAfterTheDeadline() {
		LocalDate today = LocalDate.of(2026, 3, 11);
		assertThat(PersonalTaskService.isOverdue(LocalDate.of(2026, 3, 10), today)).isTrue();
		assertThat(PersonalTaskService.isOverdue(today, today)).isFalse();
		assertThat(PersonalTaskService.isOverdue(LocalDate.of(2026, 3, 12), today)).isFalse();
		assertThat(PersonalTaskService.isOverdue(null, today)).isFalse();
	}

	@Test
	void countsExcludeClosedCategoriesAndUseTheKyivDay() {
		PersonalTaskService service = new PersonalTaskService(tasks, statuses, lateEveningUtc);
		when(tasks.countAssignedNotInCategories(user, TaskSpecs.CLOSED)).thenReturn(7L);
		when(tasks.countAssignedNotInCategoriesDueBefore(user, TaskSpecs.CLOSED, LocalDate.of(2026, 3, 11)))
				.thenReturn(2L);
		when(tasks.countAssignedInCategories(eq(user), anyCollection())).thenReturn(4L);

		MemberTaskCounts.Counts counts = service.countsFor(user);

		assertThat(counts).isEqualTo(new MemberTaskCounts.Counts(7, 2, 4));
		verify(tasks).countAssignedInCategories(user, Set.of(StatusCategory.DONE));
		assertThat(TaskSpecs.CLOSED).containsExactlyInAnyOrder(StatusCategory.DONE, StatusCategory.CANCELED);
	}

	@Test
	@SuppressWarnings("unchecked")
	void summaryWithoutUpcomingLimitReadsNoList() {
		PersonalTaskService service = new PersonalTaskService(tasks, statuses, lateEveningUtc);
		when(tasks.countAssignedNotInCategories(user, TaskSpecs.CLOSED)).thenReturn(3L);
		when(tasks.countAssignedNotInCategoriesDueBefore(user, TaskSpecs.CLOSED, LocalDate.of(2026, 3, 11)))
				.thenReturn(1L);

		PersonalTaskSummary.Summary summary = service.summaryFor(user, 0);

		assertThat(summary.active()).isEqualTo(3);
		assertThat(summary.overdue()).isEqualTo(1);
		assertThat(summary.upcoming()).isEmpty();
		verify(tasks, never()).findActive(any(Specification.class), any(Pageable.class));
	}

}
