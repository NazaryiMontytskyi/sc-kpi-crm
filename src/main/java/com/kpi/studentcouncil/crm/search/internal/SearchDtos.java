package com.kpi.studentcouncil.crm.search.internal;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** Response bodies of the search API. */
final class SearchDtos {

	private SearchDtos() {
	}

	/**
	 * One search hit. {@code parentId} is the task of a resolution, the space of a task and the parent unit of an
	 * org unit, otherwise null.
	 */
	record SearchHit(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String snippet, UUID parentId,
			boolean restricted) {
	}

	/** Hits of one type: the best {@code items} and the number of all matches {@code total}. */
	record SearchGroup(long total, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<SearchHit> items) {
	}

	/**
	 * Hits grouped by type. A task hit matches its title, description, labels, assignee full names and subtask
	 * titles. {@code resolutions} is deprecated and always empty since INC-080 (resolutions became subtasks).
	 */
	record SearchResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String query,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) SearchGroup tasks,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, deprecated = true) SearchGroup resolutions,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) SearchGroup articles,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) SearchGroup people,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) SearchGroup orgUnits) {
	}

	/** How many documents of each type the reindex wrote; {@code resolutions} is deprecated and always 0. */
	record ReindexResponse(int tasks, @Schema(deprecated = true) int resolutions, int articles, int people,
			int orgUnits) {
	}

}
