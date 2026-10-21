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

	record SearchResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String query,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) SearchGroup tasks,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) SearchGroup resolutions,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) SearchGroup articles,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) SearchGroup people,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) SearchGroup orgUnits) {
	}

	/** How many documents of each type the reindex wrote. */
	record ReindexResponse(int tasks, int resolutions, int articles, int people, int orgUnits) {
	}

}
