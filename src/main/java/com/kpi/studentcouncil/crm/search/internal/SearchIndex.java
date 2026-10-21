package com.kpi.studentcouncil.crm.search.internal;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Reads and writes the {@code search_document} table. The only class of the module that knows the SQL. */
@Component
class SearchIndex {

	static final int MAX_TITLE = 500;
	static final int MAX_BODY = 200_000;

	/** A hit of a query; {@code total} is the number of all matches of the type, not only of this page. */
	record Hit(UUID id, String title, String body, UUID parentId, boolean restricted, long total) {
	}

	private final JdbcClient jdbc;
	private final Clock clock;

	SearchIndex(JdbcTemplate template, Clock clock) {
		this.jdbc = JdbcClient.create(template);
		this.clock = clock;
	}

	/** Inserts or replaces the document of an entity. */
	void upsert(SearchType type, UUID id, String title, String body, boolean restricted, UUID parentId) {
		String cleanTitle = cut(title == null ? "" : title.strip(), MAX_TITLE);
		String cleanBody = cut(body == null ? "" : body.strip(), MAX_BODY);
		String titleLc = cleanTitle.toLowerCase(Locale.ROOT);
		String searchText = (cleanTitle + " " + cleanBody).toLowerCase(Locale.ROOT);
		jdbc.sql("""
				insert into search_document (entity_type, entity_id, title, body, title_lc, search_text, restricted,
				    parent_id, indexed_at)
				values (:type, :id, :title, :body, :titleLc, :searchText, :restricted, :parentId, :now)
				on conflict (entity_type, entity_id) do update set title = excluded.title, body = excluded.body,
				    title_lc = excluded.title_lc, search_text = excluded.search_text, restricted = excluded.restricted,
				    parent_id = excluded.parent_id, indexed_at = excluded.indexed_at
				""").param("type", type.name()).param("id", id).param("title", cleanTitle).param("body", cleanBody)
				.param("titleLc", titleLc).param("searchText", searchText).param("restricted", restricted)
				.param("parentId", parentId).param("now", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
				.update();
	}

	void delete(SearchType type, UUID id) {
		jdbc.sql("delete from search_document where entity_type = :type and entity_id = :id")
				.param("type", type.name()).param("id", id).update();
	}

	/** Removes the documents of the type whose parent is {@code parentId} (e.g. the resolutions of a task). */
	void deleteByParent(SearchType type, UUID parentId) {
		jdbc.sql("delete from search_document where entity_type = :type and parent_id = :parentId")
				.param("type", type.name()).param("parentId", parentId).update();
	}

	void deleteAll() {
		jdbc.sql("delete from search_document").update();
	}

	/** Best matches of one type first. {@code canViewRestricted} false hides restricted documents. */
	List<Hit> search(SearchType type, SearchQuery query, boolean canViewRestricted, int limit) {
		return jdbc.sql("""
				select entity_id, title, body, parent_id, restricted, count(*) over () as total
				from search_document
				where entity_type = :type and (:canRestricted or not restricted)
				  and (tsv @@ (to_tsquery('simple', :tsq) || to_tsquery('english', :tsq))
				       or search_text like :pattern escape '\\'
				       or :text <% title_lc)
				order by (ts_rank_cd(tsv, to_tsquery('simple', :tsq) || to_tsquery('english', :tsq))
				          + word_similarity(:text, title_lc) * 2
				          + case when title_lc like :pattern escape '\\' then 1 else 0 end) desc,
				         title_lc, entity_id
				limit :limit
				""").param("type", type.name()).param("canRestricted", canViewRestricted)
				.param("tsq", query.prefixTsQuery()).param("pattern", query.likePattern()).param("text", query.text())
				.param("limit", limit)
				.query((rs, n) -> new Hit(rs.getObject("entity_id", UUID.class), rs.getString("title"),
						rs.getString("body"), rs.getObject("parent_id", UUID.class), rs.getBoolean("restricted"),
						rs.getLong("total")))
				.list();
	}

	private static String cut(String s, int max) {
		return s.length() <= max ? s : s.substring(0, max);
	}

}
