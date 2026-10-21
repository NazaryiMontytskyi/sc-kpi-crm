package com.kpi.studentcouncil.crm.search.internal;

import java.util.Set;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.kpi.studentcouncil.crm.search.internal.SearchDtos.ReindexResponse;
import com.kpi.studentcouncil.crm.search.internal.SearchDtos.SearchResponse;
import com.kpi.studentcouncil.crm.shared.web.ApiV1;

/** Global search across tasks, resolutions, articles, people and org units. */
@ApiV1
@RequestMapping("/search")
@Tag(name = "Search")
class SearchController {

	private final SearchService service;
	private final SearchIndexer indexer;

	SearchController(SearchService service, SearchIndexer indexer) {
		this.service = service;
		this.indexer = indexer;
	}

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "Search everything the caller may see, grouped by type",
			description = "Matches whole words, word beginnings, parts of words and small typos, in Ukrainian and "
					+ "English. Queries shorter than 2 characters return empty groups. Each group is limited to "
					+ "`limit` hits (default 5, max 20) and reports the total number of matches. A group is empty "
					+ "unless the caller holds the view permission of its type (tasks.view for tasks and "
					+ "resolutions, kb.view for articles, members.view for people, org.view for org units); "
					+ "restricted articles need kb.restricted.view. Archived items are never returned. `types` "
					+ "restricts the search to some groups.")
	SearchResponse search(@RequestParam(name = "q", defaultValue = "") String q,
			@RequestParam(name = "types", required = false) Set<SearchType> types,
			@RequestParam(name = "limit", required = false) Integer limit) {
		return service.search(q, types, limit);
	}

	@PostMapping("/reindex")
	@PreAuthorize("hasAuthority('settings.manage')")
	@Operation(summary = "Rebuild the whole search index",
			description = "Reads every active task, resolution, article, member and org unit through the public "
					+ "APIs of the owning modules and replaces the index in one transaction. Audited. Needs "
					+ "settings.manage.")
	ReindexResponse reindex() {
		var counts = indexer.reindexAll();
		return new ReindexResponse(counts.get("tasks"), counts.get("resolutions"), counts.get("articles"),
				counts.get("people"), counts.get("orgUnits"));
	}

}
