package com.kpi.studentcouncil.crm.tasks.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.kpi.studentcouncil.crm.tasks.StatusCategory;

class SuggestCloseRuleTest {

	private static SuggestCloseRule.Subtask active(StatusCategory category) {
		return new SuggestCloseRule.Subtask(category, false);
	}

	private static SuggestCloseRule.Subtask archived(StatusCategory category) {
		return new SuggestCloseRule.Subtask(category, true);
	}

	private static final SuggestCloseRule.Subtask DONE = active(StatusCategory.DONE);
	private static final SuggestCloseRule.Subtask TODO = active(StatusCategory.TODO);
	private static final SuggestCloseRule.Subtask IN_PROGRESS = active(StatusCategory.IN_PROGRESS);
	private static final SuggestCloseRule.Subtask CANCELED = active(StatusCategory.CANCELED);

	@Test
	void noSubtasksNeverSuggests() {
		assertThat(SuggestCloseRule.suggest(StatusCategory.IN_PROGRESS, List.of())).isFalse();
	}

	@Test
	void allCountedSubtasksDoneSuggests() {
		assertThat(SuggestCloseRule.suggest(StatusCategory.IN_PROGRESS, List.of(DONE))).isTrue();
		assertThat(SuggestCloseRule.suggest(StatusCategory.TODO, List.of(DONE, DONE))).isTrue();
	}

	@Test
	void anyOpenSubtaskBlocksTheSuggestion() {
		assertThat(SuggestCloseRule.suggest(StatusCategory.IN_PROGRESS, List.of(DONE, TODO))).isFalse();
		assertThat(SuggestCloseRule.suggest(StatusCategory.IN_PROGRESS, List.of(IN_PROGRESS, DONE))).isFalse();
	}

	@Test
	void canceledAndArchivedSubtasksAreIgnored() {
		assertThat(SuggestCloseRule.suggest(StatusCategory.TODO, List.of(DONE, CANCELED, archived(StatusCategory.TODO))))
				.isTrue();
	}

	@Test
	void onlyCanceledOrArchivedSubtasksNeverSuggest() {
		assertThat(SuggestCloseRule.suggest(StatusCategory.TODO, List.of(CANCELED))).isFalse();
		assertThat(SuggestCloseRule.suggest(StatusCategory.TODO, List.of(archived(StatusCategory.DONE)))).isFalse();
		assertThat(SuggestCloseRule.suggest(StatusCategory.TODO, List.of(CANCELED, archived(StatusCategory.DONE))))
				.isFalse();
	}

	@ParameterizedTest
	@EnumSource(value = StatusCategory.class, names = { "DONE", "CANCELED" })
	void anAlreadyClosedParentIsNotSuggested(StatusCategory parent) {
		assertThat(SuggestCloseRule.suggest(parent, List.of(DONE))).isFalse();
	}

	@Test
	void anUnknownSubtaskCategoryCountsAsOpen() {
		assertThat(SuggestCloseRule.suggest(StatusCategory.TODO, List.of(DONE, active(null)))).isFalse();
		assertThat(SuggestCloseRule.openCount(List.of(active(null)))).isEqualTo(1);
	}

	@Test
	void openCountIgnoresDoneCanceledAndArchived() {
		List<SuggestCloseRule.Subtask> subtasks = List.of(TODO, IN_PROGRESS, DONE, CANCELED,
				archived(StatusCategory.TODO));
		assertThat(SuggestCloseRule.openCount(subtasks)).isEqualTo(2);
		assertThat(SuggestCloseRule.activeCount(subtasks)).isEqualTo(4);
		assertThat(SuggestCloseRule.progress(StatusCategory.TODO, subtasks))
				.isEqualTo(new SuggestCloseRule.Progress(4, 2, false));
	}

	@Test
	void aDoneParentMayStillHaveOpenSubtasks() {
		// ST-5: closing the parent with open subtasks is allowed; the progress exposes the open count
		assertThat(SuggestCloseRule.progress(StatusCategory.DONE, List.of(TODO, DONE)))
				.isEqualTo(new SuggestCloseRule.Progress(2, 1, false));
	}

}
