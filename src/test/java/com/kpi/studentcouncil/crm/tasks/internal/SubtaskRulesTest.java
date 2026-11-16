package com.kpi.studentcouncil.crm.tasks.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;
import com.kpi.studentcouncil.crm.shared.error.DomainException;

class SubtaskRulesTest {

	private static final LocalDate MAY_10 = LocalDate.of(2031, 5, 10);
	private static final LocalDate MAY_20 = LocalDate.of(2031, 5, 20);
	private static final LocalDate MAY_31 = LocalDate.of(2031, 5, 31);

	private static SubtaskRules.Subtask sub(String title, LocalDate deadline) {
		return new SubtaskRules.Subtask(UUID.randomUUID(), title, deadline);
	}

	// ---- ST-1: depth ----

	@Test
	void aTopLevelTaskCanHaveSubtasks() {
		assertThatCode(() -> SubtaskRules.assertCanHaveSubtasks(null)).doesNotThrowAnyException();
	}

	@Test
	void aSubtaskCannotHaveSubtasks() {
		assertThatThrownBy(() -> SubtaskRules.assertCanHaveSubtasks(UUID.randomUUID()))
				.isInstanceOf(BusinessRuleException.class)
				.extracting(e -> ((DomainException) e).getCode()).isEqualTo("SUBTASK_DEPTH_EXCEEDED");
	}

	// ---- ST-3: deadline bound ----

	@Test
	void aSubtaskDeadlineUpToTheParentDeadlineIsWithinTheBound() {
		assertThat(SubtaskRules.withinParent(MAY_10, MAY_20)).isTrue();
		assertThat(SubtaskRules.withinParent(MAY_20, MAY_20)).isTrue();
		assertThat(SubtaskRules.withinParent(MAY_31, MAY_20)).isFalse();
	}

	@Test
	void aParentWithoutDeadlineImposesNoLimitAndASubtaskWithoutDeadlineIsAlwaysWithin() {
		assertThat(SubtaskRules.withinParent(MAY_31, null)).isTrue();
		assertThat(SubtaskRules.withinParent(null, MAY_10)).isTrue();
		assertThat(SubtaskRules.withinParent(null, null)).isTrue();
	}

	@Test
	void aLaterSubtaskDeadlineIsRejectedNamingTheParentDeadline() {
		assertThatThrownBy(() -> SubtaskRules.assertWithinParent(MAY_31, MAY_20))
				.isInstanceOfSatisfying(BusinessRuleException.class, e -> {
					assertThat(e.getCode()).isEqualTo("SUBTASK_DEADLINE_AFTER_PARENT");
					assertThat(e.getArgs()).containsExactly("2031-05-20");
				});
		assertThatCode(() -> SubtaskRules.assertWithinParent(MAY_20, MAY_20)).doesNotThrowAnyException();
		assertThatCode(() -> SubtaskRules.assertWithinParent(MAY_31, null)).doesNotThrowAnyException();
	}

	// ---- ST-3: moving the parent deadline ----

	@Test
	void movingTheParentDeadlineBeforeSubtaskDeadlinesListsExactlyThoseSubtasksLatestFirst() {
		SubtaskRules.Subtask early = sub("Book the hall", MAY_10);
		SubtaskRules.Subtask late = sub("Print posters", MAY_31);
		SubtaskRules.Subtask middle = sub("Invite guests", MAY_20);
		SubtaskRules.Subtask none = sub("No deadline", null);

		assertThat(SubtaskRules.conflicting(MAY_31, MAY_10, List.of(early, late, middle, none)))
				.containsExactly(late, middle);
		assertThat(SubtaskRules.conflicting(MAY_31, MAY_20, List.of(early, late, middle, none)))
				.containsExactly(late);
		assertThat(SubtaskRules.conflicting(MAY_31, MAY_31, List.of(early, late, middle))).isEmpty();
	}

	@Test
	void removingTheParentDeadlineNeverConflicts() {
		assertThat(SubtaskRules.conflicting(MAY_10, null, List.of(sub("A", MAY_31)))).isEmpty();
	}

	@Test
	void settingAFirstParentDeadlineReportsEveryLaterSubtask() {
		SubtaskRules.Subtask late = sub("Late", MAY_31);
		assertThat(SubtaskRules.conflicting(null, MAY_20, List.of(late, sub("Early", MAY_10)))).containsExactly(late);
	}

	@Test
	void aPreExistingViolationIsToleratedWhenTheParentDeadlineMovesLaterButNotEarlier() {
		// migrated subtask (INC-072) already later than the parent deadline MAY_10
		SubtaskRules.Subtask migrated = sub("Migrated", MAY_31);
		SubtaskRules.Subtask fresh = sub("Fresh", MAY_10);

		assertThat(SubtaskRules.conflicting(MAY_10, MAY_20, List.of(migrated, fresh))).isEmpty();
		assertThat(SubtaskRules.conflicting(MAY_20, MAY_10, List.of(migrated, fresh))).containsExactly(migrated);
	}

	@Test
	void subtasksWithTheSameDeadlineAreOrderedByTitle() {
		SubtaskRules.Subtask b = sub("B", MAY_31);
		SubtaskRules.Subtask a = sub("A", MAY_31);
		assertThat(SubtaskRules.conflicting(null, MAY_10, List.of(b, a))).containsExactly(a, b);
	}

	// ---- TM-4: restore cascade ----

	@Test
	void onlySubtasksArchivedTogetherWithTheParentAreRestoredWithIt() {
		Instant at = Instant.parse("2031-05-01T10:00:00.123456Z");
		UUID actor = UUID.randomUUID();

		assertThat(SubtaskRules.archivedWithParent(at, actor, at, actor)).isTrue();
		assertThat(SubtaskRules.archivedWithParent(at.minusSeconds(60), actor, at, actor)).isFalse();
		assertThat(SubtaskRules.archivedWithParent(at, UUID.randomUUID(), at, actor)).isFalse();
		assertThat(SubtaskRules.archivedWithParent(null, null, at, actor)).isFalse();
	}

}
