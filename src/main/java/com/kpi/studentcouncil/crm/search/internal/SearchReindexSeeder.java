package com.kpi.studentcouncil.crm.search.internal;

import org.springframework.stereotype.Component;

import com.kpi.studentcouncil.crm.shared.seed.SeedScope;
import com.kpi.studentcouncil.crm.shared.seed.Seeder;

/**
 * One-off full reindex after the switch of search to tasks (INC-080, RR-5): task documents gain assignee names and
 * subtask titles, and the subtasks created by the resolution migration (V28, plain SQL, so no domain events) enter
 * the index. Runs once per environment through the seed history, after every other seeder; audited like a manual
 * reindex (system actor). With seeding disabled, an administrator runs {@code POST /search/reindex} instead.
 */
@Component
class SearchReindexSeeder implements Seeder {

	static final String ID = "search.reindex-tasks-as-subtasks";

	private final SearchIndexer indexer;

	SearchReindexSeeder(SearchIndexer indexer) {
		this.indexer = indexer;
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public SeedScope scope() {
		return SeedScope.REFERENCE;
	}

	/** Last: the index reads data seeded by the other modules. */
	@Override
	public int order() {
		return Integer.MAX_VALUE;
	}

	@Override
	public void run() {
		indexer.reindexAll();
	}

}
